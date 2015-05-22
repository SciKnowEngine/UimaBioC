package edu.isi.bmkeg.uimaBioC.uima.readers;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.apache.uima.UimaContext;
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

import bioc.type.UimaBioCAnnotation;
import bioc.type.UimaBioCDocument;
import bioc.type.UimaBioCLocation;
import bioc.type.UimaBioCPassage;
import edu.isi.bmkeg.uimaBioC.UimaBioCUtils;

/**
 * We want to optimize this interaction for speed, so we run a
 * manual query over the underlying database involving a minimal subset of
 * tables.
 * 
 * @author burns
 * 
 */
public class TxtFilesCollectionReader extends JCasCollectionReader_ImplBase {
	
	private Iterator<File> txtFileIt; 
	
	private int pos = 0;
	private int count = 0;
	
	private static Logger logger = Logger.getLogger(TxtFilesCollectionReader.class);
	
	public static final String PARAM_INPUT_DIRECTORY = ConfigurationParameterFactory
			.createConfigurationParameterName(TxtFilesCollectionReader.class,
					"inputDirectory");
	@ConfigurationParameter(mandatory = true, description = "Input Directory for Txt Files")
	protected String inputDirectory;
	
	@Override
	public void initialize(UimaContext context) throws ResourceInitializationException {

		try {

			String[] fileTypes = {"txt"};
			
			Collection<File> l = (Collection<File>) FileUtils.listFiles(new File(inputDirectory), fileTypes, true);
			this.txtFileIt = l.iterator();
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
			
			if(!txtFileIt.hasNext()) 
				return;
			
			File txtFile = txtFileIt.next();

			String txt = FileUtils.readFileToString(txtFile);
			jcas.setDocumentText( txt );

			//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
			UimaBioCDocument uiD = new UimaBioCDocument(jcas);
			
			Map<String,String> infons = new HashMap<String,String>();
			infons.put("relative-source-path", txtFile.getPath().replaceAll(inputDirectory + "/", ""));
			uiD.setInfons(UimaBioCUtils.convertInfons(infons, jcas));
			
			uiD.setBegin(0);
			uiD.setEnd(txt.length());
						
			uiD.addToIndexes();
			int passageCount = 0;
			int nSkip = 0;
		
			pos++;
		    if( (pos % 1000) == 0) {
		    	System.out.println("Processing " + pos + "th document.");
		    }
		    
		} catch (Exception e) {
			
			System.err.print(this.pos + "/" + this.count);
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
		return txtFileIt.hasNext();
	}

}
