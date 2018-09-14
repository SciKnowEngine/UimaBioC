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
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;
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

public class SaveAsUntokenizedSentenceSpreadsheets extends JCasAnnotator_ImplBase {

	private static Logger logger = Logger.getLogger(SaveAsSentenceSpreadsheets.class);

	public final static String PARAM_DIR_PATH = ConfigurationParameterFactory
			.createConfigurationParameterName(SaveAsUntokenizedSentenceSpreadsheets.class, "outDirPath");
	@ConfigurationParameter(mandatory = true, description = "The path to the output directory.")
	String outDirPath;

	private File outDir;
	private BioCCollection collection;
	
	private SubFigureNumberExtractor figExtractor;

	Map<String, Map<String, Integer>> table = new HashMap<String, Map<String, Integer>>();
	
	public void initialize(UimaContext context) throws ResourceInitializationException {

		super.initialize(context);

		this.outDirPath = (String) context.getConfigParameterValue(PARAM_DIR_PATH);
		this.outDir = new File(this.outDirPath);

		if (!this.outDir.exists())
			this.outDir.mkdirs();

		this.collection = new BioCCollection();

		try {
			this.figExtractor = new SubFigureNumberExtractor();
		} catch (Exception e) {
			throw new ResourceInitializationException(e);
		}
		
	}

	public void process(JCas jCas) throws AnalysisEngineProcessException {

		try {
			
			UimaBioCDocument uiD = JCasUtil.selectSingle(jCas, UimaBioCDocument.class);
			if (uiD.getId().equals("skip"))
				return;

			String id = uiD.getId();
			
			Map<String, String> inf = UimaBioCUtils.convertInfons(uiD.getInfons());
			String pmcID = "PMC" + inf.get("pmc");
			if (inf.containsKey("pmcid"))
				pmcID = inf.get("pmcid");
			if (inf.containsKey("accession"))
				pmcID = inf.get("accession");
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
									
			// Column Headings
			out.print("Paragraph");
			out.print("\t");
			out.print("SentenceId");
			out.print("\t");
			out.print("Sentence Text");
			out.print("\t");
			out.print("Codes");
			out.print("\t");
			out.print("pmids");
			out.print("\t");
			out.print("Figures");
			out.print("\t");		
			out.print("Headings");
			out.print("\t");
			out.print("Offset_Begin");
			out.print("\t");
			out.print("Offset_End");
			out.print("\t\n");
			
			List<UimaBioCAnnotation> parags = UimaBioCUtils.readParags(jCas);
			Map<String, Sentence> sMap = new HashMap<String, Sentence>();
			int sId = 0;
			PARAG_LOOP: for (int i=0; i<parags.size(); i++) {
				UimaBioCAnnotation parag = parags.get(i);
				Map<String, String> infons = UimaBioCUtils.convertInfons(parag.getInfons());
				
				String pString = String.format("%04d", i);
				if( UimaBioCUtils.isAnnotationInTableOrFigure(parag, jCas) ) {
					pString = "TF_" + pString;
				} else {
					pString = "P__" + pString;
				}
				
				List<Sentence> sentences = JCasUtil.selectCovered(jCas, Sentence.class, 
						parag.getBegin(), parag.getEnd());
				SENTENCE_LOOP: for(Sentence s : sentences) {
					String key = String.format("%05d", sId);
					sId++;
					key = pString + "\tS__" + key;
					sMap.put(key, s);
				}
			}
			
			List<String> sortedKeys=new ArrayList<String>(sMap.keySet());
			Collections.sort(sortedKeys);
			for(String key : sortedKeys) {
				Sentence s = sMap.get(key);
				printOutSentence(jCas, out, key, s, false);
			}				

			out.close();

		} catch (Exception e) {
			
			throw new AnalysisEngineProcessException(e);
			
		}

	}

	private void printOutSentence(JCas jCas, PrintWriter out, String key, Sentence s, boolean floater)
			throws Exception, StackOverflowError {
		
		Set<String> inExHeading = new HashSet<String>();
		String cStr = "";
		
		//
		// Identify exLinks, inLinks or headers
		//
		Map<Integer, Integer> currLvl = new HashMap<Integer, Integer>();
				
		out.print(key);
		out.print("\t");
		out.print(UimaBioCUtils.readUntokenizedText(jCas, (Annotation) s));
		out.print("\t");
		
		String newCodeStr = "";
		for( String str : UimaBioCUtils.extractCodesFromClause(jCas, s) ) {
			if( newCodeStr.length() > 0 )
				newCodeStr += "|";
			newCodeStr += str;
		}
		out.print(newCodeStr);
		out.print("\t");

		String newPmidsStr = "";
		for( String str : UimaBioCUtils.extractPmidFromReference(jCas, s) ) {
			newPmidsStr += "["+str+"]";
		}
		out.print(newPmidsStr);
		out.print("\t");
		
		Set<String> codeSet = this.figExtractor.extractExptsFromClause(jCas, s);
		List<String> codes =  new ArrayList<String>(codeSet);
		java.util.Collections.sort(codes);
		for(String c : codes ) {
			if( cStr.length() > 0 ) 
				cStr += "|";
			cStr += c;	
		}
		out.print(cStr);
		out.print("\t");
		
		out.print(UimaBioCUtils.readHeadingString(jCas, s, ""));
		out.print("\t");
		out.print(s.getBegin());
		out.print("\t");
		out.print(s.getEnd());
		
		out.print("\n");
		
	}

}



