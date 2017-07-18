package edu.isi.bmkeg.uimaBioC.uima.ae.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.log4j.Logger;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.uimafit.component.JCasAnnotator_ImplBase;
import org.uimafit.util.JCasUtil;

import bioc.type.UimaBioCAnnotation;
import bioc.type.UimaBioCDocument;
import bioc.type.UimaBioCPassage;
import edu.isi.bmkeg.uimaBioC.UimaBioCUtils;

public class RemoveRepeatedBioCAnnotations extends JCasAnnotator_ImplBase {
	
	private static Logger logger = Logger.getLogger(RemoveRepeatedBioCAnnotations.class);

	public void initialize(UimaContext context) throws ResourceInitializationException {

		super.initialize(context);

	}

	public void process(JCas jCas) throws AnalysisEngineProcessException {

		try {

			UimaBioCDocument uiD = JCasUtil.selectSingle(jCas, UimaBioCDocument.class);
			if (uiD.getId().equals("skip"))
				return;

			UimaBioCPassage docP = UimaBioCUtils.readDocument(jCas);

			logger.info("Cleaning repeats for " + uiD.getId() );

			Set<UimaBioCAnnotation> repeats = new HashSet<UimaBioCAnnotation>();
			for (UimaBioCAnnotation a : JCasUtil.selectCovered(jCas, UimaBioCAnnotation.class, docP)) {
				String s1 = UimaBioCUtils.stringify(a);
				for (UimaBioCAnnotation aa : JCasUtil.selectCovered(jCas, UimaBioCAnnotation.class, a.getBegin(), a.getEnd())) {
					if( repeats.contains(a) )
						continue;
					if( !a.equals(aa) ) {
						String s2 = UimaBioCUtils.stringify(aa);
						if( s1.equals(s2) ) {
							repeats.add(aa);
						}
					}
				}				
			}
			
			for (UimaBioCAnnotation a : repeats) {
				a.removeFromIndexes();
			}

			logger.info("Cleaning repeats for " + uiD.getId() + " - COMPLETED");

		} catch (Exception e) {

			throw new AnalysisEngineProcessException(e);

		}

	}
	
}
