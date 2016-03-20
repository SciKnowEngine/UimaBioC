package edu.isi.bmkeg.uimaBioC.uima.ae;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.cleartk.token.type.Sentence;
import org.uimafit.component.JCasAnnotator_ImplBase;
import org.uimafit.descriptor.ConfigurationParameter;
import org.uimafit.factory.ConfigurationParameterFactory;
import org.uimafit.util.JCasUtil;

import bioc.type.UimaBioCAnnotation;
import bioc.type.UimaBioCDocument;
import edu.isi.bmkeg.uimaBioC.UimaBioCUtils;

public class RemoveSentencesFromOtherSections extends JCasAnnotator_ImplBase {

	public final static String PARAM_ANNOT_2_EXTRACT = ConfigurationParameterFactory
			.createConfigurationParameterName(
					RemoveSentencesFromOtherSections.class, "annot2Extract");
	@ConfigurationParameter(mandatory = true, description = "The section heading *pattern* to extract")
	String annot2Extract;
	private Pattern patt;

	public final static String PARAM_KEEP_FLOATING_BOXES = ConfigurationParameterFactory
			.createConfigurationParameterName(
					RemoveSentencesFromOtherSections.class, "keepFloatsStr");
	@ConfigurationParameter(mandatory = true, description = "Should we include floating boxes in the output.")
	String keepFloatsStr;
	Boolean keepFloats = false;

	Map<String,Map<String,Integer>> table = new HashMap<String, Map<String, Integer>>();
	
	private static Logger logger = Logger.getLogger(RemoveSentencesFromOtherSections.class);
	
	public void initialize(UimaContext context)
			throws ResourceInitializationException {

		super.initialize(context);

		if(this.keepFloatsStr != null && this.keepFloatsStr.toLowerCase().equals("true") ) {
			keepFloats = true;
		} else {
			keepFloats = false;
		} 
		
		this.patt = Pattern.compile(this.annot2Extract);

	}

	public void process(JCas jCas) throws AnalysisEngineProcessException {

		UimaBioCDocument uiD = JCasUtil.selectSingle(jCas,
				UimaBioCDocument.class);
		if( uiD.getId().equals("skip") )
			return;
		
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
		
			this.removeOtherSentences(jCas, bestA);

		} else {

			for (UimaBioCAnnotation uiA1 : outerAnnotations) {
				
				Map<String, String> inf = UimaBioCUtils.convertInfons(uiA1.getInfons());
				if( !inf.containsKey("type")  ) 
					continue;

				if( !(inf.get("type").equals("formatting") &&
						inf.get("value").equals("body")) ) {
					continue;
				}
				
				logger.info( "Skipping " + uiD.getId() );
				uiD.setId("skip");
				break;
				
			}
			
		}
		
	}
	
	private void removeOtherSentences(JCas jCas, UimaBioCAnnotation uiA1) 
			throws AnalysisEngineProcessException {
		
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


		
		List<Sentence> sentences = JCasUtil.selectCovered(
				org.cleartk.token.type.Sentence.class, uiA1
				);
		Set<Sentence> sentencesToKeep = new HashSet<Sentence>(sentences);
		for(Sentence s : sentences) {
			if( !this.keepFloats ) {
				for( UimaBioCAnnotation f : floats ) {
					if( s.getBegin()>=f.getBegin() && s.getEnd()<=f.getEnd() ) {
						sentencesToKeep.remove(s);
					}
				}
			}
		}
	
		UimaBioCDocument uiD = JCasUtil.selectSingle(jCas,
				UimaBioCDocument.class);
		List<Sentence> allSentences = JCasUtil.selectCovered(
				org.cleartk.token.type.Sentence.class, uiD
				);
		for(Sentence s : allSentences) {
			if( !sentencesToKeep.contains(s) ) {
				s.removeFromIndexes();
			}
		}	
		
	}	
	
}
