package edu.isi.bmkeg.uimaBioC.uima.out;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

import bioc.BioCCollection;
import bioc.type.UimaBioCAnnotation;
import bioc.type.UimaBioCDocument;
import bioc.type.UimaBioCPassage;
import edu.isi.bmkeg.uimaBioC.UimaBioCUtils;

public class SaveExtractedAnnotations extends JCasAnnotator_ImplBase {

	public final static String PARAM_ANNOT_2_EXTRACT = ConfigurationParameterFactory
			.createConfigurationParameterName(
					SaveExtractedAnnotations.class, "annot2Extract");
	@ConfigurationParameter(mandatory = true, description = "The section heading *pattern* to extract")
	String annot2Extract;
	private Pattern patt;

	public final static String PARAM_DIR_PATH = ConfigurationParameterFactory
			.createConfigurationParameterName(
					SaveExtractedAnnotations.class, "outDirPath");
	@ConfigurationParameter(mandatory = true, description = "The path to the output directory.")
	String outDirPath;

	public final static String PARAM_HEADER_LINKS = ConfigurationParameterFactory
			.createConfigurationParameterName(
					SaveExtractedAnnotations.class, "headerLinksStr");
	@ConfigurationParameter(mandatory = true, description = "Should we include headers and links in the output.")
	String headerLinksStr;
	Boolean headerLinks = false;

	public final static String PARAM_KEEP_FLOATING_BOXES = ConfigurationParameterFactory
			.createConfigurationParameterName(
					SaveExtractedAnnotations.class, "keepFloatsStr");
	@ConfigurationParameter(mandatory = true, description = "Should we include floating boxes in the output.")
	String keepFloatsStr;
	Boolean keepFloats = false;

	public final static String PARAM_ADD_FRIES_CODES = ConfigurationParameterFactory
			.createConfigurationParameterName(
					SaveExtractedAnnotations.class, "addFriesStr");
	@ConfigurationParameter(mandatory = false, description = "Should we add FRIES output?")
	String addFriesStr;
	Boolean addFries = false;
	
	public final static String PARAM_SKIP_BODY = ConfigurationParameterFactory
			.createConfigurationParameterName(
					SaveExtractedAnnotations.class, "skipBodyStr");
	@ConfigurationParameter(mandatory = false, description = "Should we skip output that doesn't match patterns.")
	String skipBodyStr;
	Boolean skipBody = false;

	
	private File outDir;
	private BioCCollection collection;

	private List<Pattern>figPatt = new ArrayList<Pattern>();
	private List<Pattern>figsPatt = new ArrayList<Pattern>();

	Map<String,Map<String,Integer>> table = new HashMap<String, Map<String, Integer>>();
	
