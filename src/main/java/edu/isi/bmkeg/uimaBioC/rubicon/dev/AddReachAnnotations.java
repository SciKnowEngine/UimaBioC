package edu.isi.bmkeg.uimaBioC.rubicon.dev;

import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.DocumentAnnotation;
import org.apache.uima.resource.ResourceInitializationException;
import org.bigmech.fries.FRIES_Argument;
import org.bigmech.fries.FRIES_EntityMention;
import org.bigmech.fries.FRIES_EventMention;
import org.bigmech.fries.FRIES_Frame;
import org.bigmech.fries.FRIES_FrameCollection;
import org.bigmech.fries.FRIES_Passage;
import org.bigmech.fries.FRIES_Sentence;
import org.bigmech.fries.FRIES_XRef;
import org.cleartk.ml.CleartkAnnotator;
import org.cleartk.ml.Feature;
import org.cleartk.ml.Instance;
import org.cleartk.ml.feature.extractor.CleartkExtractor;
import org.cleartk.ml.feature.extractor.CoveredTextExtractor;
import org.cleartk.ml.feature.transform.extractor.TfidfExtractor;
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
import edu.isi.bmkeg.uimaBioC.utils.NeedlemanWunch;

public class AddReachAnnotations extends CleartkAnnotator<String> {

	public final static String PARAM_INPUT_DIRECTORY = ConfigurationParameterFactory
			.createConfigurationParameterName(AddReachAnnotations.class, "inDirPath");
	@ConfigurationParameter(mandatory = true, description = "Directory for the FRIES Frames.")
	String inDirPath;
	File inDir;

	CleartkExtractor<Sentence, Token> countsExtractor;
	TfidfExtractor<String, Sentence> tfidfExtractor;
	
	private StringMetric cosineSimilarityMetric;
	private StringMetric levenshteinSimilarityMetric;
	private StringMetric needlemanWunchMetric;
	private NeedlemanWunch nm;
	Pattern alignmentPattern = Pattern.compile("^(.{0,3}_+)");

	private CleartkExtractor<DocumentAnnotation, Token> extractor;

	private static Logger logger = Logger.getLogger(AddReachAnnotations.class);

	private Map<LapdftextXMLChunk, Integer> pgLookup = new HashMap<LapdftextXMLChunk, Integer>();

	private Pattern wbDetect = Pattern.compile("(\\w\\W|\\W\\w)");
	private Pattern wsDetect = Pattern.compile("\\s");

	public void initialize(UimaContext context) throws ResourceInitializationException {
		super.initialize(context);

		this.countsExtractor = new CleartkExtractor<Sentence, Token>(
		        Token.class,
		        new CoveredTextExtractor<Token>(),
		        new CleartkExtractor.Count(new CleartkExtractor.Covered()));

		this.tfidfExtractor = new TfidfExtractor<String, Sentence>(
		        "TFIDF",
		        countsExtractor);

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

			UimaBioCPassage docP = UimaBioCUtils.readDocument(jCas);
			List<Sentence> docSentences = JCasUtil.selectCovered(Sentence.class, docP);
			
			UimaBioCAnnotation title = UimaBioCUtils.readArticleTitle(jCas);
			List<Sentence> titleSentences = JCasUtil.selectCovered(Sentence.class, title);
			
			UimaBioCAnnotation abst = UimaBioCUtils.readAbstract(jCas);
			List<Sentence> abstSentences = JCasUtil.selectCovered(Sentence.class, abst);
			
			List<UimaBioCAnnotation> floats = UimaBioCUtils.readFloats(jCas);
			List<Sentence> floatSentences = new ArrayList<Sentence>();
			for( UimaBioCAnnotation f  :floats ) {
				floatSentences.addAll(JCasUtil.selectCovered(Sentence.class, f));
			}
			
			List<Sentence> sentences = new ArrayList<Sentence>();
			sentences.addAll(titleSentences);
			sentences.addAll(abstSentences);
			sentences.addAll(docSentences);
			
			for(Sentence s : sentences) {
				
				List<Feature> features = this.tfidfExtractor.extract(jCas, s);
				String outcome = s.getBegin() + "_" + s.getEnd();
				Instance<String> instance = new Instance<String>(
						outcome, features
						);
				
//				this.tfidfExtractor.save(documentFreqDataURI);
				
			}

			
			
			
			logger.info("Adding annotations from " + uiD.getId());

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
					.registerSubtype(FRIES_EventMention.class, "event-mention");

			Gson gson = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_DASHES)
					.registerTypeAdapterFactory(typeFactory).create();

