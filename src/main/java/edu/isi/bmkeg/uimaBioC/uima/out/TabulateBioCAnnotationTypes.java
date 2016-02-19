package edu.isi.bmkeg.uimaBioC.uima.out;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.stream.XMLStreamException;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.uimafit.component.JCasAnnotator_ImplBase;
import org.uimafit.descriptor.ConfigurationParameter;
import org.uimafit.factory.ConfigurationParameterFactory;
import org.uimafit.util.JCasUtil;

import com.google.gson.Gson;

import bioc.BioCCollection;
import bioc.io.BioCCollectionWriter;
import bioc.io.BioCFactory;
import bioc.type.UimaBioCAnnotation;
import bioc.type.UimaBioCDocument;
import edu.isi.bmkeg.uimaBioC.UimaBioCUtils;

public class TabulateBioCAnnotationTypes extends JCasAnnotator_ImplBase {

	public final static String PARAM_TITLE = ConfigurationParameterFactory
			.createConfigurationParameterName(
					TabulateBioCAnnotationTypes.class, "title");
	@ConfigurationParameter(mandatory = true, description = "The title of the spreadsheet.")
	String title;
	
	public final static String PARAM_FILE_PATH = ConfigurationParameterFactory
			.createConfigurationParameterName(
					TabulateBioCAnnotationTypes.class, "outFilePath");
	@ConfigurationParameter(mandatory = true, description = "The path to the summary file.")
	String outFilePath;

	private File outFile;
	private BioCCollection collection;

	Map<String,Map<String,Integer>> table = new HashMap<String, Map<String, Integer>>();
	
	public void initialize(UimaContext context)
			throws ResourceInitializationException {

		super.initialize(context);

		this.outFilePath = (String) context
				.getConfigParameterValue(PARAM_FILE_PATH);
		this.outFile = new File(this.outFilePath);

		this.collection = new BioCCollection();

	}

	public void process(JCas jCas) throws AnalysisEngineProcessException {

		UimaBioCDocument uiD = JCasUtil.selectSingle(jCas,
				UimaBioCDocument.class);
		if( uiD.getId().equals("skip") )
			return;
		
		Map<String, Integer> row = null;
		if( table.containsKey(uiD.getId()) ) {
			row = table.get(uiD.getId()); 
		} else {
			row = new HashMap<String, Integer>();
		}
		
		List<UimaBioCAnnotation> outerAnnotations = JCasUtil.selectCovered(
				UimaBioCAnnotation.class, uiD);
		for (UimaBioCAnnotation uiA1 : outerAnnotations) {
			
			Map<String, String> inf = UimaBioCUtils.convertInfons(uiA1.getInfons());
			if( !inf.containsKey("type")  ) 
				continue;

			Integer count = row.get(inf.get("type") + "." + inf.get("value"));
			if( count == null )
				count = 0;
				
			count++;
				
			row.put(inf.get("type") + "." + inf.get("value"), count);
		}

		table.put(uiD.getId(), row);

	}
	
	public void collectionProcessComplete()
			throws AnalysisEngineProcessException {
		super.collectionProcessComplete();

		try {

			if(outFile.exists())
				outFile.delete();
			
			PrintWriter out = new PrintWriter(new BufferedWriter(
					new FileWriter(outFile, true)));
			
			out.println( title );
			out.println( "pmid\ttype\tcount" );
			
			for( String ann1 : this.table.keySet() ) {
				for( String ann2 : this.table.get(ann1).keySet() ) {
					out.println(ann1 + "\t" + ann2 + "\t" + this.table.get(ann1).get(ann2) );
				}
			}
			
			out.close();
		} catch (IOException e) {

			throw new AnalysisEngineProcessException(e);

		}

	}

}
