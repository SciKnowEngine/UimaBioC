package edu.isi.bmkeg.uimaBioC.uima.readers;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

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
public class Nxml2TxtFilesCollectionReader extends JCasCollectionReader_ImplBase {
	
	private class Ref {
		private String author;
		private String pmid;
		private String ref;
		private String source;
		private String title;
	}
	
	private Iterator<File> txtFileIt; 
	private File txtFile;
	private File soFile;
	private Pattern patt;
	
	private int pos = 0;
	private int count = 0;
	
	private static Logger logger = Logger.getLogger(Nxml2TxtFilesCollectionReader.class);
	
	public static final String PARAM_INPUT_DIRECTORY = ConfigurationParameterFactory
			.createConfigurationParameterName(Nxml2TxtFilesCollectionReader.class,
					"inputDirectory");
	@ConfigurationParameter(mandatory = true, description = "Input Directory for Nxml2Txt Files")
	protected String inputDirectory;

	public static final String PARAM_OUTPUT_DIRECTORY = ConfigurationParameterFactory
			.createConfigurationParameterName(Nxml2TxtFilesCollectionReader.class,
					"outputDirectory");
	@ConfigurationParameter(mandatory = false, description = "Output Directory for Nxml2Txt Files")
	protected String outputDirectory;
	
	public static final String PARAM_OUTPUT_TYPE = ConfigurationParameterFactory
			.createConfigurationParameterName(Nxml2TxtFilesCollectionReader.class,
					"outputType");
	@ConfigurationParameter(mandatory = false, description = "Output File Suffix")
	protected String outputType;
	
	/**
	 * These files are generated from the robot_biocurator python library and have records like this:
	 *    "bib1": {
     * 	        "author": "Baltensperger K, Kozma LM, Cherniack AD, Klarlund JK, Chawla A, Banerjee U, Czech MP", 
     *          "pmid": "8391166", 
     *          "ref": "bib1", 
     *          "source": "Science. 1993 Jun 25; 260(5116):1950-2.", 
     *          "title": "Binding of the Ras activator son of sevenless to insulin receptor substrate-1 signaling complexes."
     *    }, 
	 * 
	 *  We read this and attach this information to any external references read by the model.
	 */
	public static final String PARAM_REF_DIRECTORY = ConfigurationParameterFactory
			.createConfigurationParameterName(Nxml2TxtFilesCollectionReader.class,
					"referenceFileDirectory");
	@ConfigurationParameter(mandatory = false, description = "Input Directory for .ref.json Files")
	protected String referenceFileDirectory = "";

	private Pattern firstLinePatt = Pattern.compile("^(.*)\\n");
	
	@Override
	public void initialize(UimaContext context) throws ResourceInitializationException {

		try {

			String[] fileTypes = {"txt"};
			
			Collection<File> l = (Collection<File>) FileUtils.listFiles(new File(inputDirectory), fileTypes, true);
			this.txtFileIt = l.iterator();
			this.count = l.size();
			this.patt = Pattern.compile("(\\d+)\\.txt");
			
		} catch (Exception e) {

			throw new ResourceInitializationException(e);

		}

	}

