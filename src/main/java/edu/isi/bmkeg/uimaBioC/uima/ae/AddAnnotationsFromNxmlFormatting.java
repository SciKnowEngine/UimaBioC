package edu.isi.bmkeg.uimaBioC.uima.ae;

import java.util.List;
import java.util.Map;

import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.FSArray;
import org.uimafit.component.JCasAnnotator_ImplBase;
import org.uimafit.util.JCasUtil;

import bioc.type.UimaBioCAnnotation;
import bioc.type.UimaBioCDocument;
import bioc.type.UimaBioCPassage;
import edu.isi.bmkeg.uimaBioC.UimaBioCUtils;

public class AddAnnotationsFromNxmlFormatting extends JCasAnnotator_ImplBase {

	public void process(JCas jCas) throws AnalysisEngineProcessException {

		UimaBioCDocument uiD = JCasUtil.selectSingle(jCas, UimaBioCDocument.class);
		if( uiD.getId().equals("skip") )
			return;
		
		UimaBioCPassage docPassage = null;
		
		List<UimaBioCPassage> passages = JCasUtil.selectCovered(UimaBioCPassage.class, uiD);
		FSArray psgArray = new FSArray(jCas, passages.size());
		int passageCount = 0;
		uiD.setPassages(psgArray);
		for (UimaBioCPassage uiP : passages) {			
			psgArray.set(passageCount, uiP);
			passageCount++;

			Map<String,String> infons = UimaBioCUtils.convertInfons(uiP.getInfons());
			if( infons.get("type").equals("document") ) {
				docPassage = uiP;				
			}
		}
		
		if( docPassage == null )
			throw new AnalysisEngineProcessException(
					new Exception("'Document' passage not set in BioC")
					);

		List<UimaBioCAnnotation> annotations = JCasUtil.selectCovered(
				UimaBioCAnnotation.class, docPassage);
		FSArray annArray = new FSArray(jCas, annotations.size());
		int annotationCount = 0;
		docPassage.setAnnotations(annArray);
		for (UimaBioCAnnotation uiA : annotations) {
			annArray.set(annotationCount, uiA);
			annotationCount++;
		}
		
	}

}
