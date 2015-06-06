package edu.isi.bmkeg.uimaBioC.uima.readers;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.collection.CollectionException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.FSArray;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.Progress;
import org.apache.uima.util.ProgressImpl;
import org.uimafit.component.JCasCollectionReader_ImplBase;
import org.uimafit.descriptor.ConfigurationParameter;
import org.uimafit.factory.ConfigurationParameterFactory;
import org.uimafit.util.JCasUtil;

import bioc.BioCDocument;
import bioc.io.BioCDocumentReader;
import bioc.io.BioCFactory;
import bioc.type.UimaBioCAnnotation;
import bioc.type.UimaBioCDocument;
import bioc.type.UimaBioCLocation;
import bioc.type.UimaBioCPassage;

import com.google.gson.Gson;

import edu.isi.bmkeg.uimaBioC.UimaBioCUtils;
import edu.isi.bmkeg.uimaBioC.uima.out.SaveAsBioCDocuments;

/**
 * We want to optimize this interaction for speed, so we run a
 * manual query over the underlying database involving a minimal subset of
 * tables.
 * 
 * @author burns
 * 
 */
public class BioCCollectionReader extends JCasCollectionReader_ImplBase {
	
	private Iterator<File> bioCFileIt; 
	
	private int pos = 0;
	private int count = 0;
	
	private static Logger logger = Logger.getLogger(BioCCollectionReader.class);
	
	public static final String INPUT_DIRECTORY = ConfigurationParameterFactory
			.createConfigurationParameterName(BioCCollectionReader.class,
					"inputDirectory");
	@ConfigurationParameter(mandatory = true, description = "Input Directory for BioC Files")
	protected String inputDirectory;

	public static String XML = "xml";
	public static String JSON = "json";
	public final static String PARAM_FORMAT = ConfigurationParameterFactory
			.createConfigurationParameterName(BioCCollectionReader.class,
					"inFileFormat");
	@ConfigurationParameter(mandatory = true, description = "The format of the BioC input files.")
	String inFileFormat;
	
	@Override
	public void initialize(UimaContext context) throws ResourceInitializationException {

		try {
			
			String[] fileTypes = {this.inFileFormat};
			Collection<File> l = (Collection<File>) FileUtils.listFiles(new File(inputDirectory), fileTypes, true);
			this.bioCFileIt = l.iterator();
			this.count = l.size();
			
		} catch (Exception e) {

			throw new ResourceInitializationException(e);

		}

	}

	/**
	 * @see com.ibm.uima.collection.CollectionReader#getNext(com.ibm.uima.cas.CAS)
	 */
	public void getNext(JCas jcas) throws IOException, CollectionException {

		try {
			
			if(!bioCFileIt.hasNext()) 
				return;
			
			File bioCFile = bioCFileIt.next();
			while( !bioCFile.exists()  ) {
				if(!bioCFileIt.hasNext()) 
					return;
				bioCFile = bioCFileIt.next();
			}

			BioCDocument bioD;
			if (inFileFormat.equals(XML)) {
				
				BioCDocumentReader reader = BioCFactory.newFactory(
						BioCFactory.STANDARD).createBioCDocumentReader(
								new FileReader(bioCFile));

				bioD = reader.readDocument();

				reader.close();

			} else if (inFileFormat.equals(JSON)) {

				Gson gson = new Gson();
				Class<String> classOfT;
				bioD = gson.fromJson(new FileReader(bioCFile), BioCDocument.class);
				
			} else {
				
				throw new AnalysisEngineProcessException(
						new Exception("Please write to an *.xml or a *.json file")
						);
			
			}
			
			//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
			UimaBioCDocument uiD = UimaBioCUtils.convertBioCDocument(bioD, jcas);
			
			FSArray passages = uiD.getPassages();
			if (passages != null) {
				for (int i = 0; i < passages.size(); i++) {
					UimaBioCPassage uiP = (UimaBioCPassage) passages.get(i);
					
					Map<String, String> infons = UimaBioCUtils.convertInfons(uiP.getInfons());
					if( infons.containsKey("type") && 
							infons.get("type").equals("document") ) {
						jcas.setDocumentText( uiP.getText() );
					}
				
				}
			}
						
		    pos++;
		    if( (pos % 1000) == 0) {
		    	System.out.println("Processing " + pos + "th document.");
		    }
		    
		} catch (Exception e) {
			
			System.err.print(this.count);
			throw new CollectionException(e);

		}

	}
		
	protected void error(String message) {
		logger.error(message);
	}

	@SuppressWarnings("unused")
	protected void warn(String message) {
		logger.warn(message);
	}

	@SuppressWarnings("unused")
	protected void debug(String message) {
		logger.error(message);
	}

	public Progress[] getProgress() {		
		Progress progress = new ProgressImpl(
				this.pos, 
				this.count, 
				Progress.ENTITIES);
		
        return new Progress[] { progress };
	}

	@Override
	public boolean hasNext() throws IOException, CollectionException {
		return bioCFileIt.hasNext();
	}

}
