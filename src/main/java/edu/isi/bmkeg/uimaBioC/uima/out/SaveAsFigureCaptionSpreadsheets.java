package edu.isi.bmkeg.uimaBioC.uima.out;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.cleartk.token.type.Sentence;
import org.uimafit.component.JCasAnnotator_ImplBase;
import org.uimafit.descriptor.ConfigurationParameter;
import org.uimafit.factory.ConfigurationParameterFactory;
import org.uimafit.util.JCasUtil;

import bioc.BioCCollection;
import bioc.type.UimaBioCAnnotation;
import bioc.type.UimaBioCDocument;
import edu.isi.bmkeg.uimaBioC.UimaBioCUtils;
import edu.isi.bmkeg.uimaBioC.utils.SubFigureNumberExtractor;

public class SaveAsFigureCaptionSpreadsheets extends JCasAnnotator_ImplBase {

	private static Logger logger = Logger.getLogger(SaveAsFigureCaptionSpreadsheets.class);

	public final static String PARAM_DIR_PATH = ConfigurationParameterFactory
			.createConfigurationParameterName(SaveAsFigureCaptionSpreadsheets.class, "outDirPath");
	@ConfigurationParameter(mandatory = true, description = "The path to the output directory.")
	String outDirPath;
	
	public final static String PARAM_PMC_FILE_NAMES = ConfigurationParameterFactory
			.createConfigurationParameterName(SaveAsFigureCaptionSpreadsheets.class, "pmcFileNamesStr");
	@ConfigurationParameter(mandatory = false, description = "Should we use PMC or PMID file names?")
	String pmcFileNamesStr;
	Boolean pmcFileNames = true;

	private File outDir;
	private BioCCollection collection;
	
	private Pattern figurePattern = Pattern.compile("^\\s*fig(.|ure)[\\s\\(]*(\\d+)");
	private Pattern justNumberFigurePattern = Pattern.compile("^\\s*(\\d+)");

	// (A)
	private Pattern subFigurePattern1 = Pattern.compile(
			"[\\(\\[]\\s+([A-Za-z])\\s+[\\)\\]]");
	// (A and B), (A & B), (A , B)
	private Pattern subFigurePattern2 = Pattern.compile(
			"[\\(\\[]\\s+([A-Za-z])\\s+(and|&|,)\\s+([A-Za-z])\\s+[\\)\\]]");
	// (A, B, and C), (A,B and C)
	private Pattern subFigurePattern3 = Pattern.compile(
			"[\\(\\[]\\s+([A-Za-z])\\s+,\\s+([A-Za-z])[\\s+,](and|&)\\s([A-Za-z])\\s+[\\)\\]]");
	// a, ...	
	private Pattern subFigurePattern4 = Pattern.compile("^\\s+([A-Za-z]) [,:\\)\\]]");	
	// (A - F)
	private Pattern subFigurePattern5 = Pattern.compile(
			"[\\(\\[]\\s+([A-Za-z])\\s+\\-\\s+([A-Za-z])\\s+[\\)\\]]");
	// (A , top), (A middle)
	private Pattern subFigurePattern6 = Pattern.compile(
			"[\\(\\[]\\s+([A-Za-z])[\\s\\,]*(bottom|middle|top|left|right|center)\\s+[\\)\\]]");

	private SubFigureNumberExtractor figExtractor;

	Map<String, Map<String, Integer>> table = new HashMap<String, Map<String, Integer>>();
	
	public void initialize(UimaContext context) throws ResourceInitializationException {

		super.initialize(context);

		this.outDirPath = (String) context.getConfigParameterValue(PARAM_DIR_PATH);
		this.outDir = new File(this.outDirPath);

		if (!this.outDir.exists())
			this.outDir.mkdirs();
		
		if (this.pmcFileNamesStr != null && this.pmcFileNamesStr.toLowerCase().equals("false")) {
			pmcFileNames = false;
		} else {
			pmcFileNames = true;
		}

		this.collection = new BioCCollection();
		
	}

