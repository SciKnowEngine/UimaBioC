package edu.isi.bmkeg.uimaBioC.uima.ae.core;

import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.DocumentAnnotation;
import org.apache.uima.resource.ResourceInitializationException;
import org.bigmech.fries.FRIES_EntityMention;
import org.bigmech.fries.FRIES_EventMention;
import org.bigmech.fries.FRIES_Frame;
import org.bigmech.fries.FRIES_FrameCollection;
import org.bigmech.fries.FRIES_Passage;
import org.bigmech.fries.FRIES_Sentence;
import org.cleartk.ml.feature.extractor.CleartkExtractor;
import org.cleartk.token.type.Sentence;
import org.cleartk.token.type.Token;
import org.simmetrics.StringMetric;
import org.simmetrics.StringMetricBuilder;
import org.simmetrics.metrics.CosineSimilarity;
import org.simmetrics.metrics.Levenshtein;
import org.simmetrics.simplifiers.CaseSimplifier;
import org.simmetrics.simplifiers.NonDiacriticSimplifier;
import org.simmetrics.tokenizers.QGramTokenizer;
import org.simmetrics.tokenizers.WhitespaceTokenizer;
import org.uimafit.component.JCasAnnotator_ImplBase;
import org.uimafit.descriptor.ConfigurationParameter;
import org.uimafit.factory.ConfigurationParameterFactory;
import org.uimafit.util.JCasUtil;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.typeadapters.RuntimeTypeAdapterFactory;

import bioc.type.UimaBioCAnnotation;
import bioc.type.UimaBioCDocument;
import bioc.type.UimaBioCPassage;
import edu.isi.bmkeg.lapdf.xml.model.LapdftextXMLChunk;
import edu.isi.bmkeg.uimaBioC.UimaBioCUtils;

