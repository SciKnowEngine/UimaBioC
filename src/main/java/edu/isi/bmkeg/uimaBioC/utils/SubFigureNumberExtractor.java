package edu.isi.bmkeg.uimaBioC.utils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.uima.UimaContext;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.resource.ResourceInitializationException;
import org.cleartk.token.type.Token;
import org.uimafit.util.JCasUtil;

import bioc.BioCCollection;
import bioc.type.UimaBioCAnnotation;
import edu.isi.bmkeg.uimaBioC.UimaBioCUtils;

public class SubFigureNumberExtractor {

	private List<Pattern> figPatt = new ArrayList<Pattern>();
	private List<Pattern> figsPatt = new ArrayList<Pattern>();

	private Pattern commaAndPatt = Pattern.compile(",{0,1} and ");
	
	private Pattern figNumberPatt = Pattern.compile("(\\d+)");
	
	private Pattern simplePatt = Pattern.compile("\\d+$");
	private Pattern simpleAndPatt = Pattern.compile("(\\d+)\\s*[;,]\\s*(\\d+)[\\s,]*$");
	private Pattern simpleSubPatt = Pattern.compile("\\d+[\\s,]{0,2}([A-Za-z])");
	private Pattern intPatt = Pattern.compile("\\d+[\\s,]{0,2}([A-Za-z]+)\\-([A-Za-z]+)");
	private Pattern comma2Patt = Pattern.compile("\\d+[\\s,]{0,2}([A-Za-z]+)[;,]\\s{0,1}([A-Za-z]+)");
	private Pattern comma3Patt = Pattern.compile("\\d+[\\s,]{0,2}([A-Za-z]+)[;,]\\s{0,1}([A-Za-z]+)[;,]\\s{0,1}([A-Za-z]+)");
	private Pattern comma4Patt = Pattern.compile("\\d+[\\s,]{0,2}([A-Za-z]+)[;,]\\s{0,1}([A-Za-z]+)[;,]\\s{0,1}([A-Za-z]+)[;,]\\s{0,1}([A-Za-z]+)");
	private Pattern comma5Patt = Pattern.compile("\\d+[\\s,]{0,2}([A-Za-z]+)[;,]\\s{0,1}([A-Za-z]+)[;,]\\s{0,1}([A-Za-z]+)[;,]\\s{0,1}([A-Za-z]+)[;,]\\s{0,1}([A-Za-z]+)");
	private Pattern suppM = Pattern.compile("([Ss]){1,1}\\d+");
	
    public SubFigureNumberExtractor() throws Exception {

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
		// 1a. Figs. 1 and 2).
		this.figPatt.add(Pattern.compile("\\s*[Ff]ig(ures|s|s.){0,1}\\s*(\\d+[\\s\\p{Punct}]* and \\d+[\\s\\p{Punct}]*)" + e));

		//
		// 2. Simple single alphanumeric codes, followed by punctuation.
		this.figPatt.add(Pattern.compile(b + "\\s*(S{0,1}\\d+\\s*[A-Za-z])\\p{Punct}"));

		//
		// 3. Single Alphanumeric codes, followed by words.
		this.figPatt.add(Pattern.compile(b + "\\s*(S{0,1}\\d+\\s*[A-Za-z])\\s+\\w{2,}"));

		//
		// 4. Fig 8, a and b).
		this.figPatt.add(Pattern.compile(b + "\\s*(S{0,1}\\d+[\\s\\p{Punct}]*[A-Za-z] and [A-Za-z])" + e));

		//
		// 5. Fig. 3, a-c).
		this.figPatt.add(Pattern.compile(b + "\\s*(S{0,1}\\d+[\\s\\p{Punct}]*[A-Z\\-a-z])" + e));

		//
		// 6. Fig. c).
		this.figPatt.add(Pattern.compile(b + "\\s*([\\s\\p{Punct}]*[A-Z\\-a-z])" + e));
		
		// Multiple Figures in sequence
		b = "\\s*[Ff]ig(ures|s.{0,1}|.){0,1}";
		// 1. Fig. c).
		this.figsPatt.add(Pattern.compile(b + "\\s*([\\s\\p{Punct}]*[A-Z\\-a-z])" + e));
	
	}
	
