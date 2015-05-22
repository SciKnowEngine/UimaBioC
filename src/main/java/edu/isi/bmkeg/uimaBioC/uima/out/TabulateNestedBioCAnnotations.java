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

public class TabulateNestedBioCAnnotations extends JCasAnnotator_ImplBase {

	public final static String PARAM_FILE_PATH = ConfigurationParameterFactory
			.createConfigurationParameterName(
					TabulateNestedBioCAnnotations.class, "outFilePath");
	@ConfigurationParameter(mandatory = true, description = "The path to the summary file.")
	String outFilePath;

	public final static String PARAM_OUTER_ANNOTATION_TYPE = ConfigurationParameterFactory
			.createConfigurationParameterName(
					TabulateNestedBioCAnnotations.class, "ann1Type");
	@ConfigurationParameter(mandatory = true, description = "Outer Annotations to be counted.")
	String ann1Type;

	public final static String PARAM_INNER_ANNOTATION_TYPE = ConfigurationParameterFactory
			.createConfigurationParameterName(
					TabulateNestedBioCAnnotations.class, "ann2Type");
	@ConfigurationParameter(mandatory = true, description = "Inner Annotations to be counted.")
	String ann2Type;

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

		List<UimaBioCAnnotation> outerAnnotations = JCasUtil.selectCovered(
				UimaBioCAnnotation.class, uiD);
		for (UimaBioCAnnotation uiA1 : outerAnnotations) {
			
			Map<String, String> inf = UimaBioCUtils.convertInfons(uiA1.getInfons());
			if( !inf.containsKey("type") || !inf.get("type").equals(ann1Type) ) 
				continue;

			List<UimaBioCAnnotation> innerAnnotations = JCasUtil.selectCovered(
					UimaBioCAnnotation.class, uiA1);
			for (UimaBioCAnnotation uiA2 : innerAnnotations) {
				
				Map<String, String> inf2 = UimaBioCUtils.convertInfons(uiA2.getInfons());
				if( !inf2.get("type").equals(ann2Type) ) 
					continue;
				
				Map<String, Integer> row = table.get(inf.get("value"));
				if( row == null )
					row = new HashMap<String, Integer>();
				
				Integer count = row.get(inf2.get("value"));
				if( count == null )
					count = 0;
				
				count++;
				
				row.put(inf2.get("value"), count);
				table.put(inf.get("value"), row);
				
			}

		}

	}
	
	public void collectionProcessComplete()
			throws AnalysisEngineProcessException {
		super.collectionProcessComplete();

		try {

			if(outFile.exists())
				outFile.delete();
			
			PrintWriter out = new PrintWriter(new BufferedWriter(
					new FileWriter(outFile, true)));

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