	public void initialize(UimaContext context)
			throws ResourceInitializationException {

		super.initialize(context);

		this.outDirPath = (String) context
				.getConfigParameterValue(PARAM_DIR_PATH);
		this.outDir = new File(this.outDirPath);

		if( !this.outDir.exists() )
			this.outDir.mkdirs();
		
		if(this.headerLinksStr.toLowerCase().equals("true") ) {
			headerLinks = true;
		} else if(this.headerLinksStr.toLowerCase().equals("false") ) {
			headerLinks = false;
		} else {
			Exception e = new Exception("Please set PARAM_HEADER_LINKS to 'true' or 'false'");
			throw new ResourceInitializationException(e);
		}		

		if(this.keepFloatsStr.toLowerCase().equals("true") ) {
			keepFloats = true;
		} else if(this.keepFloatsStr.toLowerCase().equals("false") ) {
			keepFloats = false;
		} else {
			keepFloats = false;
			Exception e = new Exception("Please set PARAM_ADD_FRIES_CODES to 'true' or 'false'");
			throw new ResourceInitializationException(e);
		}		

		if(this.addFriesStr != null && this.addFriesStr.toLowerCase().equals("true") ) {
			addFries = true;
		} else {
			addFries = false;
		}	
		
		if(this.skipBodyStr != null && this.skipBodyStr.toLowerCase().equals("true") ) {
			skipBody = true;
		} else {
			skipBody = false;
		}		


		this.collection = new BioCCollection();
		
		this.patt = Pattern.compile(this.annot2Extract);
		
		//
		// A List of regular expressions to recognize 
		// all figure legend codes appearing in text.
		//
		String b = "\\s*[Ff]ig(ure|.){0,1}";
		String e = "\\s*(\\)|\\w{2,}|\\p{Punct})";
		//
		// 0. No alphanumeric codes at all
		this.figPatt.add( 
				Pattern.compile(b+"\\s*(\\d+)\\s*\\p{Punct}*\\s*\\w{2,}") 
				);
		//
		// 1. Delineated by brackets
		this.figPatt.add( 
				Pattern.compile("\\("+b+"\\s*(\\d+.*?)\\)") 
				);
		//
		// 2. Simple single alphanumeric codes, followed by punctuation.
		this.figPatt.add( 
				Pattern.compile(b+"\\s*(\\d+\\s*[A-Za-z])\\p{Punct}") 
				);
		
		//
		// 3. Single Alphanumeric codes, followed by words.
		this.figPatt.add( 
				Pattern.compile(b+"\\s*(\\d+\\s*[A-Za-z])\\s+\\w{2,}") 
				);

		//
		// 4. Fig 8, a and b). 
		this.figPatt.add( 
				Pattern.compile(b+"\\s*(\\d[\\s\\p{Punct}]*[A-Za-z] and [A-Za-z])"+e) 
				);

		//
		// 5. Fig. 3, a-c). 
		this.figPatt.add( 
				Pattern.compile(b+"\\s*(\\d[\\s\\p{Punct}]*[A-Z\\-a-z])"+e) 
				);

		//
		// 6. Fig. c). 
		this.figPatt.add( 
				Pattern.compile(b+"\\s*([\\s\\p{Punct}]*[A-Z\\-a-z])"+e) 
				);
		
		// Multiple Figures in sequence
		b = "\\s*[Ff]ig(ures|s.{0,1}|.){0,1}";
		// 1. Fig. c). 
		this.figsPatt.add( 
				Pattern.compile(b+"\\s*([\\s\\p{Punct}]*[A-Z\\-a-z])"+e) 
				);
	}

	public void process(JCas jCas) throws AnalysisEngineProcessException {

		UimaBioCDocument uiD = JCasUtil.selectSingle(jCas,
				UimaBioCDocument.class);

		String id = uiD.getId();
		
		boolean anyTextExtracted = false;
		
		Set<UimaBioCAnnotation> selectedAnnotations = new HashSet<UimaBioCAnnotation>();
		List<UimaBioCAnnotation> outerAnnotations = JCasUtil.selectCovered(
				UimaBioCAnnotation.class, uiD);
		for (UimaBioCAnnotation uiA1 : outerAnnotations) {
			
			Map<String, String> inf = UimaBioCUtils.convertInfons(uiA1.getInfons());
			if( !inf.containsKey("type")  ) 
				continue;

			if( !(inf.get("type").equals("formatting") &&
					inf.get("value").equals("sec")) ) {
				continue;
			}
			
			Matcher match = this.patt.matcher(inf.get("sectionHeading"));
			if( !match.find() ) {
				continue;
			}

			selectedAnnotations.add(uiA1);
			
		}
		
		int maxL = 0;
		UimaBioCAnnotation bestA = null;
		for(UimaBioCAnnotation uiA : selectedAnnotations) {
			int l = uiA.getEnd() - uiA.getBegin();
			if( l > maxL ) {
				bestA = uiA;
				maxL = l;
			}
		}
			
		if( bestA != null ) {
		
			File outFile = new File(this.outDir.getPath() + "/" + 
					id + "_" + this.annot2Extract + "_" + bestA.getBegin() + 
					"_" + bestA.getEnd() + ".txt");
			
			this.dumpSectionToFile(jCas, outFile, bestA);

		} else if( !this.skipBody ) {

			for (UimaBioCAnnotation uiA1 : outerAnnotations) {
				
				Map<String, String> inf = UimaBioCUtils.convertInfons(uiA1.getInfons());
				if( !inf.containsKey("type")  ) 
					continue;

				if( !(inf.get("type").equals("formatting") &&
						inf.get("value").equals("body")) ) {
					continue;
				}
				
				File outFile = new File(this.outDir.getPath() + "/" + 
						id + "_body_" + uiA1.getBegin() + 
						"_" + uiA1.getEnd() + ".txt");
				
				this.dumpSectionToFile(jCas, outFile, uiA1);
				break;
				
			}
			
		}

		
	}
	