	/**
	 * @see com.ibm.uima.collection.CollectionReader#getNext(com.ibm.uima.cas.CAS)
	 */
	public void getNext(JCas jcas) throws IOException, CollectionException {

		try {
						
			UimaBioCAnnotation articleTitle = null;

			String txt = FileUtils.readFileToString(txtFile);
			jcas.setDocumentText( txt );

			//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
			UimaBioCDocument uiD = new UimaBioCDocument(jcas);

			String fileStem = this.txtFile.getName().substring(0,this.txtFile.getName().lastIndexOf("."));

			Map<String,Ref> refLookup = null;
			if( this.referenceFileDirectory.length() > 0 ) {
				File referenceFile = new File(this.referenceFileDirectory + "/" + fileStem + ".refs.json");
				if( referenceFile.exists() ) {
					Gson gson = new Gson();
					Type collectionType = new TypeToken<Map<String,Ref>>(){}.getType();
					refLookup = gson.fromJson(new FileReader(referenceFile), collectionType);
				} 
			}
			
			Map<String,String> infons = new HashMap<String,String>();
			infons.put("relative-source-path", txtFile.getPath().replaceAll(inputDirectory + "/", ""));
			uiD.setInfons(UimaBioCUtils.convertInfons(infons, jcas));
			
			uiD.setBegin(0);
			uiD.setEnd(txt.length());
						
			
			Matcher m = this.patt.matcher(this.txtFile.getName());
			if(m.find())
				uiD.setId(m.group(1));
			
			int passageCount = 0;
			int nSkip = 0;
			
			//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
			// Note that in these systems, we will create a single passage of the
			// entire document and then create general annotations for formatting 
			// on top of that, other sections such as introduction, abstract, etc.
			// will be placed into other passages but no annotations directly 
			// placed on them except for purposes of delineating the sections. 
			//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
			UimaBioCPassage uiP = new UimaBioCPassage(jcas);
			int annotationCount = 0;
			uiP.setBegin(0);
			uiP.setEnd(txt.length());
			uiP.setOffset(0);
			passageCount++;
			
			infons = new HashMap<String, String>();
			infons.put("type", "document");
			uiP.setInfons(UimaBioCUtils.convertInfons(infons, jcas));
			uiP.addToIndexes();
			//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
			
			BufferedReader in = new BufferedReader(new InputStreamReader(
					new FileInputStream(soFile)));
			String line;
			while ((line = in.readLine()) != null) {
			
				String[] fields = line.split("\t");
				
				if( fields.length < 3 ) {
					continue;		
				}
				
				String id = fields[0];

				String typeOffsetStr = fields[1];
				String[] typeOffsetArray = typeOffsetStr.split(" ");
				String type = typeOffsetArray[0];
				int begin = new Integer(typeOffsetArray[1]);
				int end = new Integer(typeOffsetArray[2]);
								
				String str = "";
				if( fields.length > 2 ) 
					str = fields[2];
				
				String codes = "";
				if( fields.length > 3 ) 
					codes = fields[3];
				
				// Just run through the data and assert the pieces to the jcas

				// High level sections, none of these have types of extra data.
				if( type.equals("front") || 
						type.equals("abstract") ||  
						type.equals("body") ||  
						type.equals("ref-list")){					
					//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
					UimaBioCAnnotation uiA = new UimaBioCAnnotation(jcas);
					uiA.setBegin(begin);
					uiA.setEnd(end);
					Map<String,String> infons2 = new HashMap<String, String>();
					infons2.put("type", "formatting");
					infons2.put("value", type);
					uiA.setInfons(UimaBioCUtils.convertInfons(infons2, jcas));
					uiA.addToIndexes();
					
					FSArray locations = new FSArray(jcas, 1);
					uiA.setLocations(locations);
					UimaBioCLocation uiL = new UimaBioCLocation(jcas);
					locations.set(0, uiL);
					uiL.setOffset(begin);
					uiL.setLength(end - begin);
					//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
				}
				
				// Paragraphs, titles, article-titles, abstracts, figure labels and captions. 
				if( type.equals("p") || 
						type.equals("title") || 
						type.equals("label") || 
						type.equals("caption") ){					
					//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
					UimaBioCAnnotation uiA = new UimaBioCAnnotation(jcas);
					uiA.setBegin(begin);
					uiA.setEnd(end);
					Map<String,String> infons2 = new HashMap<String, String>();
					infons2.put("type", "formatting");
					infons2.put("value", type);
					uiA.setInfons(UimaBioCUtils.convertInfons(infons2, jcas));
					uiA.addToIndexes();
					
					FSArray locations = new FSArray(jcas, 1);
					uiA.setLocations(locations);
					UimaBioCLocation uiL = new UimaBioCLocation(jcas);
					locations.set(0, uiL);
					uiL.setOffset(begin);
					uiL.setLength(end - begin);
					//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
				}
				
				// Only accept the first article title text
				if( type.equals("article-title") && articleTitle  == null ){					
					//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
					UimaBioCAnnotation uiA = new UimaBioCAnnotation(jcas);
					uiA.setBegin(begin);
					uiA.setEnd(end);
					Map<String,String> infons2 = new HashMap<String, String>();
					infons2.put("type", "formatting");
					infons2.put("value", type);
					uiA.setInfons(UimaBioCUtils.convertInfons(infons2, jcas));
					uiA.addToIndexes();
					
					FSArray locations = new FSArray(jcas, 1);
					uiA.setLocations(locations);
					UimaBioCLocation uiL = new UimaBioCLocation(jcas);
					locations.set(0, uiL);
					uiL.setOffset(begin);
					uiL.setLength(end - begin);
					articleTitle = uiA;
					//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
				}

				//
				// Detecting and preserving floating boxes from the text but only read text from 
				// labels and captions from within them.
				//
				if( type.equals("fig") ||
						type.equals("supplementary-material") || 
						type.equals("table-wrap") ){					
					//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
					UimaBioCAnnotation uiA = new UimaBioCAnnotation(jcas);
					uiA.setBegin(begin);
					uiA.setEnd(end);
					Map<String,String> infons2 = new HashMap<String, String>();
					infons2.put("type", "formatting");
					infons2.put("value", type);
					
					// Check for floating figure legends
					if( fields.length > 3 ) {
						String[] subfields = fields[3].split("\\s+");
						for( String s : subfields ){
							if( s.equals("position=\"float\"") ) {
								infons2.put("position", "float");			
							}
						}
					}
					uiA.setInfons(UimaBioCUtils.convertInfons(infons2, jcas));
					uiA.addToIndexes();
					
					FSArray locations = new FSArray(jcas, 1);
					uiA.setLocations(locations);
					UimaBioCLocation uiL = new UimaBioCLocation(jcas);
					locations.set(0, uiL);
					uiL.setOffset(begin);
					uiL.setLength(end - begin);
					//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
				}

				// Section Headings
				if( type.equals("sec") ){					
					//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
					String subText = txt.substring(begin, end);
					Matcher firstLineMatch = firstLinePatt.matcher(subText);
					if( !firstLineMatch.find() ) 
						continue;
					String sectionHeading = firstLineMatch.group(1);
					
					UimaBioCAnnotation uiA = new UimaBioCAnnotation(jcas);
					uiA.setBegin(begin);
					uiA.setEnd(end);
					Map<String,String> infons2 = new HashMap<String, String>();
					infons2.put("type", "formatting");
					infons2.put("value", "sec");
					infons2.put("sectionHeading", sectionHeading);
					uiA.setInfons(UimaBioCUtils.convertInfons(infons2, jcas));
					uiA.addToIndexes();
					
					FSArray locations = new FSArray(jcas, 1);
					uiA.setLocations(locations);
					UimaBioCLocation uiL = new UimaBioCLocation(jcas);
					locations.set(0, uiL);
					uiL.setOffset(begin);
					uiL.setLength(end - begin);
					//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
				}
				
				// Formatting annotations.
				if( type.equals("bold") || 
						type.equals("italic") ||  
						type.equals("sub") ||  
						type.equals("sup") ){					
					//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
					UimaBioCAnnotation uiA = new UimaBioCAnnotation(jcas);
					uiA.setBegin(begin);
					uiA.setEnd(end);
					Map<String,String> infons2 = new HashMap<String, String>();
					infons2.put("type", "formatting");
					infons2.put("value", type);
					uiA.setInfons(UimaBioCUtils.convertInfons(infons2, jcas));
					uiA.addToIndexes();
					
					FSArray locations = new FSArray(jcas, 1);
					uiA.setLocations(locations);
					UimaBioCLocation uiL = new UimaBioCLocation(jcas);
					locations.set(0, uiL);
					uiL.setOffset(begin);
					uiL.setLength(end - begin);
					//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
				}
				
				// Id values for the BioCDocument.
				if( type.equals("article-id") ){					
					
					infons = UimaBioCUtils.convertInfons(uiD.getInfons());
		
					// strip all 'quotes' from id values.
					codes = codes.replaceAll("\"", "");
		
					String[] keyValue = codes.split("=");
					if( keyValue.length < 2)
						continue;
					
					infons.put(keyValue[1], str);
					infons.put("type", "formatting");
					infons.put("value", "article-id");
					uiD.setInfons(
							UimaBioCUtils.convertInfons(infons, jcas)
							);
					if( keyValue[1].contains("pmid")){
						uiD.setId(str);
					}
					//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
				
				}
				 
				// X-REF Columns
				// format: ref-type="bibr" rid="B7"
				if( type.equals("xref") ){		
										
					String refType = "";
					String refId = "";
					try {
						String[] lc = codes.split(" ");
						refType = lc[0].substring(lc[0].indexOf("=")+2, lc[0].length()-1);
						if( lc.length > 1 )
							refId = lc[1].substring(lc[1].indexOf("=")+2, lc[1].length()-1);					
					} catch (ArrayIndexOutOfBoundsException e) {
						System.err.println("XREF not formatted correctly (" + codes + "), skipping XREF annotation");
					} catch (java.lang.StringIndexOutOfBoundsException e) {
						System.err.println("XREF not formatted correctly (" + codes + "), skipping XREF annotation");
					}
					
					//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
					if( refType.length() > 0 && refId.length() > 0 && 
							( refType.startsWith("bib") || refType.equals("fig") || 
								refType.equals("supplementary-material") ) 
							) {
						UimaBioCAnnotation uiA = new UimaBioCAnnotation(jcas);
						uiA.setBegin(begin);
						uiA.setEnd(end);
						Map<String,String> infons2 = new HashMap<String, String>();
						infons2.put("type", "formatting");
						infons2.put("value", type);
						infons2.put("refType", refType);
						infons2.put("refId", refId);
						
						if(refLookup!= null && refLookup.containsKey(refId)) {
							Ref ref = refLookup.get(refId);
							infons2.put("pmid", ref.pmid);							
						} 
						
						uiA.setInfons(UimaBioCUtils.convertInfons(infons2, jcas));
						uiA.addToIndexes();
						
						FSArray locations = new FSArray(jcas, 1);
						uiA.setLocations(locations);
						UimaBioCLocation uiL = new UimaBioCLocation(jcas);
						locations.set(0, uiL);
						uiL.setOffset(begin);
						uiL.setLength(end - begin);					
					}
					//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
				
				}
				
			}
				
			// At present, if a paper is not provided with a PubMed ID, then we skip it. 
			if( uiD.getId() == null )
				uiD.setId("skip");
			
			uiD.addToIndexes();
					    
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
		
		if( !txtFileIt.hasNext() )
			return false;
		
		this.txtFile = moveFileIteratorForwardOneStep();
		this.soFile = new File(txtFile.getPath().replaceAll("\\.txt$", ".so"));
		
		if( this.outputDirectory != null && this.outputType != null ){	
			String newPath = soFile.getPath().replaceAll(
					this.inputDirectory,
					this.outputDirectory);
			File targetFile = new File(newPath.replaceAll("\\.so$",  "."+this.outputType));
			while( targetFile.exists()  ) {
				if(!txtFileIt.hasNext()) 
					return false;
				pos++;
				txtFile = moveFileIteratorForwardOneStep();
				soFile = new File(txtFile.getPath().replaceAll("\\.txt$", ".so"));
				targetFile = new File(txtFile.getPath().replaceAll("\\.txt$", "."+this.outputType));
			}

		}			
				
		while( !txtFile.exists() || !soFile.exists() ) {
			if(!txtFileIt.hasNext()) 
				return false;
			pos++;
			txtFile = moveFileIteratorForwardOneStep();
			soFile = new File(txtFile.getPath().replaceAll("\\.txt$", ".so"));
		
		}
		
		return true;
				
	}

	private File moveFileIteratorForwardOneStep() {
		pos++;
		if( (pos % 1000) == 0) {
	    	System.out.println("Processing " + pos + "th document.");
	    }
		return txtFileIt.next();
	}

}
