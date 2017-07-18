package edu.isi.bmkeg.uimaBioC.rubicon;

import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.FSArray;
import org.apache.uima.jcas.tcas.DocumentAnnotation;
import org.apache.uima.resource.ResourceInitializationException;
import org.bigmech.fries.FRIES_Context;
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
import bioc.type.UimaBioCLocation;
import bioc.type.UimaBioCPassage;
import edu.isi.bmkeg.lapdf.xml.model.LapdftextXMLChunk;
import edu.isi.bmkeg.uimaBioC.UimaBioCUtils;
import edu.isi.bmkeg.uimaBioC.utils.NeedlemanWunch;

public class MatchReachAndNxmlText extends JCasAnnotator_ImplBase {

	public final static String PARAM_INPUT_DIRECTORY = ConfigurationParameterFactory
			.createConfigurationParameterName(MatchReachAndNxmlText.class, "inDirPath");
	@ConfigurationParameter(mandatory = true, description = "Directory for the FRIES Frames.")
	String inDirPath;
	File inDir;

	private StringMetric cosineSimilarityMetric;
	private StringMetric levenshteinSimilarityMetric;
	private StringMetric needlemanWunchMetric;
	private NeedlemanWunch nm;
	Pattern alignmentPattern = Pattern.compile("^(.{0,3}_+)");

	private CleartkExtractor<DocumentAnnotation, Token> extractor;

	private static Logger logger = Logger.getLogger(MatchReachAndNxmlText.class);

	private Map<LapdftextXMLChunk, Integer> pgLookup = new HashMap<LapdftextXMLChunk, Integer>();

	private Pattern wbDetect = Pattern.compile("(\\w\\W|\\W\\w)");
	private Pattern wsDetect = Pattern.compile("\\s");

	public void initialize(UimaContext context) throws ResourceInitializationException {

		super.initialize(context);

		this.inDir = new File(this.inDirPath);

		cosineSimilarityMetric = new StringMetricBuilder().with(new CosineSimilarity<String>())
				.simplify(new CaseSimplifier.Lower()).simplify(new NonDiacriticSimplifier())
				.tokenize(new WhitespaceTokenizer()).tokenize(new QGramTokenizer(2)).build();

		levenshteinSimilarityMetric = new StringMetricBuilder().with(new Levenshtein())
				.simplify(new NonDiacriticSimplifier()).build();

		this.nm = new NeedlemanWunch();
		needlemanWunchMetric = new StringMetricBuilder().with(nm).build();

	}

