package edu.isi.bmkeg.uimaBioC.uima.ae.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.FSArray;
import org.cleartk.token.type.Sentence;
import org.uimafit.component.JCasAnnotator_ImplBase;
import org.uimafit.util.JCasUtil;

import bioc.type.UimaBioCAnnotation;
import bioc.type.UimaBioCDocument;
import bioc.type.UimaBioCPassage;
import edu.isi.bmkeg.uimaBioC.UimaBioCUtils;

public class FixSentencesFromHeadings extends JCasAnnotator_ImplBase {

	public void process(JCas jCas) throws AnalysisEngineProcessException {

		UimaBioCDocument uiD = JCasUtil.selectSingle(jCas, UimaBioCDocument.class);
		UimaBioCPassage docPassage = null;

		List<UimaBioCPassage> passages = JCasUtil.selectCovered(UimaBioCPassage.class, uiD);
		FSArray psgArray = new FSArray(jCas, passages.size());
		int passageCount = 0;
		uiD.setPassages(psgArray);
		for (UimaBioCPassage uiP : passages) {
			psgArray.set(passageCount, uiP);
			passageCount++;

			Map<String, String> infons = UimaBioCUtils.convertInfons(uiP.getInfons());
			if (infons.get("type").equals("document")) {
				docPassage = uiP;
			}
		}

		if (docPassage == null)
			throw new AnalysisEngineProcessException(new Exception("'Document' passage not set in BioC"));

		List<UimaBioCAnnotation> annotations = JCasUtil.selectCovered(UimaBioCAnnotation.class, docPassage);
		for (UimaBioCAnnotation a : annotations) {
			Map<String, String> inf = UimaBioCUtils.convertInfons(a.getInfons());

			//
			// detects 'run-on' sentences from titles that do not end in a
			// period
			//
			if (inf.containsKey("value") && inf.get("value").toLowerCase().equals("title")) {

				List<Sentence> sentences = JCasUtil.selectCovering(jCas, Sentence.class, a.getBegin(), a.getEnd());

				for (Sentence oldSentence : sentences) {
					if (oldSentence.getBegin() == a.getBegin() && oldSentence.getEnd() != a.getEnd()) {
						oldSentence.removeFromIndexes(jCas);
						Sentence s1 = new Sentence(jCas);
						s1.setBegin(a.getBegin());
						s1.setEnd(a.getEnd());
						s1.addToIndexes(jCas);
						Sentence s2 = new Sentence(jCas);
						s2.setBegin(a.getEnd() + 1);
						s2.setEnd(oldSentence.getEnd());
						s2.addToIndexes(jCas);
					}
				}

			}
			//
			// FINDS BUGGY SENTENCES THAT PARTIALLY OVERLAP FLOATS
			//
			else if (inf.containsKey("position") && inf.get("position").toLowerCase().equals("float")) {

				List<Sentence> covered = JCasUtil.selectCovered(Sentence.class, a);
				if( covered.size() == 0 )
					continue;
				Sentence s = covered.get(0);
				List<Sentence> preceding = JCasUtil.selectPreceding(jCas, Sentence.class, s, 1);
				Sentence oldSentence = preceding.get(0);
				if (oldSentence.getEnd() > a.getBegin()) {
						oldSentence.removeFromIndexes(jCas);
						Sentence s1 = new Sentence(jCas);
						s1.setBegin(oldSentence.getBegin());
						s1.setEnd(a.getBegin() - 1);
						s1.addToIndexes(jCas);
						Sentence s2 = new Sentence(jCas);
						s2.setBegin(a.getBegin());
						s2.setEnd(oldSentence.getEnd());
						s2.addToIndexes(jCas);
				}

			}

		}

	}

}
