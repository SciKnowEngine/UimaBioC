package edu.isi.bmkeg.uimaBioC.uima.ae.core;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.FSArray;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.resource.ResourceInitializationException;
import org.cleartk.token.type.Sentence;
import org.cleartk.token.type.Token;
import org.uimafit.component.JCasAnnotator_ImplBase;
import org.uimafit.descriptor.ConfigurationParameter;
import org.uimafit.factory.ConfigurationParameterFactory;
import org.uimafit.util.JCasUtil;

import bioc.BioCCollection;
import bioc.type.UimaBioCAnnotation;
import bioc.type.UimaBioCDocument;
import bioc.type.UimaBioCLocation;
import edu.isi.bmkeg.uimaBioC.UimaBioCUtils;

public class AddFeaturesToClauses extends JCasAnnotator_ImplBase {

	private static Logger logger = Logger.getLogger(AddFeaturesToClauses.class);

	private BioCCollection collection;

	private List<Pattern> figPatt = new ArrayList<Pattern>();
	private List<Pattern> figsPatt = new ArrayList<Pattern>();

	Map<String, Map<String, Integer>> table = new HashMap<String, Map<String, Integer>>();

	public void initialize(UimaContext context) throws ResourceInitializationException {
		super.initialize(context);

		this.collection = new BioCCollection();

		//
		// A List of regular expressions to recognize
		// all figure legend codes appearing in text.
		// Include 'S' to denote supplemental figures 
		// included as well
		//
		String b = "\\s*[Ff]ig(ure|.){0,1}";
		String e = "\\s*(\\)|\\w{2,}|\\p{Punct})";
		//
		// 0. No alphanumeric codes at all
		this.figPatt.add(Pattern.compile(b + "\\s*(\\d+)\\s*\\p{Punct}*\\s*\\w{2,}"));
		//
		// 1. Delineated by brackets
		this.figPatt.add(Pattern.compile("\\(" + b + "\\s*(S{0,1}\\d+.*?)\\)"));
		
		//
		// 2. Simple single alphanumeric codes, followed by punctuation.
		this.figPatt.add(Pattern.compile(b + "\\s*(S{0,1}\\d+\\s*[A-Za-z])\\p{Punct}"));

		//
		// 3. Single Alphanumeric codes, followed by words.
		this.figPatt.add(Pattern.compile(b + "\\s*(S{0,1}\\d+\\s*[A-Za-z])\\s+\\w{2,}"));

		//
		// 4. Fig 8, a and b).
		this.figPatt.add(Pattern.compile(b + "\\s*(S{0,1}\\d[\\s\\p{Punct}]*[A-Za-z] and [A-Za-z])" + e));

		//
		// 5. Fig. 3, a-c).
		this.figPatt.add(Pattern.compile(b + "\\s*(S{0,1}\\d[\\s\\p{Punct}]*[A-Z\\-a-z])" + e));

		//
		// 6. Fig. c).
		this.figPatt.add(Pattern.compile(b + "\\s*([\\s\\p{Punct}]*[A-Z\\-a-z])" + e));

		// Multiple Figures in sequence
		b = "\\s*[Ff]ig(ures|s.{0,1}|.){0,1}";
		// 1. Fig. c).
		this.figsPatt.add(Pattern.compile(b + "\\s*([\\s\\p{Punct}]*[A-Z\\-a-z])" + e));
	}

	public void process(JCas jCas) throws AnalysisEngineProcessException {

		UimaBioCDocument uiD = JCasUtil.selectSingle(jCas, UimaBioCDocument.class);
		if (uiD.getId().equals("skip"))
			return;

		String id = uiD.getId();

		List<UimaBioCAnnotation> outerAnnotations = JCasUtil.selectCovered(UimaBioCAnnotation.class, uiD);

		this.buildDataStructures(jCas, uiD.getBegin(), uiD.getEnd());

		
	}