	public String readExptCodes(JCas jCas, UimaBioCAnnotation s) {

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
		int newlineCheck = figFrag.indexOf("\n");
		if( newlineCheck != -1 )
			figFrag = figFrag.substring(0, newlineCheck-1);
		int i=0;
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
	
	public Set<String> cleanUpFigureReference(String f) {
		
		Set<String> codes = new HashSet<String>();
		
		f = f.replaceAll(",{0,1} and ", ",");
		
		String fig = "";
		Matcher m = figNumberPatt.matcher(f); 
		if( m.find() ) {
			fig = m.group(1);
		}
		
		Matcher intM = intPatt.matcher(f);
		Matcher comma5M = comma5Patt.matcher(f); 
		Matcher comma4M = comma4Patt.matcher(f); 
		Matcher comma3M = comma3Patt.matcher(f); 
		Matcher comma2M = comma2Patt.matcher(f); 
		Matcher simpleAndM = simpleAndPatt.matcher(f);
		Matcher simpleSubM = simpleSubPatt.matcher(f); 
		
		if( intM.find() ) {
			char start = intM.group(1).charAt(0);
			char end = intM.group(2).charAt(0);
			for(char alphabet = start; alphabet <= end; alphabet++) {
				String a = (alphabet + "").toLowerCase();
			    codes.add( fig + a);
			}			
		} 
		else if(comma5M.find()) {
			if( comma5M.group(1).length() == 1)
				codes.add(fig + comma5M.group(1).toLowerCase());
			if( comma5M.group(2).length() == 1)
				codes.add(fig + comma5M.group(2).toLowerCase());
			if( comma5M.group(3).length() == 1)
				codes.add(fig + comma5M.group(3).toLowerCase());
			if( comma5M.group(4).length() == 1)
				codes.add(fig + comma5M.group(4).toLowerCase());
			if( comma5M.group(5).length() == 1)
				codes.add(fig + comma5M.group(5).toLowerCase());
		}
		else if(comma4M.find()) {
			if( comma4M.group(1).length() == 1)
				codes.add(fig + comma4M.group(1).toLowerCase());
			if( comma4M.group(2).length() == 1)
				codes.add(fig + comma4M.group(2).toLowerCase());
			if( comma4M.group(3).length() == 1)
				codes.add(fig + comma4M.group(3).toLowerCase());
			if( comma4M.group(4).length() == 1)
				codes.add(fig + comma4M.group(4).toLowerCase());
		}
		else if(comma3M.find()) {
			if( comma3M.group(1).length() == 1)
				codes.add(fig + comma3M.group(1).toLowerCase());
			if( comma3M.group(2).length() == 1)
				codes.add(fig + comma3M.group(2).toLowerCase());
			if( comma3M.group(3).length() == 1)
				codes.add(fig + comma3M.group(3).toLowerCase());
		}
		else if(comma2M.find()) {
			if( comma2M.group(1).length() == 1)
				codes.add(fig + comma2M.group(1).toLowerCase());
			if( comma2M.group(2).length() == 1)
				codes.add(fig + comma2M.group(2).toLowerCase());
		}		
		else if(simpleAndM.find()) {
			codes.add(simpleAndM.group(1).toLowerCase());
			codes.add(simpleAndM.group(2).toLowerCase());
		}
		else if(simplePatt.matcher(f).find()) {
			codes.add(fig);
		} 
		else if(simpleSubM.find()) {
			codes.add(fig + simpleSubM.group(1).toLowerCase());
		}
		
		return codes;
		
	}
	
	public Set<String> extractExptsFromClause(JCas jCas, Annotation clause) {
		Set<String> expts = new HashSet<String>();
		for (UimaBioCAnnotation a : JCasUtil.selectCovered(UimaBioCAnnotation.class, clause)) {
			Map<String, String> infons = UimaBioCUtils.convertInfons(a.getInfons());
			if( infons.containsKey("refType") ) {
				if( infons.get("refType").equals("fig") || 
						infons.get("refType").equals("supplementary-material")) {
					String exptCodes = readExptCodes(jCas, a);
					if( exptCodes.length() > 0 ) { 
						Set<String> cleanedUpCodes = cleanUpFigureReference(exptCodes);
						expts.addAll(cleanedUpCodes);
					}
					System.err.println(expts.toString() + "\t" + a.getCoveredText() + "\t" + clause.getCoveredText());
				}
			} 
		}
		return expts;
	}
		
}
