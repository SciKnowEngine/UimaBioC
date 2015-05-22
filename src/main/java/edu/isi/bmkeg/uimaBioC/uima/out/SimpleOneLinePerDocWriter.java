package edu.isi.bmkeg.uimaBioC.uima.out;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

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

import edu.isi.bmkeg.uimaBioC.UimaBioCUtils;

import bioc.type.UimaBioCDocument;

public class SimpleOneLinePerDocWriter extends JCasAnnotator_ImplBase {

	public final static String PARAM_OUT_FILE_PATH = ConfigurationParameterFactory
			.createConfigurationParameterName( SimpleOneLinePerDocWriter.class, "filePath" );
	@ConfigurationParameter(mandatory = true, description = "Output file.")
	String filePath;
	
	File file;
	
	private File outFile;
	
	public void initialize(UimaContext context)
			throws ResourceInitializationException {

		super.initialize(context);
		
		this.outFile = new File(this.filePath);
				
	}

	public void process(JCas jCas) throws AnalysisEngineProcessException {
				
		try {
			
			PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(outFile, true)));

			UimaBioCDocument uiD = JCasUtil.selectSingle(jCas, UimaBioCDocument.class);
			
			Map<String, String> inf = UimaBioCUtils.convertInfons(uiD.getInfons());

			for (Sentence sentence : JCasUtil.select(jCas, Sentence.class)) {
				List<Token> tokens = JCasUtil.selectCovered(jCas, Token.class, sentence);	
				if (tokens.size() <= 0) { continue; }
				
				List<String> tokenStrings = JCasUtil.toText(tokens);						
				for (int i = 0; i < tokens.size(); i++) {
					out.print(tokenStrings.get(i) + " ");
				}
			}

			out.print("\n");
			out.close();
		
		} catch (IOException e) {
			
			throw new AnalysisEngineProcessException(e);
			 
		}

	}

}
