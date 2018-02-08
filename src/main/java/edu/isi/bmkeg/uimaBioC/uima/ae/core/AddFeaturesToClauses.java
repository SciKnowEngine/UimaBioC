package edu.isi.bmkeg.uimaBioC.uima.ae.core;

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

import org.apache.log4j.Logger;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.FSArray;
import org.apache.uima.jcas.tcas.Annotation;
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
import bioc.type.UimaBioCLocation;
import edu.isi.bmkeg.uimaBioC.UimaBioCUtils;

public class AddFeaturesToClauses extends JCasAnnotator_ImplBase {

	private static Logger logger = Logger.getLogger(AddFeaturesToClauses.class);

	private BioCCollection collection;

	Map<String, Map<String, Integer>> table = new HashMap<String, Map<String, Integer>>();

	public void initialize(UimaContext context) throws ResourceInitializationException {
		super.initialize(context);

		this.collection = new BioCCollection();

	}

	public void process(JCas jCas) throws AnalysisEngineProcessException {

		UimaBioCDocument uiD = JCasUtil.selectSingle(jCas, UimaBioCDocument.class);
		if (uiD.getId().equals("skip"))
			return;

		String id = uiD.getId();

		List<UimaBioCAnnotation> outerAnnotations = JCasUtil.selectCovered(UimaBioCAnnotation.class, uiD);

		this.buildDataStructures(jCas, uiD.getBegin(), uiD.getEnd());
		
	}

	private void buildDataStructures(JCas jCas, int start, int end)
			throws AnalysisEngineProcessException {

		List<UimaBioCAnnotation> floats = new ArrayList<UimaBioCAnnotation>();
		List<UimaBioCAnnotation> parags = new ArrayList<UimaBioCAnnotation>();
		for (UimaBioCAnnotation a : JCasUtil.selectCovered(jCas, UimaBioCAnnotation.class, start, end)) {
			Map<String, String> infons = UimaBioCUtils.convertInfons(a.getInfons());
			if (infons.containsKey("position") && infons.get("position").equals("float")) {
				floats.add(a);
			} else if (infons.containsKey("value")
					&& (infons.get("value").equals("p") || 
							infons.get("value").equals("title") || 
							infons.get("value").equals("label") || 
							infons.get("value").equals("article-title"))) {
				parags.add(a);
			}

		}
		
		int sNumber = 0;
		List<Sentence> sentences = JCasUtil.selectCovered(jCas, Sentence.class, start, end);
		SENTENCE_LOOP: for (Sentence s : sentences) {

			//
			// Identify exLinks, inLinks or headers
			//
			Map<Integer, Integer> currLvl = new HashMap<Integer, Integer>();

			Set<String> codes = new HashSet<String>();
			
			//
			// Look for section headings
			//
			for (UimaBioCAnnotation a : JCasUtil.selectCovering(jCas, UimaBioCAnnotation.class, s.getBegin(),
					s.getEnd())) {
				Map<String, String> infons = UimaBioCUtils.convertInfons(a.getInfons());
				if (infons.get("value").equals("title")) {
					UimaBioCAnnotation secAnn = UimaBioCUtils.readSectionHeading(jCas, a);
					if (secAnn == null) {
						UimaBioCDocument uiD = JCasUtil.selectSingle(jCas, UimaBioCDocument.class);
						System.err.println(uiD.getId() + " has title sentence '" + s.getCoveredText()
								+ "' that we cannot assign to a section");
						codes.add("header-?");
						continue;
					}

					int level = 99;
					try {
						level = UimaBioCUtils.readHeadingLevel(jCas, secAnn, 0);
					} catch (StackOverflowError e) {
						e.printStackTrace();
					}

					if (currLvl.containsKey(level)) {
						currLvl.put(level, currLvl.get(level) + 1);
						int tempLevel = level;
						while (currLvl.containsKey(tempLevel + 1)) {
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
			for (int i = 0; i < parags.size(); i++) {
				UimaBioCAnnotation p = parags.get(i);
				if (s.getBegin() >= p.getBegin() && s.getEnd() <= p.getEnd()) {
					UimaBioCUtils.convertInfons(p.getInfons()).get("value");
					pCode = UimaBioCUtils.convertInfons(p.getInfons()).get("value") + i;
					break;
				}
			}

			sNumber++;

			List<UimaBioCAnnotation> clauseList = new ArrayList<UimaBioCAnnotation>();
			for (UimaBioCAnnotation a : JCasUtil.selectCovered(jCas, UimaBioCAnnotation.class, s)) {
				Map<String, String> infons = UimaBioCUtils.convertInfons(a.getInfons());
				if (infons.get("type").equals("rubicon") && infons.get("value").equals("clause"))
					clauseList.add(a);
			}
			
			if(clauseList.size() == 0) {
				UimaBioCDocument uiD = JCasUtil.selectSingle(jCas, UimaBioCDocument.class);
				logger.warn("No Clauses Found in "+uiD.getId()+"("+s.getBegin() +"-"+s.getEnd()+"): " + s.getCoveredText() );
				UimaBioCAnnotation newCl = this.createClauseAnnotation(jCas, s.getBegin(), s.getEnd()); 
				clauseList.add(newCl);
				newCl.addToIndexes();
			}

			for (UimaBioCAnnotation clause : clauseList) {
				
				Set<String> expts = UimaBioCUtils.extractExptsFromClause(jCas, clause);
				Set<String> newCodes = UimaBioCUtils.extractCodesFromClause(jCas, clause);
				newCodes.addAll(codes);
				String headingString = UimaBioCUtils.readHeadingString(jCas, clause, "");
				
				Map<String,String> infons = UimaBioCUtils.convertInfons(clause.getInfons());
				infons.put("scidp-heading-string", headingString);
				infons.put("scidp-inExHeading-string", newCodes.toString());
				infons.put("scidp-experiment-labels", expts.toString());
				infons.put("scidp-paragraph-number", pCode);
				infons.put("scidp-sentence-number", "s" + sNumber);
				
				clause.setInfons(UimaBioCUtils.convertInfons(infons, jCas));
				
			}

		}
		
		// Checking features for every clause annotation.

	}
	
	private UimaBioCAnnotation createClauseAnnotation(JCas jCas, int begin, int end) {

		UimaBioCAnnotation uiA = new UimaBioCAnnotation(jCas);

		FSArray locations = new FSArray(jCas, 1);
		uiA.setLocations(locations);
		UimaBioCLocation uiL = new UimaBioCLocation(jCas);
		uiL.setOffset(begin);
		uiL.setLength(end - begin);
		locations.set(0, uiL);

		uiA.setBegin(uiL.getOffset());
		uiA.setEnd(uiL.getOffset() + uiL.getLength());

		uiL.setBegin(uiL.getOffset());
		uiL.setEnd(uiL.getOffset() + uiL.getLength());

		Map<String, String> infons = new HashMap<String, String>();
		infons.put("type", "rubicon");
		infons.put("value", "clause");

		uiA.setInfons(UimaBioCUtils.convertInfons(infons, jCas));

		return uiA;

	}

}
