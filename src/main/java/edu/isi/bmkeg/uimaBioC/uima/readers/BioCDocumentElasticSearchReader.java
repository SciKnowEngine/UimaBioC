package edu.isi.bmkeg.uimaBioC.uima.readers;

import java.io.IOException;
import java.util.Iterator;

import org.apache.log4j.Logger;
import org.apache.uima.UimaContext;
import org.apache.uima.collection.CollectionException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.Progress;
import org.apache.uima.util.ProgressImpl;
import org.uimafit.component.JCasCollectionReader_ImplBase;

import bioc.BioCDocument;
import bioc.esViews.BioCDocumentView.BioCDocumentView__BioCDocument;
import edu.isi.bmkeg.uimaBioC.UimaBioCUtils;
import edu.isi.bmkeg.uimaBioC.elasticSearch.BioCRepository;
import edu.isi.bmkeg.utils.ViewConverter;

/**
 * We want to optimize this interaction for speed, so we run a
 * manual query over the underlying database involving a minimal subset of
 * tables.
 * 
 * @author burns
 * 
 */
public class BioCDocumentElasticSearchReader extends JCasCollectionReader_ImplBase {
	
	private static Logger logger = Logger.getLogger(BioCDocumentElasticSearchReader.class);
	
	public final static String BIOC_ES_REPO = "bioCRepository";
	
	@org.uimafit.descriptor.ExternalResource(key = BIOC_ES_REPO)
	BioCRepository biocRepo;
	
	private Iterator<BioCDocumentView__BioCDocument> biocDocIt;
	
	private long pos = 0;
	private long count = 0;
	
	@Override
	public void initialize(UimaContext context) throws ResourceInitializationException {

		try {
						
			this.count = this.biocRepo.count();
			Iterator<BioCDocumentView__BioCDocument> biocDocIt = this.biocRepo.findAll().iterator();
			
		} catch (Exception e) {

			e.printStackTrace();
			throw new ResourceInitializationException(e);

		}

	}

	/**
	 * @see com.ibm.uima.collection.CollectionReader#getNext(com.ibm.uima.cas.CAS)
	 */
	public void getNext(JCas jcas) throws IOException, CollectionException {

		try {
			
			if(!biocDocIt.hasNext()) 
				return;
			
			BioCDocumentView__BioCDocument bioCDocView = biocDocIt.next();
			BioCDocument bioCDoc = new BioCDocument();
			
			ViewConverter vc = new ViewConverter(bioCDocView);
			bioCDoc = (BioCDocument) vc.viewObjectToBase(bioCDocView, bioCDoc);
			
			UimaBioCUtils.addBioCDocumentToUimaCas(bioCDoc, jcas);
						
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
				(int) this.pos, 
				(int) this.count, 
				Progress.ENTITIES);
		
        return new Progress[] { progress };
	}

	@Override
	public boolean hasNext() throws IOException, CollectionException {
		return biocDocIt.hasNext();
	}

}
