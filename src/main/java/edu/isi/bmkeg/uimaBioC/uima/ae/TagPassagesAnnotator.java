package edu.isi.bmkeg.uimaBioC.uima.ae;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.Map;

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

import com.google.common.io.Files;

import bioc.type.UimaBioCAnnotation;
import bioc.type.UimaBioCDocument;
import bioc.type.UimaBioCPassage;
import edu.isi.bmkeg.uimaBioC.UimaBioCUtils;

/**
 * 
 */
public class TagPassagesAnnotator extends JCasAnnotator_ImplBase {

	private static Logger logger = Logger.getLogger(TagPassagesAnnotator.class);

/*	public final static String PARAM_PYTHON_EXEC_PATH = ConfigurationParameterFactory
			.createConfigurationParameterName(TagPassagesAnnotator.class, "pythonExecPath");
	@ConfigurationParameter(mandatory = true, description = "Where the python executable is")
	String pythonExecPath;*/
	
	public final static String PARAM_OUT_DIR_PATH = ConfigurationParameterFactory
			.createConfigurationParameterName(TagPassagesAnnotator.class, "outDirPath");
	@ConfigurationParameter(mandatory = true, description = "The place to put the output files")
	String outDirPath;
	
	//File tempDir;

	public void initialize(UimaContext context) throws ResourceInitializationException {

		super.initialize(context);
		
		//tempDir = Files.createTempDir();
		//tempDir.deleteOnExit();

	}

	@Override
	public void process(JCas jCas) throws AnalysisEngineProcessException {

		try {

			UimaBioCDocument uiD = JCasUtil.selectSingle(jCas, UimaBioCDocument.class);
			if (uiD.getId().equals("skip"))
				return;

			//
			// DUMP THESE DATA TO A LOCAL DIRECTORY.
			//
			File outFile = new File(outDirPath + "/" + uiD.getId() + ".txt" );
			PrintWriter out;
			try {
				out = new PrintWriter(new BufferedWriter(new FileWriter(outFile, true)));
			} catch (IOException e) {
				throw (new AnalysisEngineProcessException(e));
			}
			
			UimaBioCPassage docP = UimaBioCUtils.readDocument(jCas);

			for (UimaBioCAnnotation paragraph : JCasUtil.selectCovered(jCas, UimaBioCAnnotation.class, docP)) {
				Map<String, String> infons = UimaBioCUtils.convertInfons(paragraph.getInfons());
				if (infons.get("type").equals("formatting") && 
						(infons.get("value").equals("p") ) ) {
					boolean go = false;
					for( Sentence sentence : JCasUtil.selectCovered(Sentence.class, paragraph) ) {	
						for (UimaBioCAnnotation clause : JCasUtil.selectCovered(jCas, UimaBioCAnnotation.class, sentence)) {
							Map<String, String> infons2 = UimaBioCUtils.convertInfons(clause.getInfons());
							if (infons2.get("type").equals("rubicon") && infons2.get("value").equals("clause")  ) {
								go  = true;
								for (Token t : JCasUtil.selectCovered(jCas, Token.class, clause)) {
									out.print( t.getCoveredText() + " ");		
								}
								out.print("\n");		
							}
						}	
					}
					if(go)
						out.print("\n");		
				}
			}
			out.close();
			
			// RUN PYTHON TO CLASSIFY DISCOURSE SEGMENT TYPES.
			/*if(!pythonExecPath.endsWith("/"))
				pythonExecPath += "/";
			String command = "python " + pythonExecPath + "statement_classification/tag_passages.py \"" + outFile.getPath() + "\"";

			ProcessBuilder pb = new ProcessBuilder(command.split(" "));
			Map<String,String> env = pb.environment();
			env.put("PYTHONPATH", "/usr/local/lib/python2.7/site-packages");
			Process p = pb.start();
			
			if (p == null) {
				throw new Exception("Can't execute " + command);
			}

			InputStream in = p.getErrorStream();
			BufferedInputStream buf = new BufferedInputStream(in);
			InputStreamReader inread = new InputStreamReader(buf);
			BufferedReader bufferedreader = new BufferedReader(inread);
			String line, out2 = "";

			while ((line = bufferedreader.readLine()) != null) {
				out2 += line;
			}
			
			try {
				if (p.waitFor() != 0) {
					System.err.println("CMD: " + command);
					System.err.println("RETURNED ERROR: " + out);
				}
			} catch (Exception e) {
				System.err.println(out);
			} finally {
				// Close the InputStream
				bufferedreader.close();
				inread.close();
				buf.close();
				in.close();
			}
			
			int i = 1;*/
			
			// THEN WHAT TO DO?

		} catch (Exception e) {

			throw new AnalysisEngineProcessException(e);

		}

	}

}