			FRIES_FrameCollection fc1 = gson.fromJson(new FileReader(eventFrames), FRIES_FrameCollection.class);
			Map<String, Set<FRIES_EventMention>> eventMap = new HashMap<String, Set<FRIES_EventMention>>();
			for (FRIES_Frame frame : fc1.getFrames()) {
				if (frame instanceof FRIES_EventMention) {
					FRIES_EventMention em = (FRIES_EventMention) frame;
					String sn = em.getSentence();
					if (!eventMap.containsKey(sn))
						eventMap.put(sn, new HashSet<FRIES_EventMention>());
					eventMap.get(sn).add(em);
				}
			}

			FRIES_FrameCollection fc2 = gson.fromJson(new FileReader(entityFrames), FRIES_FrameCollection.class);

			Map<String, Set<FRIES_EntityMention>> entityMap = new HashMap<String, Set<FRIES_EntityMention>>();
			for (FRIES_Frame frame : fc2.getFrames()) {
				if (frame instanceof FRIES_EntityMention) {
					FRIES_EntityMention em = (FRIES_EntityMention) frame;
					String sn = em.getSentence();
					if (!entityMap.containsKey(sn))
						entityMap.put(sn, new HashSet<FRIES_EntityMention>());
					entityMap.get(sn).add(em);
				}
			}
			
			//
			// Work with the structure of the frame ids to order sentences. 
			//
			FRIES_FrameCollection fc3 = gson.fromJson(
					new FileReader(sentenceFrames), 
					FRIES_FrameCollection.class);
			Map<FRIES_Key, FRIES_Sentence> sentenceMap = new HashMap<FRIES_Key, FRIES_Sentence>();
			Map<FRIES_Key, FRIES_Passage> passageMap = new HashMap<FRIES_Key, FRIES_Passage>();
			for (FRIES_Frame frame : fc3.getFrames()) {
				if (frame instanceof FRIES_Sentence) {
					FRIES_Sentence fs = (FRIES_Sentence) frame;
					FRIES_Key key = new FRIES_Key(fs.getFrameId());
					sentenceMap.put(key, fs);
				} else if(frame instanceof FRIES_Passage) {
					FRIES_Passage fs = (FRIES_Passage) frame;
					FRIES_Key key = new FRIES_Key(fs.getFrameId());
					passageMap.put(key, fs);
				}
			}
			
			List<FRIES_Key> pKeys = new ArrayList<FRIES_Key>(passageMap.keySet());
			Collections.sort(pKeys);

			List<FRIES_Key> sMisses = new ArrayList<FRIES_Key>();
			
			List<FRIES_Key> sKeys = new ArrayList<FRIES_Key>(sentenceMap.keySet());
			Collections.sort(sKeys);

			int pos = -1;
			for(FRIES_Key sKey: sKeys) {
				
				FRIES_Sentence friesSentence = sentenceMap.get(sKey);
				String friesText = friesSentence.getText().replaceAll("\\s+", "");;
				
				int ii = -1, best_i=-1;
				float best = 0.0f;
				for(int i=pos+1; i<sentences.size(); i++) {
					Sentence ourSentence = sentences.get(i);
					String ourText = UimaBioCUtils.friesifySentence(jCas, ourSentence).replaceAll("\\s+", "");
					float sim = levenshteinSimilarityMetric.compare(friesText, ourText); 
					if( sim > 0.75 ) {
						ii = i;
						pos = i;
						break;
					} else {
						if( sim > best ) {
							best = sim;
							best_i = i;
						}
					}
				}
				
				if( ii == -1 ) {
					sMisses.add(sKey);
					continue;
				}
				
				UimaBioCAnnotation a = new UimaBioCAnnotation(jCas);
				Map<String, String> inf = new HashMap<String, String>();
				inf.put("type", "FRIES_Sentence");
				inf.put("value", friesSentence.getFrameId());
				a.setBegin(sentences.get(ii).getBegin());
				a.setEnd(sentences.get(ii).getEnd());
				a.setInfons(UimaBioCUtils.convertInfons(inf, jCas));
			
			}
				
