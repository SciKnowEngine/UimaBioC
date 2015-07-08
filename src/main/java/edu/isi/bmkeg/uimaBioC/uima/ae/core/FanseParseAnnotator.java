package edu.isi.bmkeg.uimaBioC.uima.ae.core;

import java.io.StringReader;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.uimafit.component.JCasAnnotator_ImplBase;
import org.uimafit.util.JCasUtil;

import tratz.parse.types.Token;
import tratz.parse.types.Arc;

import bioc.type.UimaBioCDocument;
import bioc.type.UimaBioCPassage;
import edu.isi.mavuno.input.TratzParsedDocument;
import edu.isi.mavuno.nlp.NLProcTools;
import edu.isi.mavuno.util.SentenceWritable;
import edu.isi.mavuno.util.TokenFactory;
import edu.isi.mavuno.util.TratzParsedTokenWritable;
import edu.stanford.nlp.ling.Word;
import edu.stanford.nlp.process.PTBTokenizer;

public class FanseParseAnnotator extends JCasAnnotator_ImplBase {

	private static final Logger sLogger = Logger.getLogger(FanseParseAnnotator.class);

	// text utility class
	private final NLProcTools mTextUtils = new NLProcTools();

	// parsed document
	private final TratzParsedDocument mDoc = new TratzParsedDocument();

	// token factory for TratzParsedTokenWritables
	private static final TokenFactory<TratzParsedTokenWritable> TOKEN_FACTORY = new TratzParsedTokenWritable.ParsedTokenFactory();

	public void initialize(UimaContext context)
			throws ResourceInitializationException {

		super.initialize(context);
		
		try {

			// initialize WordNet (needed by POS tagger)
			mTextUtils.initializeWordNet();

			// initialize POS tagger
			mTextUtils.initializePOSTagger();

			// initialize chunker
			mTextUtils.initializeChunker();
		
			// initialize named entity tagger
			mTextUtils.initializeNETagger();

			// initialize parser
			mTextUtils.initializeTratzParser();
			
		} catch(Exception e) {
				
			throw new ResourceInitializationException( e);
		
		}		
		
	}
	
	public void process(JCas jCas) throws AnalysisEngineProcessException {

		UimaBioCDocument uiD = JCasUtil.selectSingle(jCas, UimaBioCDocument.class);
		UimaBioCPassage docPassage = null;
		
		List<org.cleartk.token.type.Sentence> uimaSentences = JCasUtil.selectCovered(
						org.cleartk.token.type.Sentence.class, uiD);

		for(org.cleartk.token.type.Sentence s : uimaSentences) {
			
			try {
				
				List<org.cleartk.token.type.Token> uimaTokens = JCasUtil.selectCovered(
						org.cleartk.token.type.Token.class, 
						s);

				// skip very long sentences
				if(uimaTokens.size() > NLProcTools.MAX_SENTENCE_LENGTH) {
					continue;
				}
				
				PTBTokenizer<Word> tokenizer = PTBTokenizer.newPTBTokenizer(
						new StringReader(s.getCoveredText()));
				List<Word> sentence = tokenizer.tokenize();

				// set the sentence to be processed
				mTextUtils.setSentence(sentence);

				// part of speech tag sentence
				List<String> posTags = mTextUtils.getPosTags();

				// chunk sentence
				List<String> chunkTags = mTextUtils.getChunkTags();

				// tag named entities
				List<String> neTags = mTextUtils.getNETags();

				// parse sentence
				Map<Token, Arc> tokenToHeadArc = mTextUtils.getTratzParseTree();

				// get lemmas
				List<String> lemmas = mTextUtils.getLemmas(sentence);

				// get a new parsed sentence from the token factory
				SentenceWritable<TratzParsedTokenWritable> parsedSentence = 
						new SentenceWritable<TratzParsedTokenWritable>(TOKEN_FACTORY);

				// generate parsed tokens
				int sentencePos = 0;
				for(Token t : mTextUtils.getSentenceTokens()) {
					Arc headArc = tokenToHeadArc.get(t);

					// get a new parsed token
					TratzParsedTokenWritable token = new TratzParsedTokenWritable();

					// set the attributes of the parsed token
					/*token.setToken(t.getText());
					token.setCharOffset(sentence.get(sentencePos).beginPosition(), sentence.get(sentencePos).endPosition()-1);
					token.setLemma(lemmas.get(sentencePos));
					token.setPosTag(posTags.get(sentencePos)); 
					token.setChunkTag(chunkTags.get(sentencePos));
					token.setNETag(neTags.get(sentencePos));*/

					// dependency parse information
					if(headArc == null) {
						token.setDependType("root");
						token.setDependIndex(0);
					}
					else {
						token.setDependType(headArc.getDependency());
						token.setDependIndex(headArc.getHead().getIndex());
					}

					// if this token has disambiguation information, then append it to the POS tag
					if(headArc != null && headArc.getChild().getLexSense() != null) {
						//token.setPosTag(t.getPos() + "-" + headArc.getChild().getLexSense());
					}

					// add token to sentence
					parsedSentence.addToken(token);

					// increment position within sentence
					sentencePos++;
				}

				// add sentence to document
				mDoc.addSentence(parsedSentence);

			}
			catch(Exception e) {
				sLogger.info("Error parsing sentence: " + e);
			}
		
		}
	
	}

}
