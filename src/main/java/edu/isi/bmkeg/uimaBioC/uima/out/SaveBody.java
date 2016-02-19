package edu.isi.bmkeg.uimaBioC.uima.out;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
import edu.isi.bmkeg.uimaBioC.UimaBioCUtils;

public class SaveBody extends JCasAnnotator_ImplBase {

	public final static String PARAM_DIR_PATH = ConfigurationParameterFactory
			.createConfigurationParameterName(
					SaveBody.class, "outDirPath");
	@ConfigurationParameter(mandatory = true, description = "The path to the output directory.")
	String outDirPath;

	private File outDir;

	Map<String,Map<String,Integer>> table = new HashMap<String, Map<String, Integer>>();
	
	public void initialize(UimaContext context)
			throws ResourceInitializationException {

		super.initialize(context);

		this.outDirPath = (String) context
				.getConfigParameterValue(PARAM_DIR_PATH);
		this.outDir = new File(this.outDirPath);

		if( !this.outDir.exists() )
			this.outDir.mkdirs();
					
	}

	public void process(JCas jCas) throws AnalysisEngineProcessException {

		UimaBioCDocument uiD = JCasUtil.selectSingle(jCas,
				UimaBioCDocument.class);
		if( uiD.getId().equals("skip") )
			return;
		
		String id = uiD.getId();
		
		List<UimaBioCAnnotation> annotations = JCasUtil.selectCovered(
				UimaBioCAnnotation.class, uiD);
		for (UimaBioCAnnotation a : annotations) {
			Map<String, String> inf = UimaBioCUtils.convertInfons(a.getInfons());
			if( inf.containsKey("value") 
					&& inf.get("value").toLowerCase().equals("body")) {

				File outFile = new File(this.outDir.getPath() + "/" + 
						id + "_body_" + a.getBegin() + 
						"_" + a.getEnd() + ".txt");
				
				PrintWriter out;
				try {
					out = new PrintWriter(new BufferedWriter(
							new FileWriter(outFile, true)));
				} catch (IOException e) {
					throw( new AnalysisEngineProcessException(e));
				}
				
				out.println( a.getCoveredText() );
				out.close();
				
				break;
			}
		
		}	
		
	}
	
}
