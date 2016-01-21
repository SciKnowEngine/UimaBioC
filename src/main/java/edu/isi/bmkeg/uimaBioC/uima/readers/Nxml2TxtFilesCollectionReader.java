package edu.isi.bmkeg.uimaBioC.uima.readers;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
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
	
	private Iterator<File> txtFileIt; 
	
	private int pos = 0;
	private int count = 0;
	
	private static Logger logger = Logger.getLogger(Nxml2TxtFilesCollectionReader.class);
	
	public static final String PARAM_INPUT_DIRECTORY = ConfigurationParameterFactory
			.createConfigurationParameterName(Nxml2TxtFilesCollectionReader.class,
					"inputDirectory");
	@ConfigurationParameter(mandatory = true, description = "Input Directory for Nxml2Txt Files")
	protected String inputDirectory;
	
	private Pattern firstLinePatt = Pattern.compile("^(.*)\\n");
	
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
			File soFile = new File(txtFile.getPath().replaceAll("\\.txt$", ".so"));

			while( !txtFile.exists() || !soFile.exists() ) {

				if(!txtFileIt.hasNext()) 
					return;
				txtFile = txtFileIt.next();
				soFile = new File(txtFile.getPath().replaceAll("\\.txt$", ".so"));
			
			}

			String txt = FileUtils.readFileToString(txtFile);
			jcas.setDocumentText( txt );

			//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
			UimaBioCDocument uiD = new UimaBioCDocument(jcas);
			
			Map<String,String> infons = new HashMap<String,String>();
			infons.put("relative-source-path", txtFile.getPath().replaceAll(inputDirectory + "/", ""));
			uiD.setInfons(UimaBioCUtils.convertInfons(infons, jcas));
			
			uiD.setBegin(0);
			uiD.setEnd(txt.length());
						
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
				
				// Figure Captions + paragraphs
				if( type.equals("p") || type.equals("title")){					
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
				
				if( type.equals("fig") ){					
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
						refId = lc[1].substring(lc[1].indexOf("=")+2, lc[1].length()-1);					
					} catch (ArrayIndexOutOfBoundsException e) {
						System.err.println("XREF not formatted correctly (" + codes + "), skipping XREF annotation");
					}
					
					//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
					if( refType.length() > 0 && refId.length() > 0 && 
							( refType.equals("bibr") || refType.equals("fig") ) 
							) {
						UimaBioCAnnotation uiA = new UimaBioCAnnotation(jcas);
						uiA.setBegin(begin);
						uiA.setEnd(end);
						Map<String,String> infons2 = new HashMap<String, String>();
						infons2.put("type", "formatting");
						infons2.put("value", type);
						infons2.put("refType", refType);
						infons2.put("refId", refId);
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
		
			uiD.addToIndexes();
			
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
