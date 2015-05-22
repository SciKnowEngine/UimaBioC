package edu.isi.bmkeg.uimaBioC.uima.out;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

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

import bioc.type.UimaBioCDocument;
import edu.isi.bmkeg.uimaBioC.UimaBioCUtils;

public class SimpleOneLinePerDocWriter extends JCasAnnotator_ImplBase {

	public final static String PARAM_OUT_FILE_PATH = ConfigurationParameterFactory
			.createConfigurationParameterName(SimpleOneLinePerDocWriter.class,
					"filePath");
	@ConfigurationParameter(mandatory = true, description = "Output file.")
	String filePath;

	File file;

	private PrintWriter out;

	Pattern patt = Pattern.compile("\\s+");

	public void initialize(UimaContext context)
			throws ResourceInitializationException {

		super.initialize(context);

		try {
			out = new PrintWriter(new BufferedWriter(new FileWriter(new File(
					this.filePath), true)));
		} catch (IOException e) {
			throw new ResourceInitializationException(e);
		}

	}

	public void process(JCas jCas) throws AnalysisEngineProcessException {

		UimaBioCDocument uiD = JCasUtil.selectSingle(jCas,
				UimaBioCDocument.class);

		Map<String,String> infons = UimaBioCUtils.convertInfons(uiD.getInfons());
		String p = infons.get("relative-source-path");
		
		String txt = uiD.getCoveredText().replaceAll("\\r+", " ");
		txt = txt.replaceAll("\\n+", " ");
		txt = txt.replaceAll(" \\- ", "");
		txt = txt.replaceAll("\\s+", " ");
		
		out.println(p.substring(p.lastIndexOf("/"),p.length()) + "\t" + txt);

	}
	
	public void collectionProcessComplete()
			throws AnalysisEngineProcessException {
		
		super.collectionProcessComplete();			
		out.close();

	}

}
