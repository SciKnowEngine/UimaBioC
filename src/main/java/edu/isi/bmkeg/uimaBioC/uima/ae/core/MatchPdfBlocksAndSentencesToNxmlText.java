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

	private Pattern wbDetect = Pattern.compile("(\\w\\W|\\W\\w)");
	private Pattern wsDetect = Pattern.compile("\\s");

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

			logger.info(uiD.getId());

			// Want to locate the 'body' section.
			int bodyBegin = -1;
			int bodyEnd = -1;
			List<UimaBioCAnnotation> annotations = JCasUtil.selectCovered(
					UimaBioCAnnotation.class, docP);
			for (UimaBioCAnnotation uiA : annotations) {
				Map<String, String> infons = UimaBioCUtils.convertInfons(uiA
						.getInfons());
				if (infons.containsKey("type")
						&& infons.get("type").equals("formatting") 
						&& infons.get("value").equals("body")) {
					bodyBegin = uiA.getBegin();
					bodyEnd = uiA.getEnd();
				}
			}

			if (bodyBegin == -1) {
				logger.error("Can't find body section of paper.");
				return;
			}

			File lapdfFile = new File(this.lapdfDir.getPath() + "/"
					+ uiD.getId() + "_lapdf.xml");

			if (!lapdfFile.exists()) {
				logger.error("Can't find " + lapdfFile.getPath());
				return;
			}
			
			FileReader reader = new FileReader(lapdfFile);
			LapdftextXMLDocument xmlDoc = XmlBindingTools.parseXML(reader,
					LapdftextXMLDocument.class);

			//
			// Since we use single spaces for all whitespace
			// in the fragments, use that here too.
			//
			String txt = jCas.getDocumentText().replaceAll("\\s", " ");

			pgLookup = new HashMap<LapdftextXMLChunk, Integer>();
			List<UimaBioCAnnotation> annotationsToAdd = new ArrayList<UimaBioCAnnotation>();
			List<LapdftextXMLChunk> unassignedChunks = new ArrayList<LapdftextXMLChunk>();

			for (LapdftextXMLPage xmlPage : xmlDoc.getPages()) {
				for (LapdftextXMLChunk xmlChunk : xmlPage.getChunks()) {
					if (xmlChunk.getWords().size() > 0) {

						Map<LapdftextXMLWord, UimaBioCAnnotation> lookup = new HashMap<LapdftextXMLWord, UimaBioCAnnotation>();

						int p = xmlPage.getPageNumber();

						WordAnchorPosition anchor = readAnchorForChunk(txt,
								xmlChunk);
						
						if (anchor == null) {
							unassignedChunks.add(xmlChunk);
							continue;
						}

						List<LapdftextXMLWord> chunkWords = 
								new ArrayList<LapdftextXMLWord>(xmlChunk.getWords());
						if( anchor.wordPos > 0)
							chunkWords.removeAll(chunkWords.subList(0, anchor.wordPos-1));
						
						if (anchor.docPos == -1 || anchor.docPos < bodyBegin
								|| anchor.docPos > bodyEnd) {
							continue;
						}

						//
						// The remaining words in the chunk after
						// the anchor should all be a fairly good
						// match (assuming that the chunk is contiguous)
						//
						List<LapdftextXMLWord> wordList = anchor.chunk.getWords();
						int offset = 0;
						Sentence thisSentence = null, nextSentence = null;
						float checkForNextSentence = -1f;
						for (int i = anchor.wordPos; i < wordList.size(); i++) {
							LapdftextXMLWord w = wordList.get(i);

							float[] tweak;
							try {
								tweak = tweakWordLocationAndLength(txt,
										anchor.docPos, offset, w);
							} catch (Exception e) {

								//System.out.println("\t\t" + w.getT());

								offset += w.getT().length() + 1;
								continue;
							}

							int begin = anchor.docPos + offset + (int) tweak[0];
							int end = begin + w.getT().length() + (int) tweak[1];

							//System.out.println((int)tweak[0] + "," + (int)tweak[1] + ":"
							//		+ w.getT() + "/"
							//		+ txt.substring(begin, end) + tweak[2]);

							UimaBioCAnnotation a = createWordAnnotation(jCas,
									p, w, begin, end);
							annotationsToAdd.add(a);
							lookup.put(w, a);
							
							chunkWords.remove(w);
							String chunkText = "";
							for(LapdftextXMLWord cw : chunkWords) {
								if( chunkText.length() > 0 )
									chunkText += " ";
								chunkText += cw.getT();
							}
							
							//
							// Check if the next sentence in the block is present in the 
							// text. There can be differences in paragraph placement
							// between the PDF and the XML.
							// 
							// 1. Read the next sentence from the jCas and compare
							//
							List<Sentence> sList = JCasUtil.selectCovering(jCas, 
									Sentence.class, begin, end);
							if( sList.size() > 0 && sList.get(0) != thisSentence ) {
								thisSentence = sList.get(0);	
							}
							// 2. If the next word places us in the next sentence and 
							//    there are following sentences is not present in the block then
							//    add a modified offset to start looking for the next word.
							int nextWordBegin = anchor.docPos + offset + (int)tweak[0] 
									+ w.getT().length() + (int)tweak[1] + 1;
							if( nextWordBegin >= thisSentence.getEnd() ) {
								
								// We read over a whole bunch of following sentences in the document
								// if we find one that matches, we then skip ahead to that sentence 
								// in this read.
								int skip = 0;
								sList = JCasUtil.selectFollowing(jCas, Sentence.class, thisSentence, 12);
								for( Sentence s : sList ) {
									int tempEnd = (s.getEnd()-s.getBegin())>chunkText.length()?
											chunkText.length():s.getEnd()-s.getBegin();
									checkForNextSentence = cosineSimilarityMetric.compare(
											s.getCoveredText().substring(0,tempEnd), 
											chunkText.substring(0,tempEnd) );
									if( checkForNextSentence < 0.8 ) {
										skip += tempEnd + 1;
									} else {
										offset += skip;
										break;
									}
								}
								
							}
							offset += tweak[0] + w.getT().length() + tweak[1] + 1;
							
						}

						//
						// Go back in the block from the anchor and
						// try to match words as needed.
						//
						offset = 0;
						for (int i = anchor.wordPos - 1; i >= 0; i--) {
							LapdftextXMLWord w = wordList.get(i);
							offset = offset - w.getT().length() - 1;

							float[] tweak;
							try {
								tweak = tweakWordLocationAndLength(txt,
										anchor.docPos, offset, w);
							} catch (Exception e) {
								continue;
							}
							offset += tweak[0];
							int begin = anchor.docPos + offset;
							int end = begin + w.getT().length() + (int) tweak[1];

							//System.out.println(tweak[0] + "," + tweak[1] + ":"
							//		+ w.getT() + "/"
							//		+ txt.substring(begin, end) + ": " + tweak[2]);

							UimaBioCAnnotation a = createWordAnnotation(jCas,
									p, w, begin, end);
							annotationsToAdd.add(a);
							lookup.put(w, a);

						}

						int i = 0;
						while (!lookup.containsKey(wordList.get(i)))
							i++;
						UimaBioCAnnotation firstAnnotation = lookup
								.get(wordList.get(i));

						i = wordList.size() - 1;
						while (!lookup.containsKey(wordList.get(i)))
							i--;
						UimaBioCAnnotation lastAnnotation = lookup.get(wordList
								.get(i));

						int begin = firstAnnotation.getBegin();
						int end = lastAnnotation.getEnd();

						annotationsToAdd.add(createChunkAnnotation(jCas, p,
								xmlChunk, begin, end));

					} else {

						int pauseHere = 0;
					}

				}

			}

			FSArray existingAnnotations = docP.getAnnotations();
			int n = existingAnnotations.size();
			FSArray newAnnotations = new FSArray(jCas,
					existingAnnotations.size() + annotationsToAdd.size());
			for (int i = 0; i < n; i++) {
				newAnnotations.set(i, existingAnnotations.get(i));
			}
			for (int j = 0; j < annotationsToAdd.size(); j++) {
				newAnnotations.set(n + j, annotationsToAdd.get(j));
			}
			docP.setAnnotations(newAnnotations);

		} catch (Exception e) {

			throw new AnalysisEngineProcessException(e);

		}

	}

	private UimaBioCAnnotation createWordAnnotation(JCas jCas, int p,
			LapdftextXMLWord w, int begin, int end) {

		UimaBioCAnnotation uiA = new UimaBioCAnnotation(jCas);
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
		uiA.setInfons(UimaBioCUtils.convertInfons(infons, jCas));
		uiA.addToIndexes();

		if (!w.getT().toLowerCase().equals(uiA.getCoveredText().toLowerCase())) {
			int pause = 0;
			pause++;
		}

		FSArray locations = new FSArray(jCas, 1);
		uiA.setLocations(locations);
		UimaBioCLocation uiL = new UimaBioCLocation(jCas);
		locations.set(0, uiL);
		uiL.setOffset(begin);
		uiL.setLength(end - begin);

		return uiA;
	}

	private UimaBioCAnnotation createChunkAnnotation(JCas jCas, int p,
			LapdftextXMLChunk c, int begin, int end) {

		UimaBioCAnnotation uiA = new UimaBioCAnnotation(jCas);
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
		uiA.setInfons(UimaBioCUtils.convertInfons(infons, jCas));
		uiA.addToIndexes();

		FSArray locations = new FSArray(jCas, 1);
		uiA.setLocations(locations);
		UimaBioCLocation uiL = new UimaBioCLocation(jCas);
		locations.set(0, uiL);
		uiL.setOffset(begin);
		uiL.setLength(end - begin);

		return uiA;
	}

	/**
	 * 
	 * @param txt
	 *            : text of the whole file
	 * @param docPos
	 *            : position in that text of an anchor word
	 * @param offset
	 *            : offset from that anchor of the current word
	 * @param w
	 *            : the current word
	 * @return int[]: an array of two numbers describing the best guess for the
	 *         offset begin and end of the current word. Use a greedy search to
	 *         match the string with a minimum number of edits based on
	 *         Levenshtein distance to the text in the PDF word block.
	 * @throws Exception
	 */
	private float[] tweakWordLocationAndLength(String txt, int docPos,
			int offset, LapdftextXMLWord w) throws Exception {

		int begin = docPos + offset;
		int end = begin + w.getT().length();

		String testWord = txt.substring(begin, end);
		if (testWord.equals(w.getT())) {
			float[] tweak = { 0.0f, 0.0f, 0.0f };
			return tweak;
		}

		float bestSim = -1.0f;
		int bestBegin = 0, bestEnd = 0;

		int kk[] = { -1, 1 };
		for (int i = 0; i < 5; i++) { // increase the length of the string
			for (int j = 1; j < 6; j++) { // vary the start position
				for (int k : kk) { // vary the direction

					begin = docPos + offset + (j * k);
					end = begin + w.getT().length() + i;
					testWord = txt.substring(begin, end);

					if (testWord.equals(w.getT())) {
						float[] tweak = { begin - (docPos + offset),
								end - (begin + w.getT().length()), 
								1.0f };
						return tweak;
					}

					float sim = levenshteinSimilarityMetric.compare(testWord,
							w.getT());
					if (sim > bestSim) {
						bestSim = sim;
						bestBegin = begin;
						bestEnd = end;
					}

				}

			}

		}

		begin = bestBegin;
		end = bestEnd;

		if (bestSim < 0.5)
			throw new Exception("Can't find word:" + w.getT() + " in block");

		float[] tweak = { begin - (docPos + offset),
				end - (begin + w.getT().length()), 
				bestSim};
		
		return tweak;

	}

	private WordAnchorPosition readAnchorForChunk(String txt,
			LapdftextXMLChunk chunk) {

		int nWords = 10;
		int offset = 0;
		int hitLocation = -1;
		int nHits = 0;
		boolean stillLooking = true;
		while (stillLooking) {

			String chunkTxt = readSubstringFromChunk(chunk, offset, nWords);
			if (chunkTxt.length() == 0)
				return null;

			nHits = this.countHits(txt, chunkTxt);

			hitLocation = txt.indexOf(chunkTxt);
			if (hitLocation > -1 && nHits == 1) {
				stillLooking = false;
			} else {
				if (offset == chunk.getWords().size()) {
					return null;
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
		for (int i = offset; i < nWords + offset; i++) {

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