	public void process(JCas jCas) throws AnalysisEngineProcessException {

		try {
			
			UimaBioCDocument uiD = JCasUtil.selectSingle(jCas, UimaBioCDocument.class);
			if (uiD.getId().equals("skip"))
				return;

			String id = uiD.getId();			
			
			Map<String, String> infons = UimaBioCUtils.convertInfons(uiD.getInfons());
			String pmcID = "PMC" + infons.get("pmc");
			if (infons.containsKey("pmcid"))
				pmcID = infons.get("pmcid");
			if (infons.containsKey("accession"))
				pmcID = infons.get("accession");

			Map<String, String> inf = UimaBioCUtils.convertInfons(uiD.getInfons());
			String relPath = inf.get("relative-source-path").replaceAll("\\.txt", "") + "_captions.tsv";
			File outFile = new File(outDirPath + "/" + relPath);
			if( !outFile.getParentFile().exists() ) {
				outFile.getParentFile().mkdirs();
			}

			String tsv = this.generateCaptionTsv(jCas, uiD.getBegin(), uiD.getEnd());
			if( tsv != null ) {
				PrintWriter out;
				try {
					out = new PrintWriter(new BufferedWriter(new FileWriter(outFile, true)));
				} catch (IOException e) {
					throw (new AnalysisEngineProcessException(e));
				}
				out.print(tsv);
				out.close();
			} else {
				logger.error("No Caption TSV generated for " + id);
			}

		} catch (Exception e) {
			
			throw new AnalysisEngineProcessException(e);
			
		}

	}

	private String generateCaptionTsv(JCas jCas, int start, int end) throws Exception {

		UimaBioCDocument uiD = JCasUtil.selectSingle(jCas, UimaBioCDocument.class);
		String id = uiD.getId();
		id += "";

		List<UimaBioCAnnotation> figs = new ArrayList<UimaBioCAnnotation>();
		for (UimaBioCAnnotation a : JCasUtil.selectCovered(jCas, UimaBioCAnnotation.class, start, end)) {
			Map<String, String> infons = UimaBioCUtils.convertInfons(a.getInfons());
			if (infons.containsKey("value") && infons.get("value").equals("fig")) {
				figs.add(a);
			}
		}

		// Column Headings
		String tsvText = "DocId";
		tsvText += "\t";
		tsvText += "Figure";
		tsvText += "\t";
		tsvText += "SubFigure";
		tsvText += "\t";
		tsvText += "Caption";
		tsvText += "\t";
		tsvText += "Offset_Begin";
		tsvText += "\t";
		tsvText += "Offset_End";
		tsvText += "\n";

		boolean dataPresent = false;
		
		CAPTION_LOOP: for (UimaBioCAnnotation p : figs) {
			
			List<Sentence> sList = JCasUtil.selectCovered(jCas, Sentence.class, p.getBegin(), p.getEnd());
			if( sList.size() == 0 )
				continue;
			// figure box only contains 1 sentence...
			else if( sList.size() == 1 ) {
				String s = UimaBioCUtils.readTokenizedText(jCas, sList.get(0)).toLowerCase();
				Matcher m1 = figurePattern.matcher(s);
				Matcher m2 = justNumberFigurePattern.matcher(s);
				if( m1.find() ) {
					tsvText += printOutCaption(jCas, p, m1.group(2));
					dataPresent = true;
				} else if(m2.find()) {
					tsvText += printOutCaption(jCas, p, m2.group(1));
					dataPresent = true;
				}
			}
			// figure box only contains 2 sentences...
			else {
				String s = UimaBioCUtils.readTokenizedText(jCas, sList.get(0)).toLowerCase();
				Matcher m1 = figurePattern.matcher(s);
				Matcher m2 = justNumberFigurePattern.matcher(s);
				if( m1.find() ) {
					tsvText += printOutCaption(jCas, p, m1.group(2));
					dataPresent = true;
				} else if(m2.find()) {
					tsvText += printOutCaption(jCas, p, m2.group(1));
					dataPresent = true;
				} else {
					s = UimaBioCUtils.readTokenizedText(jCas, sList.get(1)).toLowerCase();
					m1 = figurePattern.matcher(s);
					m2 = justNumberFigurePattern.matcher(s);
					if( m1.find() ) {
						tsvText += printOutCaption(jCas, p, m1.group(2));
						dataPresent = true;
					} else if(m2.find()) {
						tsvText += printOutCaption(jCas, p, m2.group(1));
						dataPresent = true;
					}
				}
			}
			//Matcher mm = doiFigurePattern.matcher(UimaBioCUtils.readTokenizedText(jCas, p));
			//if( mm.find() ) {
			//	tsvText += printOutCaption(jCas, p, mm.group(1));
			//	dataPresent = true;
			//}
		}
		
		if( !dataPresent )
			return null;
			
		return tsvText;
		
	}
	
	private String checkFigure(JCas jCas, UimaBioCAnnotation p, String s, Pattern patt, int groupNumber) throws StackOverflowError, Exception {
		String out = "";
		Matcher m = patt.matcher(s);
		if( m.find() ) {
			out += printOutCaption(jCas, p, m.group(groupNumber));
		} 
		return out;
	}

