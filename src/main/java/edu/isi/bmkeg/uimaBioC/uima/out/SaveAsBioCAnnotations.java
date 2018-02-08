package edu.isi.bmkeg.uimaBioC.uima.out;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.uimafit.component.JCasAnnotator_ImplBase;
import org.uimafit.descriptor.ConfigurationParameter;
import org.uimafit.factory.ConfigurationParameterFactory;
import org.uimafit.util.JCasUtil;

import com.google.gson.Gson;

import bioc.BioCAnnotation;
import bioc.BioCCollection;
import bioc.BioCDocument;
import bioc.BioCLocation;
import bioc.BioCPassage;
import bioc.esViews.BioCAnnotation.BioCAnnotation__BioCAnnotation;
import bioc.esViews.BioCAnnotation.BioCAnnotation__BioCDocument;
import bioc.esViews.BioCAnnotation.BioCAnnotation__BioCLocation;
import bioc.esViews.BioCAnnotation.BioCAnnotation__BioCPassage;
import bioc.type.UimaBioCDocument;
import edu.isi.bmkeg.uimaBioC.UimaBioCUtils;

public class SaveAsBioCAnnotations extends JCasAnnotator_ImplBase {

	public final static String PARAM_FILE_PATH = ConfigurationParameterFactory
			.createConfigurationParameterName(SaveAsBioCAnnotations.class,
					"outFilePath");
	@ConfigurationParameter(mandatory = true, description = "The place to put the document files to be classified.")
	String outFilePath;
	private File outFile;

	public static String XML = ".xml";
	public static String JSON = ".json";
	public final static String PARAM_FORMAT = ConfigurationParameterFactory
			.createConfigurationParameterName(SaveAsBioCAnnotations.class,
					"outFileFormat");
	@ConfigurationParameter(mandatory = true, description = "The format of the output.")
	String outFileFormat;

	private BioCCollection collection;
	private Map<String,String> json_lines;

	public void initialize(UimaContext context)
			throws ResourceInitializationException {

		super.initialize(context);

		this.outFilePath = (String) context
				.getConfigParameterValue(PARAM_FILE_PATH);
		this.outFile= new File(this.outFilePath);

		this.collection = new BioCCollection();
		this.json_lines = new HashMap<String,String>();

	}

	public void process(JCas jCas) throws AnalysisEngineProcessException {

		for (UimaBioCDocument uiD : JCasUtil.select(jCas,
				UimaBioCDocument.class)) {
			
			try {
				
				if( uiD.getId().equals("skip") ){
					continue;					
				}
					
				BioCCollection c = new BioCCollection();
				BioCDocument d = UimaBioCUtils.convertUimaBioCDocument(uiD, jCas);
				BioCAnnotation__BioCDocument dd = new BioCAnnotation__BioCDocument();
				dd.setId( d.getID() );
				dd.setInfons(d.getInfons());
				
				for( BioCPassage p : d.getPassages() ){
					
					BioCAnnotation__BioCPassage pp = new BioCAnnotation__BioCPassage();
					pp.setInfons(p.getInfons());
					pp.setOffset(p.getOffset());
					//pp.setText(p.getText());
					pp.setDocument(dd);
					
					for( BioCAnnotation a : p.getAnnotations()){
						List<BioCAnnotation__BioCLocation> llist = new ArrayList<BioCAnnotation__BioCLocation>();
						for( BioCLocation l : a.getLocations()){
							llist.add(new BioCAnnotation__BioCLocation(l.getOffset(), l.getLength()));
						}		
						BioCAnnotation__BioCAnnotation aa = new BioCAnnotation__BioCAnnotation();
						aa.setId(a.getID());
						aa.setText(a.getText());
						aa.setInfons(a.getInfons());
						aa.setLocations(llist);
						aa.setPassage(pp);

						Gson gson = new Gson();
						json_lines.put(d.getID(), gson.toJson(d));					

					}					
				}
				
				String relPath = d.getInfon("relative-source-path").replaceAll("\\.txt", "") 
						+ outFileFormat;
				File outFile = new File(outFilePath + "/" + relPath);
				if( !outFile.getParentFile().exists() ) {
					outFile.getParentFile().mkdirs();
				}
				
				if( outFile.exists() )
					outFile.delete();

			} catch (Exception e) {

				throw new AnalysisEngineProcessException(e);

			}			
			
		}

	}

	public void collectionProcessComplete() throws AnalysisEngineProcessException {

		PrintWriter out = null;
		try {
			
			out = new PrintWriter(new BufferedWriter(
					new FileWriter(outFile, true)));
			for( String key : this.json_lines.keySet() ) {
				out.write(key + ":\t" + this.json_lines);
			}
			out.close();			

		} catch (IOException e) {
			e.printStackTrace();
			throw new AnalysisEngineProcessException(e);
		}
		
	}
	
	

	
}
