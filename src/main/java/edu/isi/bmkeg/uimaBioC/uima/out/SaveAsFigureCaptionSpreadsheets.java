package edu.isi.bmkeg.uimaBioC.uima.out;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
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
	
	private Pattern figurePattern = Pattern.compile("^\\s*Figure\\s*(\\d+)");

	// (A)
	private Pattern subFigurePattern1 = Pattern.compile(
			"[\\(\\[]\\s+([A-Za-z])\\s+[\\)\\]]");
	// (A and B), (A & B)
	private Pattern subFigurePattern2 = Pattern.compile(
			"[\\(\\[]\\s+([A-Za-z])\\s+(and|&)\\s+([A-Za-z])\\s+[\\)\\]]");
	// (A, B, and C), (A,B and C)
	private Pattern subFigurePattern3 = Pattern.compile(
			"[\\(\\[]\\s+([A-Za-z])\\s+,\\s+([A-Za-z])[\\s+,](and|&)\\s([A-Za-z])\\s+[\\)\\]]");
	// a, ...	
	private Pattern subFigurePattern4 = Pattern.compile("^\\s+([A-Za-z]) [,:\\)\\]]");	
	// (A - F)
	private Pattern subFigurePattern5 = Pattern.compile(
			"[\\(\\[]\\s+([A-Za-z])\\s+\\-\\s+([A-Za-z])\\s+[\\)\\]]");

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
			String relPath = inf.get("relative-source-path").replaceAll("\\.txt", "") + ".tsv";
			File outFile = new File(outDirPath + "/" + relPath);
			if( !outFile.getParentFile().exists() ) {
				outFile.getParentFile().mkdirs();
			}
	
			PrintWriter out;
			try {
				out = new PrintWriter(new BufferedWriter(new FileWriter(outFile, true)));
			} catch (IOException e) {
				throw (new AnalysisEngineProcessException(e));
			}
						
			this.dumpSectionToFile(jCas, out, uiD.getBegin(), uiD.getEnd());

			out.close();

		} catch (Exception e) {
			
			throw new AnalysisEngineProcessException(e);
			
		}

	}

	private void dumpSectionToFile(JCas jCas, PrintWriter out, int start, int end) throws Exception {

		List<UimaBioCAnnotation> floats_and_parags = new ArrayList<UimaBioCAnnotation>();
		for (UimaBioCAnnotation a : JCasUtil.selectCovered(jCas, UimaBioCAnnotation.class, start, end)) {
			Map<String, String> infons = UimaBioCUtils.convertInfons(a.getInfons());
			if (infons.containsKey("position") && infons.get("position").equals("float")) {
				floats_and_parags.add(a);
			} else if (infons.containsKey("value")
					&& (infons.get("value").equals("p") || infons.get("value").equals("title"))) {
				floats_and_parags.add(a);
			}
		}

		// Column Headings
		out.print("DocId");
		out.print("\t");
		out.print("Figure");
		out.print("\t");
		out.print("SubFigure");
		out.print("\t");
		out.print("Caption");
		out.print("\t");
		out.print("Offset_Begin");
		out.print("\t");
		out.print("Offset_End");
		out.print("\n");

		CAPTION_LOOP: for (UimaBioCAnnotation p : floats_and_parags) {
			Matcher m = figurePattern.matcher(UimaBioCUtils.readTokenizedText(jCas, p));
			if( m.find() ) {
				printOutCaption(jCas, out, p, m.group(1));
			}
		}
		
	}

	private void printOutCaption(JCas jCas, PrintWriter out, UimaBioCAnnotation a, String fignumber)
			throws Exception, StackOverflowError {

		UimaBioCDocument uiD = JCasUtil.selectSingle(jCas, UimaBioCDocument.class);
		
		char[] alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();
		int codeCount = 0;
		int startPos = a.getBegin();
		String lastCode = "";
		String lastText = "";
		
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
					if(!subfigCodes.contains(alphabet[codeCount+i]+"")) {
						String errorCode = subfigCodeList.get(i);			
						toRemove.add(errorCode);	 // remove all out-of-sequence hits.
					}
				}
				subfigCodes.removeAll(toRemove);
			}
			
			//
			// This sentence denotes a new subfigure, so print out the last subfigure notation.
			//
			if(subfigCodes.size()>0 && lastText.length()>0) {	
				out.print(uiD.getId()+"\t");		// 1 - document id
				out.print(fignumber+"\t"); 		// 2 - figure number
				out.print(lastCode+"\t");		// 3 - subfigure codes
				out.print(lastText+"\t");		// 4 - caption text
				out.print(startPos+"\t");		// 5 - offset begin
				out.print((s.getBegin()-1)+"\n");// 6 - offset end
				lastText = ""; 
			}
			if(subfigCodes.size()>0) {
				lastCode = String.join("", subfigCodeList); 
				startPos = s.getBegin(); 
				codeCount += subfigCodes.size();
			}				
			lastText += " " + sentenceText;
		
		}
		out.print(uiD.getId()+"\t");	// 1 - document id
		out.print(fignumber+"\t"); 	// 2 - figure number
		out.print(lastCode+"\t");	// 3 - subfigure codes
		out.print(lastText+"\t");	// 4 - caption text
		out.print(startPos+"\t");	// 5 - offset begin
		out.print(a.getEnd()+"\n");	// 6 - offset end
	
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
		
		return subfigCodes;
	}
	
}
