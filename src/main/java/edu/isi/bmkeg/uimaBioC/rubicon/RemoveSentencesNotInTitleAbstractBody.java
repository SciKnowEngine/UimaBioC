package edu.isi.bmkeg.uimaBioC.rubicon;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.cleartk.token.type.Sentence;
import org.uimafit.component.JCasAnnotator_ImplBase;
import org.uimafit.util.JCasUtil;

import bioc.type.UimaBioCAnnotation;
import bioc.type.UimaBioCDocument;
import edu.isi.bmkeg.uimaBioC.UimaBioCUtils;

public class RemoveSentencesNotInTitleAbstractBody extends JCasAnnotator_ImplBase {

	Map<String,Map<String,Integer>> table = new HashMap<String, Map<String, Integer>>();
	
	private static Logger logger = Logger.getLogger(RemoveSentencesNotInTitleAbstractBody.class);
	
	public void initialize(UimaContext context)
			throws ResourceInitializationException {

		super.initialize(context);
		
	}

	public void process(JCas jCas) throws AnalysisEngineProcessException {

		UimaBioCDocument uiD = JCasUtil.selectSingle(jCas,
				UimaBioCDocument.class);
		if( uiD.getId().equals("skip") )
			return;
		
		List<UimaBioCAnnotation> floats = UimaBioCUtils.readFloats(jCas);
		List<Sentence> sentences = UimaBioCUtils.readAllReadableSentences(jCas);
				
		Set<Sentence> sentencesToKeep = new HashSet<Sentence>(sentences);
		
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