	private void buildDataStructures(JCas jCas, int start, int end)
			throws AnalysisEngineProcessException {

		List<UimaBioCAnnotation> floats = new ArrayList<UimaBioCAnnotation>();
		List<UimaBioCAnnotation> parags = new ArrayList<UimaBioCAnnotation>();
		for (UimaBioCAnnotation a : JCasUtil.selectCovered(jCas, UimaBioCAnnotation.class, start, end)) {
			Map<String, String> infons = UimaBioCUtils.convertInfons(a.getInfons());
			if (infons.containsKey("position") && infons.get("position").equals("float")) {
				floats.add(a);
			} else if (infons.containsKey("value")
					&& (infons.get("value").equals("p") || 
							infons.get("value").equals("title") || 
							infons.get("value").equals("label") || 
							infons.get("value").equals("article-t"))) {
				parags.add(a);
			}

		}
		
		int sNumber = 0;
		List<Sentence> sentences = JCasUtil.selectCovered(jCas, Sentence.class, start, end);
		SENTENCE_LOOP: for (Sentence s : sentences) {

			//
			// Identify exLinks, inLinks or headers
			//
			Map<Integer, Integer> currLvl = new HashMap<Integer, Integer>();

			Set<String> codes = new HashSet<String>();
			
			//
			// Look for section headings
			//
			for (UimaBioCAnnotation a : JCasUtil.selectCovering(jCas, UimaBioCAnnotation.class, s.getBegin(),
					s.getEnd())) {
				Map<String, String> infons = UimaBioCUtils.convertInfons(a.getInfons());
				if (infons.get("value").equals("title")) {
					UimaBioCAnnotation secAnn = this.readSectionHeading(jCas, a);
					if (secAnn == null) {
						UimaBioCDocument uiD = JCasUtil.selectSingle(jCas, UimaBioCDocument.class);
						System.err.println(uiD.getId() + " has title sentence '" + s.getCoveredText()
								+ "' that we cannot assign to a section");
						codes.add("header-?");
						continue;
					}

					int level = 99;
					try {
						level = readHeadingLevel(jCas, secAnn, 0);
					} catch (StackOverflowError e) {
						e.printStackTrace();
					}

					if (currLvl.containsKey(level)) {
						currLvl.put(level, currLvl.get(level) + 1);
						int tempLevel = level;
						while (currLvl.containsKey(tempLevel + 1)) {
							tempLevel++;
							currLvl.remove(tempLevel);
						}
					} else {
						currLvl.put(level, 1);
					}
					codes.add("header-" + level);
					
				}

			}

			//
			// Look for paragraphs
			//
			String pCode = "-";
			for (int i = 0; i < parags.size(); i++) {
				UimaBioCAnnotation p = parags.get(i);
				if (s.getBegin() >= p.getBegin() && s.getEnd() <= p.getEnd()) {
					UimaBioCUtils.convertInfons(p.getInfons()).get("value");
					pCode = UimaBioCUtils.convertInfons(p.getInfons()).get("value") + i;
					break;
				}
			}

			sNumber++;

			List<UimaBioCAnnotation> clauseList = new ArrayList<UimaBioCAnnotation>();
			for (UimaBioCAnnotation a : JCasUtil.selectCovered(jCas, UimaBioCAnnotation.class, s)) {
				Map<String, String> infons = UimaBioCUtils.convertInfons(a.getInfons());
				if (infons.get("type").equals("rubicon") && infons.get("value").equals("clause"))
					clauseList.add(a);
			}
			
			if(clauseList.size() == 0) {
				UimaBioCDocument uiD = JCasUtil.selectSingle(jCas, UimaBioCDocument.class);
				logger.warn("No Clauses Found in "+uiD.getId()+"("+s.getBegin() +"-"+s.getEnd()+"): " + s.getCoveredText() );
				UimaBioCAnnotation newCl = this.createClauseAnnotation(jCas, s.getBegin(), s.getEnd()); 
				clauseList.add(newCl);
				newCl.addToIndexes();
			}

			for (UimaBioCAnnotation clause : clauseList) {
				
				Set<String> expts = extractExptsFromClause(jCas, clause);
				Set<String> newCodes = extractCodesFromClause(jCas, clause);
				newCodes.addAll(codes);
				String headingString = this.readHeadingString(jCas, clause, "");
				
				Map<String,String> infons = UimaBioCUtils.convertInfons(clause.getInfons());
				infons.put("scidp-heading-string", headingString);
				infons.put("scidp-inExHeading-string", newCodes.toString());
				infons.put("scidp-experiment-labels", expts.toString());
				infons.put("scidp-paragraph-number", pCode);
				infons.put("scidp-sentence-number", "s" + sNumber);
				
				clause.setInfons(UimaBioCUtils.convertInfons(infons, jCas));
				
			}

		}
		
		// Checking features for every clause annotation.
		
		

	}
	
	private Set<String> extractExptsFromClause(JCas jCas, Annotation clause) {
		Set<String> expts = new HashSet<String>();
		for (UimaBioCAnnotation a : JCasUtil.selectCovered(UimaBioCAnnotation.class, clause)) {
			Map<String, String> infons = UimaBioCUtils.convertInfons(a.getInfons());
			if( infons.containsKey("refType") ) {
				if( infons.get("refType").equals("fig") || 
						infons.get("refType").equals("supplementary-material")) {
					String exptCodes = readExptCodes(jCas, a);
					expts.add(exptCodes);
				}
			} 
		}
		return expts;
	}
	
