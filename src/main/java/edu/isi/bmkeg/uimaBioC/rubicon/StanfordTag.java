package edu.isi.bmkeg.uimaBioC.rubicon;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

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
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.parser.lexparser.Options;
import edu.stanford.nlp.parser.lexparser.ParseFiles;
import edu.stanford.nlp.parser.lexparser.ParserQuery;
import edu.stanford.nlp.process.DocumentPreprocessor;
import edu.stanford.nlp.process.PTBEscapingProcessor;
import edu.stanford.nlp.tagger.maxent.MaxentTagger;
import edu.stanford.nlp.trees.PennTreeReader;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreebankLanguagePack;
import edu.stanford.nlp.util.ReflectionLoading;

public class StanfordTag extends JCasAnnotator_ImplBase {

	private String[] args;

	private PrintStream nullStream;

	private MaxentTagger tagger;
	public PTBEscapingProcessor escaper;
	public Options op;
	private ParseFiles pf;
	private TreebankLanguagePack tlp;
	
	private int numProcessed = 0;
	
	@Override
	public void initialize(UimaContext context) throws ResourceInitializationException {

		super.initialize(context);
			    
        tagger = new MaxentTagger("edu/stanford/nlp/models/pos-tagger/english-bidirectional/english-bidirectional-distsim.tagger");

		nullStream = (new PrintStream(new OutputStream() {
		    public void write(int b) {
		    }
		}));

	}

	@Override
	public void process(JCas jCas) throws AnalysisEngineProcessException {

		try {
			
			UimaBioCDocument uiD = JCasUtil.selectSingle(jCas, UimaBioCDocument.class);
			UimaBioCPassage docP = UimaBioCUtils.readDocumentUimaBioCPassage(jCas);

			List<Sentence> sentences = JCasUtil.selectCovered(Sentence.class, docP);
			int sCount = 0;
			for (Sentence s : sentences) {

				Map<Integer, Token> tokPos = new HashMap<Integer, Token>();
				String ss = "";
				for(Token t : JCasUtil.selectCovered(Token.class, s) )  {
					if( ss.length() > 0 ) ss += " ";
					tokPos.put(ss.length(), t);
					ss += t.getCoveredText();
					tokPos.put(ss.length(), t);
				}
				
				// The tagged string
		        String p = tagger.tagString(ss);
		       
		        String pauseHere = "";
		        pauseHere += "no, pause HERE";
		        
				/* NEED TO DO SOMETHING HERE WITH THE TREE. 
				 * 
				 * String satClauseStr = treeToString(extractSatClause(tree));
				
				int ind = ss.indexOf(satClauseStr);
				if( ind == -1 ) {
					throw new Exception("Mismatch in clause splitting");
				}

				if( satClauseStr.length() > 0 ) {
					Token startTok = tokPos.get(ind);
					Token endTok = tokPos.get(ind+satClauseStr.length());
					
					UimaBioCAnnotation satClause = this.createClauseAnnotation(
							jCas, startTok, endTok);
					satClause.addToIndexes();
				}*/
				
				//
				// Note, we only need to annotate the satClause within a sentence
				// since we can easily infer the other clauses when they exist. 
				//
				
				sCount++;
				
			}

		} catch (Exception e) {

			throw new AnalysisEngineProcessException(e);
		
		}

	}

	private Tree extractSatClause(Tree tree) {
		return extractSatClause(tree, true);
	}
	
	private Tree extractSatClause(Tree tree, Boolean isRoot) {
		
		if( tree.toString().length() == 0 ) {
			return null;
		} 
		else if(tree.label().toString().equals("S") || 
				tree.label().toString().equals("SBAR")) {
			if( isRoot ) {
				List<Tree> cands = new ArrayList<Tree>();
				for( Tree t : tree.getChildrenAsList() ) {
					cands.add(extractSatClause(t, false));
				}
				Tree l = this.getLongestCand(cands);
				return l;
			} else {
				return tree;
			}
		} 
		else {
			List<Tree> cands = new ArrayList<Tree>();
			for( Tree t : tree.getChildrenAsList() ) {
				cands.add( extractSatClause(t, isRoot) );
			}
			Tree l = this.getLongestCand(cands);
			return l;
		}
		
	}
	
	/** 
	 * Ported From Pradeep Dasagi's Python Code.
	 */	
	private Tree getLongestCand(List<Tree> cands) {
		int maxlen = 0;
		Tree bestcand = null;
		for(Tree cand : cands ) {
		    if( cand != null && treeToString(cand).length() > maxlen ) {
			      maxlen = treeToString(cand).length();
			      bestcand = cand;
		    }			
		}
		return bestcand;
	}
	
	private String treeToString(Tree tree) {

		if( tree == null )
			return "";
		
		String phrase = "";
	
		for(Tree leaf : tree.getLeaves() ) {
			phrase += leaf.toString() + " ";
		}
		phrase = phrase.substring(0, phrase.length()-1).replaceAll("-LRB-", "(");
		phrase = phrase.replaceAll("-RRB-", ")");
		phrase = phrase.replaceAll("-LSB-", "[");
		phrase = phrase.replaceAll("-RSB-", "]");
		phrase = phrase.replaceAll("\\\\", "");

		return phrase;
	
	}
	
	private UimaBioCAnnotation createClauseAnnotation(JCas jCas, Token start, Token end) {

		UimaBioCAnnotation uiA = new UimaBioCAnnotation(jCas);
		
		FSArray locations = new FSArray(jCas, 1);
		uiA.setLocations(locations);
		UimaBioCLocation uiL = new UimaBioCLocation(jCas);
		uiL.setOffset(start.getBegin());
		uiL.setLength(end.getEnd() - start.getBegin());
		locations.set(0, uiL);

		uiA.setBegin(uiL.getOffset());
		uiA.setEnd(uiL.getOffset() + uiL.getLength());

		uiL.setBegin(uiL.getOffset());
		uiL.setEnd(uiL.getOffset() + uiL.getLength());

		Map<String, String> infons = new HashMap<String, String>();
		infons.put("type", "rubicon");
		infons.put("value", "clause");
		
		return uiA;
	
	}

	

}