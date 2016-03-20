package edu.isi.bmkeg.uimaBioC.uima.ae;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.log4j.Logger;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.FSArray;
import org.apache.uima.resource.ResourceInitializationException;
import org.cleartk.token.type.Sentence;
import org.cleartk.token.type.Token;
import org.uimafit.component.JCasAnnotator_ImplBase;
import org.uimafit.descriptor.ConfigurationParameter;
import org.uimafit.factory.ConfigurationParameterFactory;
import org.uimafit.util.JCasUtil;

import bioc.type.UimaBioCAnnotation;
import bioc.type.UimaBioCDocument;
import bioc.type.UimaBioCLocation;
import bioc.type.UimaBioCPassage;
import edu.isi.bmkeg.uimaBioC.UimaBioCUtils;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.parser.common.ParserQuery;
import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.parser.lexparser.Options;
import edu.stanford.nlp.parser.lexparser.ParseFiles;
import edu.stanford.nlp.process.DocumentPreprocessor;
import edu.stanford.nlp.process.PTBEscapingProcessor;
import edu.stanford.nlp.trees.TreebankLanguagePack;
import edu.stanford.nlp.util.ReflectionLoading;

public class StanfordParse extends JCasAnnotator_ImplBase {

	private static Logger logger = Logger.getLogger(SeparateClauses.class);

	public final static String PARAM_MAX_LENGTH = ConfigurationParameterFactory
			.createConfigurationParameterName(StanfordParse.class,
					"maxLength");
	@ConfigurationParameter(mandatory = true, description = "Maximum Sentence Length")
	int maxLength;
	
	private PrintStream nullStream;

	private LexicalizedParser lp;
	public PTBEscapingProcessor escaper;
	public Options op;
	private ParseFiles pf;
	private TreebankLanguagePack tlp;
	
	private int numProcessed = 0;
	
	@Override
	public void initialize(UimaContext context) throws ResourceInitializationException {

		super.initialize(context);

		Properties properties = new Properties();
		properties.put("annotators", "tokenize, ssplit, pos, lemma, ner, parse, dcoref");

		op = new Options();
	    
	    String[] extraArgs = {"-outputFormat", "penn"};
        escaper = ReflectionLoading.loadByReflection("edu.stanford.nlp.process.PTBEscapingProcessor");
		lp = LexicalizedParser.loadModel("edu/stanford/nlp/models/lexparser/englishPCFG.ser.gz", op, extraArgs);
		pf = new ParseFiles(op, lp.getTreePrint(), lp);

	}

	@Override
	public void process(JCas jCas) throws AnalysisEngineProcessException {

		try {
			
			UimaBioCDocument uiD = JCasUtil.selectSingle(jCas, UimaBioCDocument.class);
			if( uiD.getId().equals("skip") )
				return;
			
			UimaBioCPassage docP = UimaBioCUtils.readDocument(jCas);

			List<Sentence> sentences = JCasUtil.selectCovered(Sentence.class, docP);
			int sCount = 0;
			SENTENCE_LOOP: for (Sentence s : sentences) {
				
				if( s.getEnd() - s.getBegin() > this.maxLength ) {
					logger.warn("Sentence too long for " + uiD.getId() + ": '" + s.getCoveredText());
					continue SENTENCE_LOOP;
				}

				Map<Integer, Token> tokPos = new HashMap<Integer, Token>();
				String ss = "";
				for(Token t : JCasUtil.selectCovered(Token.class, s) )  {
					if( ss.length() > 0 ) ss += " ";
					tokPos.put(ss.length(), t);
					ss += t.getCoveredText();
					tokPos.put(ss.length(), t);
				}
								
				DocumentPreprocessor documentPreprocessor = new DocumentPreprocessor(new BufferedReader(new StringReader(ss)));
				documentPreprocessor.setSentenceFinalPuncWords(
						op.tlpParams.treebankLanguagePack().sentenceFinalPunctuationWords()
						);
			    documentPreprocessor.setEscaper(escaper);
			    documentPreprocessor.setSentenceDelimiter("\n");
			    documentPreprocessor.setTagDelimiter(null);
			    documentPreprocessor.setElementDelimiter(null);
			    documentPreprocessor.setTokenizerFactory(null);
			    
			    ByteArrayOutputStream baos = new ByteArrayOutputStream();
			    PrintWriter pwo = new PrintWriter(baos);
		        ParserQuery pq = lp.parserQuery();
		        for (List<HasWord> sentence : documentPreprocessor) {
		          int len = sentence.size();
		          pq.parseAndReport(sentence, null);
				  pf.processResults(pq, numProcessed++, pwo);
		        }
		        String p = baos.toString();
			    				
		        UimaBioCAnnotation uiA = createStanfordParseAnnotation(jCas, s.getBegin(), s.getEnd(), p);
		        uiA.addToIndexes();
		        			
				sCount++;
				
			}

		} catch (Exception e) {

			throw new AnalysisEngineProcessException(e);
		
		}

	}
	
	private UimaBioCAnnotation createStanfordParseAnnotation(JCas jCas, int begin, int end, String parse) {

		UimaBioCAnnotation uiA = new UimaBioCAnnotation(jCas);

		FSArray locations = new FSArray(jCas, 1);
		uiA.setLocations(locations);
		UimaBioCLocation uiL = new UimaBioCLocation(jCas);
		uiL.setOffset(begin);
		uiL.setLength(end - begin);
		locations.set(0, uiL);

		uiA.setBegin(uiL.getOffset());
		uiA.setEnd(uiL.getOffset() + uiL.getLength());

		uiL.setBegin(uiL.getOffset());
		uiL.setEnd(uiL.getOffset() + uiL.getLength());

		Map<String, String> infons = new HashMap<String, String>();
		infons.put("type", "stanford-parse");
		infons.put("value", parse);

		uiA.setInfons(UimaBioCUtils.convertInfons(infons, jCas));

		return uiA;

	}

	

}