	private void dumpSectionToFile(JCas jCas, File outFile, UimaBioCAnnotation uiA1) 
			throws AnalysisEngineProcessException {
		
		PrintWriter out;
		try {
			out = new PrintWriter(new BufferedWriter(
					new FileWriter(outFile, true)));
		} catch (IOException e) {
			throw( new AnalysisEngineProcessException(e));
		}

		List<UimaBioCAnnotation> floats = new ArrayList<UimaBioCAnnotation>();
		List<UimaBioCAnnotation> parags = new ArrayList<UimaBioCAnnotation>();
		for( UimaBioCAnnotation a : JCasUtil.selectCovered(
				UimaBioCAnnotation.class, uiA1) ) {
			Map<String, String> infons = 
					UimaBioCUtils.convertInfons(a.getInfons());
			if( infons.containsKey("position") 
					&& infons.get("position").equals("float") ){
				floats.add(a);							
			} else if( infons.containsKey("value") 
					&& (infons.get("value").equals("p") || 
							infons.get("value").equals("title")) ){
				parags.add(a);							
			} 

		}
		
		int sNumber = 0;
		List<Sentence> sentences = JCasUtil.selectCovered(
				org.cleartk.token.type.Sentence.class, uiA1
				);
		SENTENCE_LOOP: for(Sentence s : sentences) {
		
			if( !this.keepFloats ) {
				for( UimaBioCAnnotation f : floats ) {
					if( s.getBegin()>=f.getBegin() && s.getEnd()<=f.getEnd() ) {
						continue SENTENCE_LOOP;
					}
				}
			}
			
			//
			// Identify exLinks, inLinks or headers
			//
			Map<Integer,Integer> currLvl = new HashMap<Integer,Integer>();
			if( this.headerLinks ) {
				
				Set<String> codes = new HashSet<String>();
				Set<String> expts = new HashSet<String>();
				
				for( UimaBioCAnnotation a : JCasUtil.selectCovered(
						UimaBioCAnnotation.class, s) ) {
					Map<String, String> infons = 
							UimaBioCUtils.convertInfons(a.getInfons());
					if( infons.containsKey("refType") 
							&& infons.get("refType").equals("bibr") ){
						codes.add("exLink");							
					} else if( infons.containsKey("refType") 
							&& infons.get("refType").equals("fig") ){
						codes.add("inLink");

						// DO WE WANT TO EXTRACT 
						// THE EXPERIMENTAL CODES
						// MORE ACCURATELY?
						String exptCodes = readExptCodes(jCas, a);
						expts.add(exptCodes);
					} 
				}	

				//
				// Look for section headings
				//
				for( UimaBioCAnnotation a : JCasUtil.selectCovering(jCas, 
						UimaBioCAnnotation.class, s.getBegin(), s.getEnd()) ) {
					Map<String, String> infons = 
							UimaBioCUtils.convertInfons(a.getInfons());						
					if( infons.get("value").equals("title") ) {
						UimaBioCAnnotation secAnn = this.readSectionHeading(jCas, a);
						if( secAnn == null ) {
							UimaBioCDocument uiD = JCasUtil.selectSingle(jCas,
									UimaBioCDocument.class);
							System.err.println(uiD.getId() + " has title sentence '" + s.getCoveredText() 
								+ "' that we cannot assign to a section");
							codes.add("header-?");							
							continue;
						}
						
						int level = 99;
						try {
							level = readHeadingLevel(jCas, secAnn, 0);
						} catch (StackOverflowError e) {
							e.printStackTrace();
						}

						if( currLvl.containsKey(level) ) {
							currLvl.put(level, currLvl.get(level) + 1);
							int tempLevel = level;
							while( currLvl.containsKey(tempLevel+1) ) {
								tempLevel++;
								currLvl.remove(tempLevel);
							}
						} else {
							currLvl.put(level, 1);
						}
						codes.add("header-" + level);							
					}

				}	
				
				// 
				// Look for paragraphs
				// 
				String pCode = "-";
				for( int i=0; i<parags.size(); i++ ) {
					UimaBioCAnnotation p = parags.get(i);
					if( s.getBegin()>=p.getBegin() && s.getEnd()<=p.getEnd() ) {
						UimaBioCUtils.convertInfons(p.getInfons()).get("value");
						pCode = UimaBioCUtils.convertInfons(p.getInfons()).get("value") + i;
						break;
					}
				}
				
				String friesSentenceId = "-";
				String friesEventsIds = "-";
				for( UimaBioCPassage friesSent : JCasUtil.selectCovered(
						UimaBioCPassage.class, s
						)) {
					Map<String,String> inf = UimaBioCUtils.convertInfons(friesSent.getInfons());
					if( inf.containsKey("friesId")) {
						friesSentenceId = inf.get("friesId") + ":" + inf.get("score") ;
						friesEventsIds = inf.get("events");
					}
				}				
				
				sNumber++;
				PHRASE_LOOP: for( UimaBioCAnnotation a : JCasUtil.selectCovered(jCas, UimaBioCAnnotation.class, s) ) {
					Map<String, String> infons = 
							UimaBioCUtils.convertInfons(a.getInfons());						
					if( infons.get("type").equals("rubicon") &&
							infons.get("value").equals("clause") ) {
						
						out.print( sNumber );
						out.print( "\t" );
						out.print( readTokenizedText(jCas, a) );
						out.print("\t");
						out.print(codes.toString());
						out.print("\t");
						out.print(expts.toString());
						out.print("\t");
						out.print( pCode );
						if( addFries ) {
							out.print("\t");
							out.print( friesSentenceId );
							out.print("\t");
							out.print( friesEventsIds );
						}
						out.print("\n");

					}
				}
				
				/*out.print( sNumber++ );
				out.print("\t");
				out.print( readTokenizedText(jCas, s) );
				out.print("\t");
				out.print(codes.toString());
				out.print("\t");
				out.print(expts.toString());
				out.print("\t");
				out.print( pCode );
				if( addFries ) {
					out.print("\t");
					out.print( friesSentenceId );
					out.print("\t");
					out.print( friesEventsIds );
				}*/
				
			} else {
				
				out.print( readTokenizedText(jCas, s) );
				out.print("\n");

			}
						
		}
		out.close();
	}