public class MatchReachAndNxmlText extends
		JCasAnnotator_ImplBase {

	public final static String PARAM_INPUT_DIRECTORY = ConfigurationParameterFactory
			.createConfigurationParameterName(MatchReachAndNxmlText.class,
					"inDirPath");
	@ConfigurationParameter(mandatory = true, description = "The place to put the document files to be classified.")
	String inDirPath;
	File inDir;
	
	private StringMetric cosineSimilarityMetric;
	private StringMetric levenshteinSimilarityMetric;

	private CleartkExtractor<DocumentAnnotation, Token> extractor;

	private static Logger logger = Logger
			.getLogger(MatchReachAndNxmlText.class);

	private List<Sentence> sentences = new ArrayList<Sentence>();
	private Map<LapdftextXMLChunk, Integer> pgLookup = new HashMap<LapdftextXMLChunk, Integer>();

	private Pattern wbDetect = Pattern.compile("(\\w\\W|\\W\\w)");
	private Pattern wsDetect = Pattern.compile("\\s");

	
	public void initialize(UimaContext context)
			throws ResourceInitializationException {

		super.initialize(context);

		this.inDir = new File(this.inDirPath);
		
		cosineSimilarityMetric = new StringMetricBuilder()
				.with(new CosineSimilarity<String>())
				.simplify(new CaseSimplifier.Lower())
				.simplify(new NonDiacriticSimplifier())
				.tokenize(new WhitespaceTokenizer())
				.tokenize(new QGramTokenizer(2)).build();

		levenshteinSimilarityMetric = new StringMetricBuilder()
				.with(new Levenshtein()).simplify(new NonDiacriticSimplifier())
				.build();

	}

	public void process(JCas jCas) throws AnalysisEngineProcessException {

		try {
			
			UimaBioCDocument uiD = JCasUtil.selectSingle(jCas,
					UimaBioCDocument.class);
			UimaBioCPassage docP = UimaBioCUtils
					.readDocumentUimaBioCPassage(jCas);

			logger.info(uiD.getId());

			// Want to locate the 'body' section.
			UimaBioCAnnotation body = null;
			List<UimaBioCAnnotation> annotations = JCasUtil.selectCovered(
					UimaBioCAnnotation.class, docP);
			for (UimaBioCAnnotation uiA : annotations) {
				Map<String, String> infons = UimaBioCUtils.convertInfons(uiA
						.getInfons());
				if (infons.get("value").equals("body")) {
					body = uiA;
				}
			}

			if (body == null) {
				logger.error("Can't find body section of paper.");
				return;
			}

			List<Sentence> sentences = JCasUtil.selectCovered(
					Sentence.class, body);
			
			Map<String,String> infons = UimaBioCUtils.convertInfons(uiD.getInfons());
			String docId = "PMC" + infons.get("pmc");
			
			String[] fileTypes = {"json"};
			Collection<File> files = (Collection<File>) FileUtils.listFiles(
					this.inDir, fileTypes, true);
			File sentenceFrames = null;
			File eventFrames = null;
			for( File f : files) {
				if( f.getName().startsWith(docId+".uaz.sentences") ) {
					sentenceFrames = f;
					break;
				}
			}
			for( File f : files) {
				if( f.getName().startsWith(docId+".uaz.events") ) {
					eventFrames = f;
					break;
				}
			}
			
			if( eventFrames == null || sentenceFrames == null)
				return;
			
			final RuntimeTypeAdapterFactory<FRIES_Frame> typeFactory = RuntimeTypeAdapterFactory
	                .of(FRIES_Frame.class, "frame-type")
	                .registerSubtype(FRIES_EntityMention.class, "entity-mention")
	                .registerSubtype(FRIES_Sentence.class, "sentence")
	                .registerSubtype(FRIES_Passage.class, "passage")
	                .registerSubtype(FRIES_EventMention.class, "event-mention");

			Gson gson = new GsonBuilder()
					.setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_DASHES)
					.registerTypeAdapterFactory(typeFactory)
					.create();
			
			FRIES_FrameCollection fc1 = gson.fromJson(new FileReader(eventFrames), 
					FRIES_FrameCollection.class);	 
			
			Map<String,Set<String>> map = new HashMap<String,Set<String>>();
			for( FRIES_Frame frame : fc1.getFrames() ) {
				
				if( frame instanceof FRIES_EventMention)  {
					FRIES_EventMention em  = (FRIES_EventMention) frame;
					String sn = em.getSentence();
					String ev = em.getFrameId();
					if( !map.containsKey(sn) ) {
						map.put(sn, new HashSet<String>());
					}
					Set<String> evs = map.get(sn);
					evs.add(ev);
					map.put(sn, evs);
				}
			}
			
			FRIES_FrameCollection fc2 = gson.fromJson(new FileReader(sentenceFrames), 
					FRIES_FrameCollection.class);	 
			
			for( FRIES_Frame frame : fc2.getFrames() ) {
				
				if( frame instanceof FRIES_Sentence)  {
					
					FRIES_Sentence fs  = (FRIES_Sentence) frame;
					
					if( !map.containsKey(fs.getFrameId()) )
						continue;
					
					if( fs.getText().length() < 10 )
						continue;
					
					float bestSim = 0.0f;
					Sentence match = null;
					for( Sentence s : sentences ) {
						float sim = cosineSimilarityMetric.compare(s.getCoveredText(),
								fs.getText());
						if( sim > bestSim ) {
							match = s;
							bestSim = sim;
						}
						if( sim > 0.9 ) {
							break;
						}
					}
	
					if(match != null) {				
						UimaBioCPassage p = new UimaBioCPassage(jCas);
						p.setBegin(match.getBegin());
						p.setEnd(match.getEnd());
						Map<String, String> inf = new HashMap<String,String>();
						inf.put("friesId", fs.getFrameId() );
						inf.put("type", "Sentence" );
						inf.put("score", bestSim + "" );
						inf.put("events", map.get(fs.getFrameId()).toString() );
						p.setInfons(UimaBioCUtils.convertInfons(inf, jCas));
						p.addToIndexes(jCas);
					}
				}
			}
			

		} catch (Exception e) {

			throw new AnalysisEngineProcessException(e);

		}

	}

	

}
