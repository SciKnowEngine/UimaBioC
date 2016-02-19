package edu.isi.bmkeg.uimaBioC.uima.ae.core;

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
import org.uimafit.component.JCasAnnotator_ImplBase;
import org.uimafit.descriptor.ConfigurationParameter;
import org.uimafit.factory.ConfigurationParameterFactory;
import org.uimafit.util.JCasUtil;

import bioc.type.UimaBioCAnnotation;
import bioc.type.UimaBioCDocument;
import edu.isi.bmkeg.uimaBioC.UimaBioCUtils;

public class FilterUnwantedSections extends JCasAnnotator_ImplBase {

	public final static String PARAM_ANNOT_2_EXTRACT = ConfigurationParameterFactory
			.createConfigurationParameterName(
					FilterUnwantedSections.class, "annot2Extract");
	@ConfigurationParameter(mandatory = true, description = "The section heading *pattern* to extract")
	String annot2Extract;
	private Pattern patt;

	public final static String PARAM_KEEP_FLOATING_BOXES = ConfigurationParameterFactory
			.createConfigurationParameterName(
					FilterUnwantedSections.class, "keepFloatsStr");
	@ConfigurationParameter(mandatory = true, description = "Should we include floating boxes in the output.")
	String keepFloatsStr;
	Boolean keepFloats = false;
	
	Map<String,Map<String,Integer>> table = new HashMap<String, Map<String, Integer>>();
	
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

		List<Sentence> sentences = JCasUtil.selectCovered(
				org.cleartk.token.type.Sentence.class,uiD
				);
		for(Sentence s : sentences) {

			if( bestA != null ) {
				if( s.getBegin() < bestA.getBegin() || 
						s.getEnd() > bestA.getEnd() ) {
					s.removeFromIndexes();
				}
			} else {
				s.removeFromIndexes();				
			}
			
		} 
		
	}
	
	
}
