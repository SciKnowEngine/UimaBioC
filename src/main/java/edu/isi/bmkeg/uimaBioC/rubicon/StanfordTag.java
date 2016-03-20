package edu.isi.bmkeg.uimaBioC.rubicon;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.FSArray;
import org.apache.uima.resource.ResourceInitializationException;
import org.cleartk.token.type.Sentence;
import org.cleartk.token.type.Token;
import org.uimafit.component.JCasAnnotator_ImplBase;
import org.uimafit.util.JCasUtil;

import bioc.type.UimaBioCAnnotation;
import bioc.type.UimaBioCDocument;
import bioc.type.UimaBioCLocation;
import bioc.type.UimaBioCPassage;
import edu.isi.bmkeg.uimaBioC.UimaBioCUtils;
import edu.isi.bmkeg.uimaBioC.bin.rubicon.RUBICON_01_preprocessToBioC;
import edu.stanford.nlp.parser.lexparser.Options;
import edu.stanford.nlp.parser.lexparser.ParseFiles;
import edu.stanford.nlp.process.PTBEscapingProcessor;
import edu.stanford.nlp.tagger.maxent.MaxentTagger;
import edu.stanford.nlp.trees.TreebankLanguagePack;

public class StanfordTag extends JCasAnnotator_ImplBase {

	private static Logger logger = Logger.getLogger(StanfordTag.class);

	private String[] args;

	private PrintStream nullStream;

	private MaxentTagger tagger;
	public PTBEscapingProcessor escaper;
	public Options op;
	private ParseFiles pf;
	private TreebankLanguagePack tlp;

	private int numProcessed = 0;

	private List<Pattern> removePatterns = new ArrayList<Pattern>();

	@Override
	public void initialize(UimaContext context) throws ResourceInitializationException {

		super.initialize(context);

		tagger = new MaxentTagger(
				"edu/stanford/nlp/models/pos-tagger/english-bidirectional/english-bidirectional-distsim.tagger");

		nullStream = (new PrintStream(new OutputStream() {
			public void write(int b) {
			}
		}));

		removePatterns.add(Pattern.compile(",(\\d{3})"));
		removePatterns.add(Pattern.compile("([a-zA-Z]+)\\."));

	}

	@Override
	public void process(JCas jCas) throws AnalysisEngineProcessException {

		try {

			UimaBioCDocument uiD = JCasUtil.selectSingle(jCas, UimaBioCDocument.class);
			if (uiD.getId().equals("skip"))
				return;

			UimaBioCPassage docP = UimaBioCUtils.readDocument(jCas);

			List<Sentence> sentences = JCasUtil.selectCovered(Sentence.class, docP);
			int sCount = 0;
			for (Sentence s : sentences) {

				Map<Integer, Token> tokPos = new HashMap<Integer, Token>();
				String ss = "", ns = "";
				for (Token t : JCasUtil.selectCovered(Token.class, s)) {
					if (ss.length() > 0)
						ss += " ";

					//
					// Run fixes over the sentence to ensure correct running of
					// the MaxEntTagger, but we can't be sure that this won't 
					// change the tokenization. Need to build an 
					//
					String str = t.getCoveredText().replaceAll("_", "&underscore;");
					str = runFixes(str);

					//
					// this is an index of the tokens in the string with no whitespace
					tokPos.put(ns.length(), t); 
					
					ss += str;
					ns += str;
				}

				//
				// Linking the sentences in the document to the tagged sentences.
				// 
				String p = tagger.tagString(ss);
				
				String[] tokTagArray = p.split("\\s+");
				String ns2 = "";
				for(String tokTag : tokTagArray) {
					String[] tokTagTuple = tokTag.split("_");
					String tok = tokTagTuple[0];
					String tag = tokTagTuple[1];
					ns += tok;
					if( !tokPos.containsKey(ns2.length()) ) {
						logger.warn("Stanford tag mismatch (tokenization error): " + tokTag);
						continue;
					}
					Token storedTok = tokPos.get(ns2.length());
					
					UimaBioCAnnotation uiA = new UimaBioCAnnotation(jCas);
					FSArray locations = new FSArray(jCas, 1);
					uiA.setLocations(locations);
					UimaBioCLocation uiL = new UimaBioCLocation(jCas);
					uiL.setOffset(storedTok.getBegin());
					uiL.setLength(storedTok.getEnd() - storedTok.getBegin());
					locations.set(0, uiL);

					uiA.setBegin(uiL.getOffset());
					uiA.setEnd(uiL.getOffset() + uiL.getLength());

					uiL.setBegin(uiL.getOffset());
					uiL.setEnd(uiL.getOffset() + uiL.getLength());

					Map<String, String> infons = new HashMap<String, String>();
					infons.put("type", "stanford-tag");
					infons.put("text", tok);
					infons.put("value", tag);
					uiA.setInfons(UimaBioCUtils.convertInfons(infons, jCas));

					uiA.addToIndexes();

				}

				sCount++;

			}

		} catch (Exception e) {

			throw new AnalysisEngineProcessException(e);

		}

	}

	private String runFixes(String str) {
		for (Pattern patt : this.removePatterns) {
			Matcher m = patt.matcher(str);
			if (m.find())
				str = m.replaceAll(m.group(1));
		}
		str = str.replaceAll("\\\\", "");

		return str;
	}

}