package edu.isi.bmkeg.uimaBioC.rubicon;

import java.io.File;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.io.FileUtils;
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
import edu.stanford.nlp.io.StringOutputStream;
import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.trees.PennTreeReader;
import edu.stanford.nlp.trees.Tree;

public class SeparateClauses extends JCasAnnotator_ImplBase {

	private LexicalizedParser lp;

	private String[] args;

	private PrintStream nullStream;

	@Override
	public void initialize(UimaContext context) throws ResourceInitializationException {

		super.initialize(context);

		Properties properties = new Properties();
		properties.put("annotators", "tokenize, ssplit, pos, lemma, ner, parse, dcoref");

		String[] args = { "-model", "edu/stanford/nlp/models/lexparser/englishPCFG.ser.gz", 
				"-sentences", "newline",
				"-outputFormat", "penn", 
				"-tokenized", 
				"-escaper", "edu.stanford.nlp.process.PTBEscapingProcessor",
				"-" };
		this.args = args;
		
		nullStream = (new PrintStream(new OutputStream() {
		    public void write(int b) {
		    }
		}));

	}

	/**
	 * Ported from Pradeep Dasagi's Python Function:
	 * 
	 *  def separate_clauses(lines):
	 *    parse_iters = parser.parse_sents(lines)
	 *    clauses = []
	 *    for words, parse_iter in zip(lines, parse_iters):
	 *      sent = " ".join(words)
	 *      sat_clause = extract_sat_clause(parse_iter.next())
	 *      ind = sent.index(sat_clause)
	 *      sat_len = len(sat_clause)
	 *      if ind == 0:
	 *        main_clause = sent[sat_len:]
	 *        clause_set = (sat_clause, main_clause)
	 *      else:
	 *        main_clause = sent[:ind]
	 *        if ind + sat_len != len(sent):
	 *          # There is a remainder at the end
	 *          remainder = sent[ind + sat_len :]
	 *          if remainder.strip() == ".":
	 *            sat_clause = sat_clause + " ."
	 *            clause_set = (main_clause, sat_clause)
	 *          else:
	 *            clause_set = (main_clause, sat_clause, remainder)
	 *        else:
	 *          clause_set = (main_clause, sat_clause) 
	 *      clauses.append(clause_set)
	 *    return clauses
	 */
	@Override
	public void process(JCas jCas) throws AnalysisEngineProcessException {

		try {

			UimaBioCDocument uiD = JCasUtil.selectSingle(jCas, UimaBioCDocument.class);
			UimaBioCPassage docP = UimaBioCUtils.readDocumentUimaBioCPassage(jCas);

			List<Sentence> sentences = JCasUtil.selectCovered(Sentence.class, docP);
			int sCount = 0;
			for (Sentence s : sentences) {

				File tmpIn = File.createTempFile("snt_" + uiD.getId() + "." + sCount + ".", "_in.txt");
				tmpIn.deleteOnExit();
				
				Map<Integer, Token> tokPos = new HashMap<Integer, Token>();
				String ss = "";
				for(Token t : JCasUtil.selectCovered(Token.class, s) )  {
					if( ss.length() > 0 ) ss += " ";
					tokPos.put(ss.length(), t);
					ss += t.getCoveredText();
					tokPos.put(ss.length(), t);
				}
				FileUtils.writeStringToFile(tmpIn, ss);
				
				PrintStream sysout = System.out;
				PrintStream syserr = System.err;
				StringOutputStream outputStream = new StringOutputStream();
				PrintStream out = new PrintStream(outputStream);
				
				System.setOut(out);
				System.setErr(nullStream);
				
				args[args.length - 1] = tmpIn.getPath();
				LexicalizedParser.main(args);
				out.flush();
				
				System.setOut(sysout);
				System.setErr(syserr);
				
				PennTreeReader ptr = new PennTreeReader(new StringReader(outputStream.toString()));
				Tree tree = ptr.readTree();
				ptr.close();
				
				String satClauseStr = treeToString(extractSatClause(tree));
				
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
				}
				
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

	/**
	 * 	Ported From Pradeep Dasagi's Python Code.
	 *	
	 * def extract_sat_clause(tree, is_root = True):
	 *   if len(tree) == 0 or type(tree) == unicode:
	 *      return ""
	 *   elif tree.label() == 'S' or tree.label() == 'SBAR':
	 *      if is_root:
	 *        return get_longest_cand([extract_sat_clause(t, is_root = False) for t in tree])
	 *      else:
	 *        phrase = " ".join(tree.leaves())
	 *        return phrase.replace("-LRB-", "(").replace("-RRB-", ")").replace("-LSB-", "[").replace("-RSB-", "]")
	 *   else:
	 *      return get_longest_cand([extract_sat_clause(t, is_root) for t in tree])
     */
	private Tree extractSatClause(Tree tree) {
		return extractSatClause(tree, true);
	}
	
	private Tree extractSatClause(Tree tree, Boolean isRoot) {
		if( tree.toString().length() == 0 ) {
			return null;
		} else if(tree.label().toString().equals("S") || 
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
		} else {
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
