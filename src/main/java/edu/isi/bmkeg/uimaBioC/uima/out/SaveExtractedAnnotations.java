package edu.isi.bmkeg.uimaBioC.uima.out;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

public class SaveExtractedAnnotations extends JCasAnnotator_ImplBase {

	public final static String PARAM_ANNOT_2_EXTRACT = ConfigurationParameterFactory
			.createConfigurationParameterName(
					SaveExtractedAnnotations.class, "annot2Extract");
	@ConfigurationParameter(mandatory = true, description = "The section heading *pattern* to extract")
	String annot2Extract;
	private Pattern patt;
	
	public final static String PARAM_DIR_PATH = ConfigurationParameterFactory
			.createConfigurationParameterName(
					SaveExtractedAnnotations.class, "outDirPath");
	@ConfigurationParameter(mandatory = true, description = "The path to the output directory.")
	String outDirPath;

	public final static String PARAM_HEADER_LINKS = ConfigurationParameterFactory
			.createConfigurationParameterName(
					SaveExtractedAnnotations.class, "headerLinksStr");
	@ConfigurationParameter(mandatory = true, description = "Should we include headers and links in the output.")
	String headerLinksStr;
	Boolean headerLinks = false;
	
	private File outDir;
	private BioCCollection collection;

	Map<String,Map<String,Integer>> table = new HashMap<String, Map<String, Integer>>();
	
	public void initialize(UimaContext context)
			throws ResourceInitializationException {

		super.initialize(context);

		this.outDirPath = (String) context
				.getConfigParameterValue(PARAM_DIR_PATH);
		this.outDir = new File(this.outDirPath);

		if( !this.outDir.exists() )
			this.outDir.mkdirs();
		
		if(this.headerLinksStr.toLowerCase().equals("true") ) {
			headerLinks = true;
		} else if(this.headerLinksStr.toLowerCase().equals("false") ) {
			headerLinks = false;
		} else {
			Exception e = new Exception("Please set PARAM_HEADER_LINKS to 'true' or 'false'");
			throw new ResourceInitializationException(e);
		}		
			
		this.collection = new BioCCollection();
		
		this.patt = Pattern.compile(this.annot2Extract);

	}

	public void process(JCas jCas) throws AnalysisEngineProcessException {

		UimaBioCDocument uiD = JCasUtil.selectSingle(jCas,
				UimaBioCDocument.class);

		String id = uiD.getId();

		boolean anyTextExtracted = false;
		
		List<UimaBioCAnnotation> outerAnnotations = JCasUtil.selectCovered(
				UimaBioCAnnotation.class, uiD);
		for (UimaBioCAnnotation uiA1 : outerAnnotations) {
			
			Map<String, String> inf = UimaBioCUtils.convertInfons(uiA1.getInfons());
			if( !inf.containsKey("type")  ) 
				continue;

			if( !(inf.get("type").equals("formatting") &&
					inf.get("value").equals("sec")) ) {
				continue;
			}
			
			Matcher match = this.patt.matcher(inf.get("sectionHeading"));
			if( !match.find() ) {
				continue;
			}

			anyTextExtracted = true;
			File outFile = new File(this.outDir.getPath() + "/" + 
					id + "_" + this.annot2Extract + "_" + uiA1.getBegin() + 
					"_" + uiA1.getEnd() + ".txt");
			
			this.dumpSectionToFile(jCas, outFile, uiA1);
			
		}
		
		if(!anyTextExtracted) {

			for (UimaBioCAnnotation uiA1 : outerAnnotations) {
				
				Map<String, String> inf = UimaBioCUtils.convertInfons(uiA1.getInfons());
				if( !inf.containsKey("type")  ) 
					continue;

				if( !(inf.get("type").equals("formatting") &&
						inf.get("value").equals("body")) ) {
					continue;
				}
				
				File outFile = new File(this.outDir.getPath() + "/" + 
						id + "_body_" + uiA1.getBegin() + 
						"_" + uiA1.getEnd() + ".txt");
				
				this.dumpSectionToFile(jCas, outFile, uiA1);
				break;
				
			}
			
		}

		
	}
	
	private void dumpSectionToFile(JCas jCas, File outFile, UimaBioCAnnotation uiA1) 
			throws AnalysisEngineProcessException {
		
		PrintWriter out;
		try {
			out = new PrintWriter(new BufferedWriter(
					new FileWriter(outFile, true)));
		} catch (IOException e) {
			throw( new AnalysisEngineProcessException(e));
		}
		
		List<Sentence> sentences = JCasUtil.selectCovered(
				org.cleartk.token.type.Sentence.class, uiA1
				);
		for(Sentence s : sentences) {
			
			out.print( s.getCoveredText() );
			
			//
			// Identify exLinks, inLinks or headers
			//
			if( this.headerLinks ) {
				out.print("\t");
				Set<String> codes = new HashSet<String>();
				for( UimaBioCAnnotation a : JCasUtil.selectCovered(
						UimaBioCAnnotation.class, s) ) {
					Map<String, String> infons = 
							UimaBioCUtils.convertInfons(a.getInfons());
					if( infons.containsKey("refType") 
							&& infons.get("refType").equals("bibr") ){
						codes.add("exLink");							
					} else if( infons.containsKey("refType") 
							&& infons.get("refType").equals("fig") ){
						codes.add("inLink");							
					} 
				}	
				// Looking for section headings
				for( UimaBioCAnnotation a : JCasUtil.selectCovering(jCas, 
						UimaBioCAnnotation.class, s.getBegin(), s.getEnd()) ) {
					Map<String, String> infons = 
							UimaBioCUtils.convertInfons(a.getInfons());						
					if( infons.get("value").equals("title") ) {
						codes.add("header");							
					}
				}					
				out.print(codes.toString());
			
			}
			
			out.print("\n");
			
		}
		out.close();
	}
	
}