	public void process(JCas jCas) throws AnalysisEngineProcessException {

		try {

			UimaBioCDocument uiD = JCasUtil.selectSingle(jCas, UimaBioCDocument.class);
			if (uiD.getId().equals("skip"))
				return;

			List<String> misses = new ArrayList<String>();
			List<String> issues = new ArrayList<String>();
			
			UimaBioCPassage docP = UimaBioCUtils.readDocument(jCas);
			logger.info("Matching " + uiD.getId());

			Map<String, String> infons = UimaBioCUtils.convertInfons(uiD.getInfons());
			String pmcDocId = "PMC" + infons.get("pmc");

			String[] fileTypes = { "json" };
			Collection<File> files = (Collection<File>) FileUtils.listFiles(this.inDir, fileTypes, true);
			File sentenceFrames = null;
			File eventFrames = null;
			File entityFrames = null;
			for (File f : files) {
				if (f.getName().startsWith(pmcDocId + ".uaz.sentences") || 
						f.getName().startsWith(uiD.getId() + ".uaz.sentences") ) {
					sentenceFrames = f;
					break;
				}
			}
			for (File f : files) {
				if (f.getName().startsWith(pmcDocId + ".uaz.events") || 
						f.getName().startsWith(uiD.getId() + ".uaz.events") ) {
					eventFrames = f;
					break;
				}
			}
			for (File f : files) {
				if (f.getName().startsWith(pmcDocId + ".uaz.entities") || 
						f.getName().startsWith(uiD.getId() + ".uaz.entities") ) {
					entityFrames = f;
					break;
				}
			}

			if (eventFrames == null || sentenceFrames == null)
				return;

			final RuntimeTypeAdapterFactory<FRIES_Frame> typeFactory = RuntimeTypeAdapterFactory
					.of(FRIES_Frame.class, "frame-type").registerSubtype(FRIES_EntityMention.class, "entity-mention")
					.registerSubtype(FRIES_Sentence.class, "sentence").registerSubtype(FRIES_Passage.class, "passage")
					.registerSubtype(FRIES_EventMention.class, "event-mention").registerSubtype(FRIES_Context.class, "context");

			Gson gson = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_DASHES)
					.registerTypeAdapterFactory(typeFactory).create();

			FRIES_FrameCollection fc = gson.fromJson(new FileReader(sentenceFrames), FRIES_FrameCollection.class);
			Map<String, FRIES_Sentence> sentenceMap = new HashMap<String, FRIES_Sentence>();
			for (FRIES_Frame frame : fc.getFrames()) {
				if (frame instanceof FRIES_Sentence) {
					FRIES_Sentence sn = (FRIES_Sentence) frame;
					sentenceMap.put(sn.getFrameId(), sn);
				}
			}
			
			FRIES_FrameCollection fc3 = gson.fromJson(new FileReader(eventFrames), FRIES_FrameCollection.class);
			List<UimaBioCAnnotation> passages = UimaBioCUtils.readAllReadablePassagesAndFloats(jCas);
			passages.addAll( UimaBioCUtils.readFloats(jCas) );
			
			FRIES_FRAMES: for (FRIES_Frame frame : fc3.getFrames()) {

				if (frame instanceof FRIES_EventMention) {

					FRIES_EventMention fem = (FRIES_EventMention) frame;
					String sid = fem.getSentence();
					FRIES_Sentence fs = null;
					if( sentenceMap.containsKey(sid) )
						fs = sentenceMap.get(sid);
					else
						misses.add(sid);
					
					if (fs.getText().length() < 10)
						continue;

					String ss1 = fs.getText().replaceAll("XREF_BIBR", " ");
					ss1 = ss1.replaceAll("\\s+", "");
					
					//
					// Find the sentence that starts and ends at the closest point 
					// to the current sentence. 
					// 
					// - Note that there are errors in both FRIES 
					//   and our sentence splitting
					//
					int[] delts = {10000, 10000};
					Sentence matchingSentence = null;
					LOOP: for(UimaBioCAnnotation p : passages) {
						for (Sentence ss : JCasUtil.selectCovered(Sentence.class, p) ) {
							
							String ss2 = UimaBioCUtils.friesifySentence(jCas, ss);
							ss2 = ss2.replaceAll("\\s+", "");
	
							if( ss1.equals(ss2) ) {
								
								matchingSentence = ss;
								break LOOP;
								
							} else {
									
								int l = Math.min(ss1.length(), ss2.length());
								float sim1 = levenshteinSimilarityMetric.compare(
										ss1.substring(0,l), ss2.substring(0,l));
								float sim2 = levenshteinSimilarityMetric.compare(
										ss1.substring(ss1.length()-l, ss1.length()),
										ss2.substring(ss2.length()-l, ss2.length()));
								
								if ( sim1 > 0.8 ||
										sim2 > 0.8 ) {
									matchingSentence = ss;
									
									if ( sim1 < 0.8 || sim2 < 0.8 ) 
										issues.add(fs.getFrameId());
										//logger.warn("Mismatch: \n\tClearTk: '" 
										//		+ matchingSentence.getCoveredText() + "'\n\t" 
										//		+ "REACH: '" + fs.getText() + "'");
										
									break LOOP;
								}
								
							}

						}
				
					}
					
					if( matchingSentence == null ) {
						//logger.warn("A. Can't find a match for " + fs.getFrameId() 
						//		+ "\n\t'" + fs.getText() + "'");
						misses.add(fs.getFrameId());
						continue FRIES_FRAMES;
					}
					
					matchFriesSentenceToClearTkSentence(jCas, matchingSentence, fs);
					
				}
				
			}
			
			logger.info("Matching "+uiD.getId()+" - misses: "+misses.size()+"; issues: "+issues.size());
			logger.info("Matching "+uiD.getId()+" - COMPLETED");
			
			
			String mm = "";
			for(String m : misses) {
				mm += m+"|";
			}
			infons.put("misses", mm);
			String ii = "";
			for(String i : issues) {
				ii += i+"|";
			}
			infons.put("issues", ii);

		} catch (Exception e) {

			throw new AnalysisEngineProcessException(e);

		}

	}

	private void matchFriesSentenceToClearTkSentence(
			JCas jCas, Sentence match, FRIES_Sentence fs) {
		
		for( UimaBioCAnnotation a : JCasUtil.selectCovered(UimaBioCAnnotation.class, match) ){
			Map<String, String> inf = UimaBioCUtils.convertInfons(a.getInfons());
			if( inf.get("type").equals("fries_sentence") 
					&& inf.get("value").equals(fs.getFrameId()) ) {
				return;
			}

		}
		
		UimaBioCAnnotation s = new UimaBioCAnnotation(jCas);
		s.setBegin(match.getBegin());
		s.setEnd(match.getEnd());
		Map<String, String> inf = new HashMap<String, String>();
		inf.put("value", fs.getFrameId());
		inf.put("type", "fries_sentence");
		inf.put("friesText", fs.getText());
		s.setInfons(UimaBioCUtils.convertInfons(inf, jCas));
		
		FSArray locations = new FSArray(jCas, 1);
		s.setLocations(locations);
		
		UimaBioCLocation l = new UimaBioCLocation(jCas);
		l.setOffset(s.getBegin());
		l.setLength(s.getEnd()-s.getBegin());
		s.setLocations(0, l);
		s.addToIndexes();
		
	}

	private int countChars(String s, String c) {

		String mod = s.replaceAll(c, "");
		return (s.length() - mod.length());

	}
	

}