	private String printOutCaption(JCas jCas, UimaBioCAnnotation a, String fignumber)
			throws Exception, StackOverflowError {

		UimaBioCDocument uiD = JCasUtil.selectSingle(jCas, UimaBioCDocument.class);
		
		char[] alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();
		int codeCount = 0;
		int startPos = a.getBegin();
		String lastCode = "";
		String lastText = "";
		String tsvText = "";
			
		for (Sentence s : JCasUtil.selectCovered(jCas, Sentence.class, a.getBegin(), a.getEnd())) {
		
			String sentenceText = UimaBioCUtils.readTokenizedText(jCas, s);
			Set<String> subfigCodes = extractPossibleCodes(sentenceText);
			List<String> subfigCodeList = new ArrayList<String>(subfigCodes);
			Collections.sort(subfigCodeList);
			
			//
			// check for obvious errors - codes that are out of sync.  
			if(subfigCodes.size()>0) {	
				Set<String> toRemove = new HashSet<String>();
				for( int i=0; i<subfigCodes.size(); i++) {
					if(!subfigCodeList.get(i).equals(alphabet[codeCount+i]+"")) {
						String errorCode = subfigCodeList.get(i);			
						toRemove.add(errorCode);	 // remove all out-of-sequence hits.
					}
				}
				subfigCodes.removeAll(toRemove);
				subfigCodeList = new ArrayList<String>(subfigCodes);
				Collections.sort(subfigCodeList);
			}
			
			//
			// This sentence denotes a new subfigure, so print out the last subfigure notation.
			//
			if(subfigCodes.size()>0 && lastText.length()>0) {	
				tsvText += uiD.getId()+"\t";		// 1 - document id
				tsvText += fignumber+"\t"; 		// 2 - figure number
				tsvText += lastCode+"\t";		// 3 - subfigure codes
				tsvText += lastText+"\t";		// 4 - caption text
				tsvText += startPos+"\t";		// 5 - offset begin
				tsvText += (s.getBegin()-1)+"\n";// 6 - offset end
				lastText = ""; 
			}
			if(subfigCodes.size()>0) {
				lastCode = String.join("", subfigCodeList); 
				startPos = s.getBegin(); 
				codeCount += subfigCodes.size();
			}				
			lastText += " " + sentenceText;
		
		}
		tsvText += uiD.getId()+"\t";	// 1 - document id
		tsvText += fignumber+"\t"; 	// 2 - figure number
		tsvText += lastCode+"\t";	// 3 - subfigure codes
		tsvText += lastText+"\t";	// 4 - caption text
		tsvText += startPos+"\t";	// 5 - offset begin
		tsvText += a.getEnd()+"\n";	// 6 - offset end
		
		return tsvText;
	
	}

	/**
	 * @param sentenceText
	 */
	private Set<String> extractPossibleCodes(String sentenceText) {
		Set<String> subfigCodes = new HashSet<String>();

		// SEARCH FOR CAPTIONS FORMATTED LIKE THIS : (A), (B)  
		Matcher m = subFigurePattern1.matcher(sentenceText);
		while(m.find()) {
			subfigCodes.add(m.group(1).toUpperCase());
		}
		
		// SEARCH FOR CAPTIONS FORMATTED LIKE THIS : (A and B) 
		m = subFigurePattern2.matcher(sentenceText);
		while(m.find()) {
			subfigCodes.add(m.group(1).toUpperCase());
			subfigCodes.add(m.group(3).toUpperCase());
		}

		// SEARCH FOR CAPTIONS FORMATTED LIKE THIS : (A, B and C) 
		m = subFigurePattern3.matcher(sentenceText);
		while(m.find()) {
			subfigCodes.add(m.group(1).toUpperCase());
			subfigCodes.add(m.group(2).toUpperCase());
			subfigCodes.add(m.group(4).toUpperCase());
		}

		// SEARCH FOR CAPTIONS FORMATTED LIKE THIS : "a, ...", "C: ..."  
		m = subFigurePattern4.matcher(sentenceText);
		if(m.find()) {
			subfigCodes.add(m.group(1).toUpperCase());
		}

		// SEARCH FOR CAPTIONS FORMATTED LIKE THIS : "(e-f)"  
		m = subFigurePattern5.matcher(sentenceText);
		while(m.find()) {
			char first = m.group(1).toUpperCase().toCharArray()[0];
			char last = m.group(2).toUpperCase().toCharArray()[0];
			for( char c=first; c<=last; c++) {
				subfigCodes.add(c+"");
			}
		}

		// SEARCH FOR CAPTIONS FORMATTED LIKE THIS : "(A middle)"  
		m = subFigurePattern6.matcher(sentenceText.toLowerCase());
		while(m.find()) {
			subfigCodes.add(m.group(1).toUpperCase());
		}
		
		return subfigCodes;
	}
	
}