			// If we can't find them in the main text, 
			// look for them in floating boxes
			pos = -1;
			for(FRIES_Key sKey: sMisses) {
				
				FRIES_Sentence friesSentence = sentenceMap.get(sKey);
				String friesText = friesSentence.getText();
				
				int ii = -1;
				for(int i=0; i<floatSentences.size(); i++) {
					Sentence ourSentence = sentences.get(i);
					String ourText = ourSentence.getCoveredText();
					float sim = levenshteinSimilarityMetric.compare(friesText, ourText); 
					if( sim > 0.75 ) {
						ii = i;
						pos = i;
						break;
					} 
				}
				
				if( ii == -1 ) {
					sMisses.add(sKey);
					continue;
				}
				
				UimaBioCAnnotation a = new UimaBioCAnnotation(jCas);
				Map<String, String> inf = new HashMap<String, String>();
				inf.put("type", "FRIES_Sentence");
				inf.put("value", friesSentence.getFrameId());
				a.setBegin(sentences.get(ii).getBegin());
				a.setEnd(sentences.get(ii).getEnd());
				a.setInfons(UimaBioCUtils.convertInfons(inf, jCas));
			
			}
			
			
			
			int pauseHere = 0;
			pauseHere++;
					/*if (fs.getgetText().length() < 10)
						continue;

					float bestSim = 0.0f;
					Sentence match = null;
					for (Sentence s : sentences) {
						float sim = cosineSimilarityMetric.compare(s.getCoveredText(), fs.getText());
						if (sim > bestSim) {
							match = s;
							bestSim = sim;
						}
						if (sim > 0.9) {
							break;
						}
					}

					if (match != null) {

						//
						// Check for the best match based on Levenshtein edit
						// distance
						// to make sure that it's correct.
						// We also truncate the match to the length of the FRIES
						// corpus'
						// sentence to ignore errors from the FRIES sentence
						// splitter.
						//
						String ss1 = fs.getText().replaceAll("\\s+", "");
						String ss2 = match.getCoveredText().replaceAll("\\s+", "");
						if (ss2.length() > ss1.length() + 5) {
							ss2 = ss2.substring(0, ss1.length() + 5);
						}
						float sim = levenshteinSimilarityMetric.compare(ss1, ss2);
						if (sim < 0.85)
							continue FRIES_FRAMES;

						if (eventMap.containsKey(fs.getFrameId())) {
							for (FRIES_EventMention em : eventMap.get(fs.getFrameId())) {
								matchFriesEventToClearTkSentence(jCas, bestSim, match, em);
							}
						}
						/*if (entityMap.containsKey(fs.getFrameId())) {
							for (FRIES_EntityMention em : entityMap.get(fs.getFrameId())) {
								matchFriesEventToClearTkSentence(jCas, bestSim, match, em);
							}
						}*/
						
					//}
					
				//}
				