	private Set<String> extractCodesFromClause(JCas jCas, Annotation clause) {
		Set<String> codes = new HashSet<String>();
		for (UimaBioCAnnotation a : JCasUtil.selectCovered(UimaBioCAnnotation.class, clause)) {
			Map<String, String> infons = UimaBioCUtils.convertInfons(a.getInfons());
			if (infons.containsKey("refType") && infons.get("refType").equals("bibr")) {
				codes.add("exLink");
			} else if (infons.containsKey("refType") && infons.get("refType").equals("fig")) {
				codes.add("inLink");
			} else if (infons.containsKey("refType") && infons.get("refType").equals("supplementary-material")) {
				codes.add("inLink");
			} else if (infons.get("value").equals("label")) {
				codes.add("label");
			}
		}
		return codes;
	}

	private String readExptCodes(JCas jCas, UimaBioCAnnotation s) {

		String exptCode = s.getCoveredText();
		int offset = 2;
		if (exptCode.toLowerCase().startsWith("fig")) {
			offset = 1;
		}

		List<Token> l = JCasUtil.selectCovered(Token.class, s);
		List<Token> f = JCasUtil.selectFollowing(jCas, Token.class, s, 10);
		List<Token> p = JCasUtil.selectPreceding(jCas, Token.class, s, offset);

		Token start = p.get(0);
		Token end = f.get(9);
		String figFrag = jCas.getDocumentText().substring(start.getBegin(), end.getEnd());

		for (Pattern patt : this.figPatt) {
			Matcher m = patt.matcher(figFrag);
			if (m.find()) {
				
				// use group 2 since all
				exptCode = m.group(2).replaceAll("\\n", "");
				return exptCode;
				
			}
		}

		return exptCode;
	}

	private UimaBioCAnnotation readSectionHeading(JCas jCas, UimaBioCAnnotation a) {

		// Looking for section headings
		for (UimaBioCAnnotation a1 : JCasUtil.selectCovering(jCas, UimaBioCAnnotation.class, a.getBegin(),
				a.getEnd())) {
			Map<String, String> infons = UimaBioCUtils.convertInfons(a1.getInfons());
			if (infons.containsKey("sectionHeading") && a1.getBegin() == a.getBegin()) {
				return a1;
			}
		}

		return null;

	}

	private int readHeadingLevel(JCas jCas, UimaBioCAnnotation a, int level) throws StackOverflowError {

		// Looking for section headings
		for (UimaBioCAnnotation a1 : JCasUtil.selectCovering(jCas, UimaBioCAnnotation.class, a.getBegin(),
				a.getEnd())) {
			if (a1.equals(a))
				continue;
			Map<String, String> infons = UimaBioCUtils.convertInfons(a1.getInfons());
			if (infons.containsKey("sectionHeading")) {
				level = readHeadingLevel(jCas, a1, level + 1);
				return level;
			}
		}

		return level;

	}
	
	private String readHeadingString(JCas jCas, UimaBioCAnnotation a, String heading) throws StackOverflowError {

		// Looking for section headings
		for (UimaBioCAnnotation a1 : JCasUtil.selectCovering(jCas, UimaBioCAnnotation.class, a.getBegin(),
				a.getEnd())) {
			if (a1.equals(a))
				continue;
			Map<String, String> infons = UimaBioCUtils.convertInfons(a1.getInfons());
			if (infons.containsKey("sectionHeading")) {
				if( !heading.equals("") )
					heading += "|";
				return heading + infons.get("sectionHeading");
			}
		}

		return heading;

	}

	private String readHeadingString(JCas jCas, Sentence a, String heading) throws StackOverflowError {

		// Looking for section headings
		for (UimaBioCAnnotation a1 : JCasUtil.selectCovering(jCas, UimaBioCAnnotation.class, a.getBegin(),
				a.getEnd())) {
			if (a1.equals(a))
				continue;
			Map<String, String> infons = UimaBioCUtils.convertInfons(a1.getInfons());
			if (infons.containsKey("sectionHeading")) {
				if( !heading.equals("") )
					heading += "|";
				return heading + infons.get("sectionHeading");
			}
		}

		return heading;

	}
	
	
	private String readTokenizedText(JCas jCas, Sentence s) {
		String txt = "";
		for (Token t : JCasUtil.selectCovered(jCas, Token.class, s)) {
			txt += t.getCoveredText() + " ";
		}
		if (txt.length() == 0)
			return txt;
		return txt.substring(0, txt.length() - 1);
	}

	private String readTokenizedText(JCas jCas, UimaBioCAnnotation a) {
		String txt = "";
		for (Token t : JCasUtil.selectCovered(jCas, Token.class, a)) {
			txt += t.getCoveredText() + " ";
		}
		if (txt.length() == 0)
			return txt;
		return txt.substring(0, txt.length() - 1);
	}
	
	private UimaBioCAnnotation createClauseAnnotation(JCas jCas, int begin, int end) {

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
		infons.put("type", "rubicon");
		infons.put("value", "clause");

		uiA.setInfons(UimaBioCUtils.convertInfons(infons, jCas));

		return uiA;

	}

}
