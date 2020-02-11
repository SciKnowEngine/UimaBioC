package edu.isi.bmkeg.uimaBioC.uima.out;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.FileUtils;
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

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;

import bioc.BioCCollection;
import bioc.type.UimaBioCAnnotation;
import bioc.type.UimaBioCDocument;
import bioc.type.UimaBioCSentence;
import edu.isi.bmkeg.uimaBioC.UimaBioCUtils;
import edu.isi.bmkeg.uimaBioC.utils.SubFigureNumberExtractor;

public class SaveAsSentenceSpreadsheets extends JCasAnnotator_ImplBase {

	private static Logger logger = Logger.getLogger(SaveAsSentenceSpreadsheets.class);

	public final static String PARAM_DIR_PATH = ConfigurationParameterFactory
			.createConfigurationParameterName(SaveAsSentenceSpreadsheets.class, "outDirPath");
	@ConfigurationParameter(mandatory = true, description = "The path to the output directory.")
	String outDirPath;

	
	public final static String PARAM_PMC_FILE_NAMES = ConfigurationParameterFactory
			.createConfigurationParameterName(SaveAsSentenceSpreadsheets.class, "pmcFileNamesStr");
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

			Map<String, String> inf = UimaBioCUtils.convertInfons(uiD.getInfons());
			String relPath = inf.get("relative-source-path").replaceAll("\\.txt", "") + ".tsv";
			File outFile = new File(outDirPath + "/" + relPath);
			if( !outFile.getParentFile().exists() ) {
				outFile.getParentFile().mkdirs();
			}
	
			/*if( this.pmcFileNames) 
				outFile = new File(this.outDir.getPath() + "/" + pmcID + ".scidp.discourse.tsv");
			else 
				outFile = new File(this.outDir.getPath() + "/" + id + ".tsv");*/

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

		List<UimaBioCAnnotation> floats = new ArrayList<UimaBioCAnnotation>();
		List<UimaBioCAnnotation> parags = new ArrayList<UimaBioCAnnotation>();
		for (UimaBioCAnnotation a : JCasUtil.selectCovered(jCas, UimaBioCAnnotation.class, start, end)) {
			Map<String, String> infons = UimaBioCUtils.convertInfons(a.getInfons());
			if (infons.containsKey("position") && infons.get("position").equals("float")) {
				floats.add(a);
			} else if (infons.containsKey("value")
					&& (infons.get("value").equals("p") || infons.get("value").equals("title"))) {
				parags.add(a);
			}
		}

		// Column Headings
		out.print("SentenceId");
		out.print("\t");
		out.print("Sentence Text");
		out.print("\t");
		out.print("Codes");
		out.print("\t");
		out.print("ExperimentValues");
		out.print("\t");
		out.print("ExternalRef");
		out.print("\t");		
		out.print("Paragraph");
		out.print("\t");
		out.print("Headings");
		out.print("\t");
		out.print("FloatingBox?");
		out.print("\t");
		out.print("Discourse Type");
		out.print("\t");
		out.print("Offset_Begin");
		out.print("\t");
		out.print("Offset_End");
		out.print("\t");
		out.print("Figure Assignment");

		List<Sentence> sentences = JCasUtil.selectCovered(jCas, Sentence.class, start, end);
		List<Sentence> floatingSentences = new ArrayList<Sentence>();
		SENTENCE_LOOP: for (Sentence s : sentences) {
			for (UimaBioCAnnotation f : floats) {
				if (s.getBegin() >= f.getBegin() && s.getEnd() <= f.getEnd()) {
					floatingSentences.add(s);
					continue SENTENCE_LOOP;
				}
			}
			printOutSentence(jCas, out, s, false);
		}

		SENTENCE_LOOP: for (Sentence s : floatingSentences) {
			printOutSentence(jCas, out, s, true);
		}
	
	}

	private void printOutSentence(JCas jCas, PrintWriter out, Sentence s, boolean floater)
			throws Exception, StackOverflowError {
		
		List<UimaBioCAnnotation> clauseList = new ArrayList<UimaBioCAnnotation>();
		for (UimaBioCAnnotation a : JCasUtil.selectCovered(jCas, UimaBioCAnnotation.class, s)) {
			Map<String, String> infons = UimaBioCUtils.convertInfons(a.getInfons());
			if (infons.get("type").equals("rubicon") && infons.get("value").equals("clause"))
				clauseList.add(a);
		}

		if (clauseList.size() == 0) {
			throw new Exception("Can't have sentences without clauses");
		}
		
		String sentenceId = "";
		String paragraphId = "";
		Set<String> inExHeading = new HashSet<String>();
		String cStr = "";
		String heading = "";
		String discourse = "";
		String pmidStr = "";
		Set<String> figAssignment = new HashSet<String>();	
		
		for (UimaBioCAnnotation clause : clauseList) {

			Map<String, String> infons = UimaBioCUtils.convertInfons(clause.getInfons());

			sentenceId = infons.get("scidp-sentence-number");
			paragraphId = infons.get("scidp-paragraph-number");
			
			String iehStr = infons.get("scidp-inExHeading-string");
			iehStr = iehStr.substring(1, iehStr.length()-1);
			for(String ieh : iehStr.split(",")) {
				inExHeading.add(ieh);				
			}
			
			Set<String> codeSet = this.figExtractor.extractExptsFromClause(jCas, clause);
			List<String> codes =  new ArrayList<String>(codeSet);
			java.util.Collections.sort(codes);
			for(String c : codes ) {
				if( cStr.length() > 0 ) 
					cStr += "|";
				cStr += c;	
			}
			
			Set<String> pmidSet = new HashSet<String>();
			for (UimaBioCAnnotation a : JCasUtil.selectCovered(UimaBioCAnnotation.class, clause)) {
				Map<String, String> inf = UimaBioCUtils.convertInfons(a.getInfons());
				if( inf.containsKey("pmid") ) 
					pmidSet.add(inf.get("pmid"));
			}
			List<String> pmidList = new ArrayList<String>(pmidSet);
			Collections.sort(pmidList);
			for(String pmid : pmidList) {
				pmidStr += "["+pmid+"]";
			}			
			
			heading = UimaBioCUtils.readHeadingString(jCas, clause, "");
			
			if( discourse.length() > 0 ) 
				discourse += "|";
			if (infons.containsKey("scidp-discourse-type"))
				discourse += infons.get("scidp-discourse-type");
			else
				discourse += "-";

			if (infons.containsKey("scidp-fig-assignment")) {
				for( String figAssign : infons.get("scidp-fig-assignment").split("\\|") ) {
					figAssignment.add(figAssign);				
				}
			}

		}
				
		if(inExHeading.contains("")) 
			inExHeading.remove("");
		
		out.print(sentenceId);
		out.print("\t");
		out.print(UimaBioCUtils.readTokenizedText(jCas, s));
		out.print("\t");
		out.print(inExHeading);
		out.print("\t");
		out.print(cStr);
		out.print("\t");	
		out.print(pmidStr);
		out.print("\t");
		out.print(paragraphId);
		out.print("\t");
		out.print(heading);
		out.print("\t");
		out.print(floater);
		out.print("\t");
		out.print(discourse);

		out.print("\t");
		out.print(s.getBegin());
		out.print("\t");
		out.print(s.getEnd());

		out.print("\t");
		String fa_text = "";
		for( String fa : figAssignment) {
			if( fa_text.length() > 0 )
				fa_text += "|";
			fa_text += fa;
		}
		out.print(fa_text);			

		out.print("\n");
		
	}
	
}
