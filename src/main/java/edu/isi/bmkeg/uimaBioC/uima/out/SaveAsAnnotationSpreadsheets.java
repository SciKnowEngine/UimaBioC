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
import org.apache.uima.resource.ResourceInitializationException;
import org.uimafit.component.JCasAnnotator_ImplBase;
import org.uimafit.descriptor.ConfigurationParameter;
import org.uimafit.factory.ConfigurationParameterFactory;
import org.uimafit.util.JCasUtil;

import bioc.BioCCollection;
import bioc.type.UimaBioCAnnotation;
import bioc.type.UimaBioCDocument;
import bioc.type.UimaBioCPassage;
import edu.isi.bmkeg.uimaBioC.UimaBioCUtils;
import edu.isi.bmkeg.uimaBioC.utils.SubFigureNumberExtractor;

public class SaveAsAnnotationSpreadsheets extends JCasAnnotator_ImplBase {

	private static Logger logger = Logger.getLogger(SaveAsAnnotationSpreadsheets.class);

	public final static String PARAM_DIR_PATH = ConfigurationParameterFactory
			.createConfigurationParameterName(SaveAsAnnotationSpreadsheets.class, "outDirPath");
	@ConfigurationParameter(mandatory = true, description = "The path to the output directory.")
	String outDirPath;
	
	public final static String PARAM_PMC_FILE_NAMES = ConfigurationParameterFactory
			.createConfigurationParameterName(SaveAsAnnotationSpreadsheets.class, "pmcFileNamesStr");
	@ConfigurationParameter(mandatory = false, description = "Should we use PMC or PMID file names?")
	String pmcFileNamesStr;
	Boolean pmcFileNames = true;

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

		if (this.pmcFileNamesStr != null && this.pmcFileNamesStr.toLowerCase().equals("false")) {
			pmcFileNames = false;
		} else {
			pmcFileNames = true;
		}

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

			Map<String, String> infons = UimaBioCUtils.convertInfons(uiD.getInfons());
			String pmcID = "PMC" + infons.get("pmc");
			if (infons.containsKey("pmcid"))
				pmcID = infons.get("pmcid");
			if (infons.containsKey("accession"))
				pmcID = infons.get("accession");

			File outFile = null;
			if( this.pmcFileNames) 
				outFile = new File(this.outDir.getPath() + "/" + pmcID + ".scidp.discourse.tsv");
			else 
				outFile = new File(this.outDir.getPath() + "/" + id + ".tsv");

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

		UimaBioCDocument uiD = JCasUtil.selectSingle(jCas, UimaBioCDocument.class);
		if (uiD.getId().equals("skip"))
			return;
		String docId = uiD.getId();

		String psgIds = "";
		List<UimaBioCPassage> uiPs = JCasUtil.selectCovering(jCas, UimaBioCPassage.class, start, end);
		for (UimaBioCPassage uiP : uiPs ) {
			Map<String, String> infons = UimaBioCUtils.convertInfons(uiP.getInfons());
			if( infons.containsKey("id") ) {
				if( psgIds.length() > 0 ) 
					psgIds += "|";
				psgIds += infons.get("id");
			} else {				
				psgIds = "-";
				break;
			}
		}

		// Column Headings
		out.print("DocumentId");
		out.print("\t");
		out.print("PassageId");
		out.print("\t");
		out.print("Text");
		out.print("\t");
		out.print("type");
		out.print("\t");
		out.print("value");
		out.print("\t");
		out.print("Infon_Keys");
		out.print("\t");
		out.print("Infon_Values");
		out.print("\t");
		out.print("Offset_Begin");
		out.print("\t");
		out.print("Offset_End");

		out.print("\n");

		List<UimaBioCAnnotation> annotList = JCasUtil.selectCovered(jCas, UimaBioCAnnotation.class, start, end);		
		for (UimaBioCAnnotation annot : annotList) {

			out.print(docId + "\t");
			out.print(psgIds + "\t");
			
			String txt = UimaBioCUtils.readTokenizedText(jCas, annot);
			int l = txt.length();
			if( l < 500 ) {
				out.print(txt + "\t");
			} else {
				out.print("..."+"\t");				
			}
			
			Map<String, String> infons = UimaBioCUtils.convertInfons(annot.getInfons());
			out.print(infons.get("type") + "\t");
			out.print(infons.get("value").replaceAll("\\n", " ") + "\t");
			String keys = "", values = "";
			for (String key : infons.keySet()) {
				if( key.equals("value") || key.equals("type") )
					continue;
				if(keys.length()>0) keys += "<|>";
				keys += key;
				if(values.length()>0) values += "<|>";
				values += infons.get(key).replaceAll("\\n", " ");
			}
			out.print(keys + "\t");
			out.print(values + "\t");
			
			out.print(annot.getBegin() + "\t");
			out.print(annot.getEnd() + "\t");			
			out.print("\n");

		}
		
	}
	
	@Override
	public void collectionProcessComplete() throws AnalysisEngineProcessException {

		/*File tempFile = new File(outFile.getPath() + ".ttl");
		PrintWriter out;
		try {
		
			out = new PrintWriter(new BufferedWriter(
					new FileWriter(tempFile, true)));
			ontModel.write(out, "TTL");
			out.close();

		} catch (IOException e) {
			throw new AnalysisEngineProcessException(e);
		}*/

		/*
		 * Gson gson = new Gson(); 
		 * String json = gson.toJson(d);
		 * 
		 * PrintWriter out = new PrintWriter(new BufferedWriter( new
		 * FileWriter(outFile, true)));
		 * 
		 * out.write(json);
		 * 
		 * out.close();
		 */
		//this.collection = new BioCCollection();

		System.out.print("DONE DONE DONE");

	}
}