			//}

		} catch (Exception e) {

			throw new AnalysisEngineProcessException(e);

		}

	}

	private void matchFriesEventToClearTkSentence(JCas jCas, float bestSim, Sentence match, FRIES_Frame f) {
		
		UimaBioCAnnotation a = new UimaBioCAnnotation(jCas);
		Map<String, String> inf = new HashMap<String, String>();
		inf.put("eventId", f.getFrameId());
		inf.put("sentId", f.getFrameId());
		inf.put("score", bestSim + "");
		
		if( f instanceof FRIES_EventMention) {
			
			FRIES_EventMention em = (FRIES_EventMention) f;
			inf.put("type", "FRIES_EventMention");
			inf.put("fType", em.getType());
			inf.put("fSubType", em.getSubtype());
			inf.put("friesEventText", em.getText());
			String code = "";
			for (FRIES_Argument args : em.getArguments()) {
				code += "[" + args.getText() + "]";
			}
			inf.put("value", code);
			a.setInfons(UimaBioCUtils.convertInfons(inf, jCas));
			
		} else  if( f instanceof FRIES_EntityMention) {
			
			FRIES_EntityMention em = (FRIES_EntityMention) f;
			inf.put("type", "FRIES_EventMention");
			inf.put("fType", em.getType());
			inf.put("fSubType", em.getSubtype());
			inf.put("friesEventText", em.getText());
			String code = "";
			for (FRIES_XRef args : em.getXrefs()) {
				code += "[" + args.getNamespace() + ":" + args.getId() + "]";
			}
			inf.put("value", code);
			a.setInfons(UimaBioCUtils.convertInfons(inf, jCas));
			
		}

		
		// Need to align the text from FRIES
		// Ideally find an exact match
		int pos = match.getCoveredText().lastIndexOf(f.getText());
		if (pos != -1) {

			a.setBegin(match.getBegin() + pos);
			a.setEnd(match.getBegin() + pos + f.getText().length());
			a.addToIndexes(jCas);

		} else {

			//
			// If that doesn't work then match based on
			// from incorrectly matched whitespace.
			//
			String no_ws1 = match.getCoveredText().replaceAll("\\s+", "");
			String no_ws2 = f.getText().replaceAll("\\s+", "");
			int no_ws_pos = no_ws1.lastIndexOf(no_ws2) + 1;

			if (no_ws_pos != -1) {
				String s1 = match.getCoveredText();
				int startPos = 0;
				int endPos = 0;
				int p = 0;
				for (int i = 0; i < s1.length(); i++) {
					if (!s1.substring(i, i + 1).matches("\\s"))
						p++;
					if (p == no_ws_pos)
						startPos = i;
					if (p == no_ws_pos + no_ws2.length())
						endPos = i;
				}
				if( endPos == 0 ) 
					endPos = match.getEnd();
				
				a.setBegin(match.getBegin() + startPos);
				a.setEnd(match.getBegin() + endPos);
				try {
					logger.info("new: " + a.getCoveredText());
					logger.info("old: " + f.getText());
					a.addToIndexes(jCas);
				} catch( Exception e ) {
					e.printStackTrace();
				}
			}
			//
			// If that doesn't work,
			// use Needleman Wunch algorithm.
			//
			else {

				float sim2 = this.needlemanWunchMetric.compare(match.getCoveredText(),
						f.getText());
				String s1Align = this.nm.getString1Alignment();
				String s2Align = this.nm.getString2Alignment();

				//
				// Using this algorithm, the string
				// alignments seems
				// to make a substitution error at the
				// start, which
				// looks like "f__________"
				// - We detect this and correct for it.
				//
				Matcher m = this.alignmentPattern.matcher(s2Align);
				if (m.find()) {
					int offset = m.group(1).length() - 1;

					String s1Remain = s1Align.substring(offset, s1Align.length());
					int s1ModCount = this.countChars(s1Remain, "_");

					String s2Remain = s2Align.substring(offset, s2Align.length());
					int s2ModCount = this.countChars(s2Remain, "_");

					int len = s2Remain.length() - s2ModCount + s1ModCount;

					a.setBegin(match.getBegin() + offset);
					a.setEnd(match.getBegin() + offset + len);
					try {
						logger.info("new: " + a.getCoveredText());
						logger.info("old: " + f.getText());
						a.addToIndexes(jCas);
					} catch( Exception e ) {
						e.printStackTrace();
					}

				} else {

					a.setBegin(match.getBegin());
					a.setEnd(match.getEnd());
					try {
						logger.warn("Entity: " + f.getText());
						logger.warn("Error Aligning Texts");
						a.addToIndexes(jCas);
					} catch( Exception e ) {
						e.printStackTrace();
					}

				}
				
			}
			
		}
	}

	private int countChars(String s, String c) {

		String mod = s.replaceAll(c, "");
		return (s.length() - mod.length());

	}
	
	private class FRIES_Key implements Comparable<FRIES_Key>{
		
		private String key;
		
		public FRIES_Key(String key) {
			this.key = key;
		}
		
		public String getKey() {
			return key;
		}

		public void setKey(String key) {
			this.key = key;
		}

		//"pass-15550174-UAZ-r1-0"
		private int read_p_index() {
			String[] tup = this.key.split("\\-");
			String pStr = tup[4];
			return new Integer(pStr);
		}

		//"pass-15550174-UAZ-r1-0-0"
		private int read_s_index() {
			String[] tup = this.key.split("\\-");
			String sStr = tup[5];
			return new Integer(sStr);
		}
		
		@Override
		public int compareTo(FRIES_Key compareKey)  {
			
			if( this.key.startsWith("pass") )
				return this.read_p_index() - compareKey.read_p_index();
			else {

				if( this.read_p_index() != compareKey.read_p_index() ) 
					return this.read_p_index() - compareKey.read_p_index();
				else 
					return this.read_s_index() - compareKey.read_s_index();					
				
			}
				
		}
		
		public String toString() {
			return this.key;
		}
		
		
	}


}
