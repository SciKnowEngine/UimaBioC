package edu.isi.bmkeg.uimaBioC.uima.ae.core;

import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.FSArray;
import org.apache.uima.jcas.tcas.DocumentAnnotation;
import org.apache.uima.resource.ResourceInitializationException;
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

import bioc.type.UimaBioCAnnotation;
import bioc.type.UimaBioCDocument;
import bioc.type.UimaBioCLocation;
import bioc.type.UimaBioCPassage;
import edu.isi.bmkeg.lapdf.xml.model.LapdftextXMLChunk;
import edu.isi.bmkeg.lapdf.xml.model.LapdftextXMLDocument;
import edu.isi.bmkeg.lapdf.xml.model.LapdftextXMLPage;
import edu.isi.bmkeg.lapdf.xml.model.LapdftextXMLWord;
import edu.isi.bmkeg.uimaBioC.UimaBioCUtils;
import edu.isi.bmkeg.utils.xml.XmlBindingTools;

public class MatchPdfBlocksAndSentencesToNxmlText extends
		JCasAnnotator_ImplBase {

	private class WordAnchorPosition {

		private LapdftextXMLChunk chunk;
		private int wordPos;
		private int docPos;
		private int nHits;

		private WordAnchorPosition(LapdftextXMLChunk chunk, int wordPos,
				int docPos, int nHits) {
			this.chunk = chunk;
			this.wordPos = wordPos;
			this.docPos = docPos;
			this.nHits = nHits;

		}

	}

	public static final String LAPDF_DIR = ConfigurationParameterFactory
			.createConfigurationParameterName(
					MatchPdfBlocksAndSentencesToNxmlText.class, "lapdfDir");
	@ConfigurationParameter(mandatory = true, description = "Layout Aware PDF XML Directory")
	protected File lapdfDir;

	private StringMetric cosineSimilarityMetric;
	private StringMetric levenshteinSimilarityMetric;

	private CleartkExtractor<DocumentAnnotation, Token> extractor;

	private static Logger logger = Logger
			.getLogger(MatchPdfBlocksAndSentencesToNxmlText.class);

	private List<Sentence> sentences = new ArrayList<Sentence>();
	private Map<LapdftextXMLChunk, Integer> pgLookup = new HashMap<LapdftextXMLChunk, Integer>();
	
	private Pattern wbDetect = Pattern.compile("\\b");
	
	public void initialize(UimaContext context)
			throws ResourceInitializationException {

		super.initialize(context);

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

			// Want to locate the 'body' section.
			int bodyBegin = -1;
			int bodyEnd = -1;
			List<UimaBioCAnnotation> annotations = JCasUtil.selectCovered(
					UimaBioCAnnotation.class, docP);
			for (UimaBioCAnnotation uiA : annotations) {
				Map<String, String> infons = UimaBioCUtils.convertInfons(uiA
						.getInfons());
				if (infons.containsKey("type")
						&& infons.get("type").equals("body")) {
					bodyBegin = uiA.getBegin();
					bodyEnd = uiA.getEnd();
				}
			}

			if (bodyBegin == -1) {
				throw new Exception("Can't find body section of paper.");
			}

			File lapdfFile = new File(this.lapdfDir.getPath() + "/"
					+ uiD.getId() + "_lapdf.xml");

			FileReader reader = new FileReader(lapdfFile);
			LapdftextXMLDocument xmlDoc = XmlBindingTools.parseXML(reader,
					LapdftextXMLDocument.class);

			String txt = jCas.getDocumentText();

			pgLookup = new HashMap<LapdftextXMLChunk, Integer>();
			List<UimaBioCAnnotation> annotationsToAdd = 
					new ArrayList<UimaBioCAnnotation>();

			for (LapdftextXMLPage xmlPage : xmlDoc.getPages()) {
				for (LapdftextXMLChunk xmlChunk : xmlPage.getChunks()) {
					if (xmlChunk.getWords().size() > 0) {
					
						Map<LapdftextXMLWord,UimaBioCAnnotation> lookup = 
								new HashMap<LapdftextXMLWord,UimaBioCAnnotation>();
						
						int p = xmlPage.getPageNumber();
						WordAnchorPosition anchor = readAnchorForChunk(jCas,
								xmlChunk);
						
						if (anchor.docPos == -1 
								|| anchor.docPos < bodyBegin
								|| anchor.docPos > bodyEnd) {
							continue;
						}

						//
						// The remaining words in the chunk after
						// the anchor should all be a fairly good
						// match (assuming that the chunk is contiguous)
						//
						List<LapdftextXMLWord> wordList = anchor.chunk
								.getWords();
						int offset = 0;
						for (int i = anchor.wordPos; i < wordList.size(); i++) {
							LapdftextXMLWord w = wordList.get(i);

							int begin = tweakWordLocation(txt, anchor.docPos, offset, w);
							int end = begin + w.getT().length();

							UimaBioCAnnotation a = createWordAnnotation(jCas, p, w, begin, end);
							annotationsToAdd.add(a);
							lookup.put(w, a);

							offset += w.getT().length() + 1;							

						}
						
						//
						// Go back in the block from the anchor and 
						// try to match words as needed.
						//
						offset = 0;
						for (int i=anchor.wordPos-1; i>=0; i--) {
							LapdftextXMLWord w = wordList.get(i);
							offset = offset - w.getT().length() - 1;  
									
							int begin = tweakWordLocation(txt, anchor.docPos, offset, w);
							int end = begin + w.getT().length();

							UimaBioCAnnotation a = createWordAnnotation(jCas, p, w, begin, end);
							annotationsToAdd.add(a);
							lookup.put(w, a);

						}
						
						LapdftextXMLWord first = wordList.get(0);
						UimaBioCAnnotation firstAnnotation = lookup.get(first);
						LapdftextXMLWord last = wordList.get(wordList.size()-1);
						UimaBioCAnnotation lastAnnotation = lookup.get(last);
						int begin = firstAnnotation.getBegin();
						int end = lastAnnotation.getEnd();
					
						annotationsToAdd.add( 
								createChunkAnnotation(jCas, p, xmlChunk, begin, end)
								);
							
					}
					
					
				}

			}

			FSArray existingAnnotations = docP.getAnnotations();
			int n = existingAnnotations.size();
			FSArray newAnnotations = new FSArray(jCas, 
					existingAnnotations.size() + annotationsToAdd.size() );
			for(int i=0; i<n; i++) {
				newAnnotations.set(i, existingAnnotations.get(i));
			}
			for(int j=0; j<annotationsToAdd.size(); j++) {
				newAnnotations.set(n+j, annotationsToAdd.get(j));				
			}
			docP.setAnnotations(newAnnotations);

		} catch (Exception e) {

			throw new AnalysisEngineProcessException(e);

		}

	}

	private UimaBioCAnnotation createWordAnnotation(JCas jCas, int p, LapdftextXMLWord w,
			int begin, int end) {
		
		UimaBioCAnnotation uiA = new UimaBioCAnnotation(
				jCas);
		uiA.setBegin(begin);
		uiA.setEnd(end);
		Map<String, String> infons = new HashMap<String, String>();
		infons.put("type", "lapdf-word-block");
		infons.put("x", w.getX() + "");
		infons.put("y", w.getY() + "");
		infons.put("w", w.getW() + "");
		infons.put("h", w.getH() + "");
		infons.put("t", w.getT() + "");
		infons.put("font", w.getFont());
		infons.put("p", p + "");
		uiA.setInfons(UimaBioCUtils.convertInfons(infons,
				jCas));
		uiA.addToIndexes();

		FSArray locations = new FSArray(jCas, 1);
		uiA.setLocations(locations);
		UimaBioCLocation uiL = new UimaBioCLocation(jCas);
		locations.set(0, uiL);
		uiL.setOffset(begin);
		uiL.setLength(end - begin);
		
		return uiA;
	}
	
	private UimaBioCAnnotation createChunkAnnotation(JCas jCas, int p, LapdftextXMLChunk c,
			int begin, int end) {
		
		UimaBioCAnnotation uiA = new UimaBioCAnnotation(
				jCas);
		uiA.setBegin(begin);
		uiA.setEnd(end);
		Map<String, String> infons = new HashMap<String, String>();
		infons.put("type", "lapdf-chunk-block");
		infons.put("x", c.getX() + "");
		infons.put("y", c.getY() + "");
		infons.put("w", c.getW() + "");
		infons.put("h", c.getH() + "");
		infons.put("font", c.getFont());
		infons.put("p", p + "");
		uiA.setInfons(UimaBioCUtils.convertInfons(infons,
				jCas));
		uiA.addToIndexes();

		FSArray locations = new FSArray(jCas, 1);
		uiA.setLocations(locations);
		UimaBioCLocation uiL = new UimaBioCLocation(jCas);
		locations.set(0, uiL);
		uiL.setOffset(begin);
		uiL.setLength(end - begin);
		
		return uiA;
	}

	private int tweakWordLocation(String txt, int docPos,
			int offset, LapdftextXMLWord w) {
		
		int begin = docPos + offset;

		// It's important to get the location of the start 
		// of the word correct but not essential that 
		// there be a perfect match. Look around for 
		// a match for the first letter of the word.
		// If we can't get that, then we just use the 
		// word from the offsets of word lengths.  
		int tweak = 0;
		boolean tweakForward = true;
		int maxTweak = 10;
		while (!txt.substring(begin, begin+1).equals(w.getT().substring(0, 1)) 
				&& !(begin > 0 && wbDetect.matcher(txt.substring(begin-1, begin+1)).find())
				&& tweak < maxTweak ) {
			
			if( tweakForward ) {
				begin = docPos + offset + Math.round(tweak/2.0f);
			} else {
				begin = docPos + offset - Math.round(tweak/2.0f);
			}
			tweak++;
			tweakForward = !tweakForward;
		}
		
		if( tweak == maxTweak ) {
			begin = docPos + offset;
		}
		return begin;
	}

	private WordAnchorPosition readAnchorForChunk(JCas jCas,
			LapdftextXMLChunk chunk) {

		String txt = jCas.getDocumentText();

		int nWords = 10;
		int offset = 0;
		int hitLocation = -1;
		int nHits = 0;
		boolean stillLooking = true;
		while (stillLooking) {

			String chunkTxt = readSubstringFromChunk(chunk, offset, nWords);
			nHits = this.countHits(txt, chunkTxt);

			hitLocation = txt.indexOf(chunkTxt);
			if (hitLocation > -1 && nHits == 1) {
				stillLooking = false;
			} else {
				if (offset == chunk.getWords().size()) {
					stillLooking = false;
				}
				offset++;
			}

		}

		WordAnchorPosition wap = new WordAnchorPosition(chunk, offset,
				hitLocation, nHits);

		return wap;

	}

	private String readSubstringFromChunk(LapdftextXMLChunk chunk, int offset,
			int nWords) {

		String chunkText = "";
		for (int i = offset; i < nWords; i++) {

			if (i >= chunk.getWords().size())
				return chunkText;

			LapdftextXMLWord xmlWord = chunk.getWords().get(i);
			if (chunkText.length() > 0)
				chunkText += " ";
			chunkText += xmlWord.getT();
		}

		return chunkText;

	}

	private int countHits(String docText, String chunkText) {

		if (chunkText.length() == 0)
			return 0;

		int hitLocation = -1;
		int nHits = 0;
		while ((hitLocation = docText.indexOf(chunkText, hitLocation + 1)) != -1) {
			nHits++;
		}
		return nHits;

	}

}
