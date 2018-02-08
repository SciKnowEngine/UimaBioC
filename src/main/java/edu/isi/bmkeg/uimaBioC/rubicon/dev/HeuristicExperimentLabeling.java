package edu.isi.bmkeg.uimaBioC.rubicon.dev;

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

public class HeuristicExperimentLabeling extends JCasAnnotator_ImplBase {

	private static Logger logger = Logger.getLogger(HeuristicExperimentLabeling.class);

	public final static String PARAM_ANNOT_2_EXTRACT = ConfigurationParameterFactory
			.createConfigurationParameterName(HeuristicExperimentLabeling.class, "annot2Extract");
	@ConfigurationParameter(mandatory = false, description = "The section heading *pattern* to extract")
	String annot2Extract;
	private Pattern patt;

	public final static String PARAM_DIR_PATH = ConfigurationParameterFactory
			.createConfigurationParameterName(HeuristicExperimentLabeling.class, "outFilePath");
	@ConfigurationParameter(mandatory = true, description = "The path to the output directory.")
	String outDirPath;
	
	public final static String PARAM_KEEP_FLOATING_BOXES = ConfigurationParameterFactory
			.createConfigurationParameterName(HeuristicExperimentLabeling.class, "keepFloatsStr");
	@ConfigurationParameter(mandatory = false, description = "Should we include floating boxes in the output.")
	String keepFloatsStr;
	Boolean keepFloats = false;

	private File outDir;
	private BioCCollection collection;

	Map<String, Map<String, Integer>> table = new HashMap<String, Map<String, Integer>>();

	public void initialize(UimaContext context) throws ResourceInitializationException {

		super.initialize(context);

		this.outDirPath = (String) context.getConfigParameterValue(PARAM_DIR_PATH);
		this.outDir = new File(this.outDirPath);

		if (!this.outDir.exists())
			this.outDir.mkdirs();

		if (this.keepFloatsStr.toLowerCase().equals("true")) {
			keepFloats = true;
		}

		this.collection = new BioCCollection();

		if (this.annot2Extract != null)
			this.patt = Pattern.compile(this.annot2Extract);

	}

	public void process(JCas jCas) throws AnalysisEngineProcessException {

		UimaBioCDocument uiD = JCasUtil.selectSingle(jCas, UimaBioCDocument.class);
		if (uiD.getId().equals("skip"))
			return;

		String id = uiD.getId();

		List<UimaBioCAnnotation> outerAnnotations = JCasUtil.selectCovered(UimaBioCAnnotation.class, uiD);

		if (this.annot2Extract != null) {

			Set<UimaBioCAnnotation> selectedAnnotations = new HashSet<UimaBioCAnnotation>();
			for (UimaBioCAnnotation uiA1 : outerAnnotations) {

				Map<String, String> inf = UimaBioCUtils.convertInfons(uiA1.getInfons());
				if (!inf.containsKey("type"))
					continue;

				if (!(inf.get("type").equals("formatting") && inf.get("value").equals("sec"))) {
					continue;
				}

				if (this.patt != null) {
					Matcher match = this.patt.matcher(inf.get("sectionHeading"));
					if (!match.find()) {
						continue;
					}
				}

				selectedAnnotations.add(uiA1);

			}

			int maxL = 0;
			UimaBioCAnnotation bestA = null;
			for (UimaBioCAnnotation uiA : selectedAnnotations) {
				int l = uiA.getEnd() - uiA.getBegin();
				if (l > maxL) {
					bestA = uiA;
					maxL = l;
				}
			}

			if (bestA != null) {

				this.buildDataStructures(jCas, bestA.getBegin(), bestA.getEnd());

			} else {

				logger.error("Couldn't find a section heading corresponding to " + this.annot2Extract + " in "
						+ uiD.getId());

			}

		} else {

			this.buildDataStructures(jCas, uiD.getBegin(), uiD.getEnd());

		}

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
					&& (infons.get("value").equals("p") || infons.get("value").equals("title"))) {
				parags.add(a);
			}

		}
		
		int sNumber = 0;
		List<Sentence> sentences = JCasUtil.selectCovered(jCas, Sentence.class, start, end);
		SENTENCE_LOOP: for (Sentence s : sentences) {

			if (!this.keepFloats) {
				for (UimaBioCAnnotation f : floats) {
					if (s.getBegin() >= f.getBegin() && s.getEnd() <= f.getEnd()) {
						continue SENTENCE_LOOP;
					}
				}
			}

			//
			// Identify exLinks, inLinks or headers
			//
			Map<Integer, Integer> currLvl = new HashMap<Integer, Integer>();

			Set<String> codes = new HashSet<String>();
			
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
			}

			List<String> headingList = new ArrayList<String>();
			List<String> codeList = new ArrayList<String>();
			List<String> exptList = new ArrayList<String>();
			for (UimaBioCAnnotation clause : clauseList) {
				Map<String,String> infons = UimaBioCUtils.convertInfons(clause.getInfons());
				headingList.add( infons.get("scidp-heading-string") );
				codeList.add( infons.get("scidp-inExHeading-string") );
				exptList.add( infons.get("scidp-experiment-labels") );
				clause.setInfons(UimaBioCUtils.convertInfons(infons, jCas));
				clause.addToIndexes();
			}
			
			
			

		}

	}
	
	private String readTokenizedText(JCas jCas, Sentence s) {
		String txt = "";
		for (Token t : JCasUtil.selectCovered(jCas, Token.class, s)) {
			txt += t.getCoveredText() + " ";
		}
		if (txt.length() == 0)
			return txt;
		return txt.substring(0, txt.length() - 1);
	}

	private String readTokenizedText(JCas jCas, UimaBioCAnnotation a) {
		String txt = "";
		for (Token t : JCasUtil.selectCovered(jCas, Token.class, a)) {
			txt += t.getCoveredText() + " ";
		}
		if (txt.length() == 0)
			return txt;
		return txt.substring(0, txt.length() - 1);
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