	private String readExptCodes(JCas jCas, UimaBioCAnnotation s) {

		String exptCode = s.getCoveredText();
		int offset = 2;
		if( exptCode.toLowerCase().startsWith("fig") ) {
			offset = 1;
		} 
			
		List<Token> l = JCasUtil.selectCovered(Token.class, s);
		List<Token> f = JCasUtil.selectFollowing(jCas, Token.class, s, 10);
		List<Token> p = JCasUtil.selectPreceding(jCas, Token.class, s, offset);
		
		Token start = p.get(0);
		Token end = f.get(9);
		String figFrag = jCas.getDocumentText().substring(
				start.getBegin(), 
				end.getEnd());
		
		for(Pattern patt : this.figPatt){
			Matcher m = patt.matcher(figFrag);
			if( m.find() ) {
				// use group 2 since all 
				return m.group(2);
			}
		}
		
		return exptCode;
	}

	private UimaBioCAnnotation readSectionHeading(JCas jCas, UimaBioCAnnotation a) {
		
		// Looking for section headings
		for( UimaBioCAnnotation a1 : JCasUtil.selectCovering(jCas,
				UimaBioCAnnotation.class, a.getBegin(), a.getEnd())
				){
			Map<String, String> infons = 
					UimaBioCUtils.convertInfons(a1.getInfons());						
			if( infons.containsKey("sectionHeading") 
					&& a1.getBegin() == a.getBegin()  ) {
				return a1;
			}
		}		
		
		return null;
				
	}
	
	private int readHeadingLevel(JCas jCas, UimaBioCAnnotation a, int level) throws StackOverflowError {
		
		// Looking for section headings
		for( UimaBioCAnnotation a1 : JCasUtil.selectCovering(jCas, 
				UimaBioCAnnotation.class, a.getBegin(), a.getEnd()) ) {
			if( a1.equals(a) ) 
				continue;
			Map<String, String> infons = 
					UimaBioCUtils.convertInfons(a1.getInfons());						
			if( infons.containsKey("sectionHeading")  ) {
				level = readHeadingLevel(jCas, a1, level+1);
				return level;
			}
		}		
		
		return level;
				
	}
	
	private String readTokenizedText(JCas jCas, Sentence s) {
		String txt = "";
		for( Token t : JCasUtil.selectCovered(jCas, Token.class, s) ) {
			txt += t.getCoveredText() + " ";
		}		
		return txt.substring(0,txt.length()-1);
	}
	
	private String readTokenizedText(JCas jCas, UimaBioCAnnotation a) {
		String txt = "";
		for( Token t : JCasUtil.selectCovered(jCas, Token.class, a) ) {
			txt += t.getCoveredText() + " ";
		}		
		return txt.substring(0,txt.length()-1);
	}
	
}
