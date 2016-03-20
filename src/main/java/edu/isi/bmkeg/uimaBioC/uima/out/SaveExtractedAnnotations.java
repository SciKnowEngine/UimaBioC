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

import org.apache.log4j.Logger;
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
import edu.isi.bmkeg.uimaBioC.rubicon.MatchReachAndNxmlText;

public class SaveExtractedAnnotations extends JCasAnnotator_ImplBase {

	private static Logger logger = Logger.getLogger(SaveExtractedAnnotations.class);

	public final static String PARAM_ANNOT_2_EXTRACT = ConfigurationParameterFactory
			.createConfigurationParameterName(SaveExtractedAnnotations.class, "annot2Extract");
	@ConfigurationParameter(mandatory = false, description = "The section heading *pattern* to extract")
	String annot2Extract;
	private Pattern patt;

	public final static String PARAM_DIR_PATH = ConfigurationParameterFactory
			.createConfigurationParameterName(SaveExtractedAnnotations.class, "outDirPath");
	@ConfigurationParameter(mandatory = true, description = "The path to the output directory.")
	String outDirPath;

	public final static String PARAM_CLAUSE_LEVEL = ConfigurationParameterFactory
			.createConfigurationParameterName(SaveExtractedAnnotations.class, "clauseLevelStr");
	@ConfigurationParameter(mandatory = false, description = "Do we split at the clause level?")
	String clauseLevelStr;
	Boolean clauseLevel = false;

	public final static String PARAM_KEEP_FLOATING_BOXES = ConfigurationParameterFactory
			.createConfigurationParameterName(SaveExtractedAnnotations.class, "keepFloatsStr");
	@ConfigurationParameter(mandatory = false, description = "Should we include floating boxes in the output.")
	String keepFloatsStr;
	Boolean keepFloats = false;

	public final static String PARAM_ADD_FRIES_CODES = ConfigurationParameterFactory
			.createConfigurationParameterName(SaveExtractedAnnotations.class, "addFriesStr");
	@ConfigurationParameter(mandatory = false, description = "Should we add FRIES output?")
	String addFriesStr;
	Boolean addFries = false;

	private File outDir;
	private BioCCollection collection;

	private List<Pattern> figPatt = new ArrayList<Pattern>();
	private List<Pattern> figsPatt = new ArrayList<Pattern>();

	Map<String, Map<String, Integer>> table = new HashMap<String, Map<String, Integer>>();

	public void initialize(UimaContext context) throws ResourceInitializationException {

		super.initialize(context);

		this.outDirPath = (String) context.getConfigParameterValue(PARAM_DIR_PATH);
		this.outDir = new File(this.outDirPath);

		if (!this.outDir.exists())
			this.outDir.mkdirs();

		if (this.clauseLevelStr.toLowerCase().equals("true")) {
			clauseLevel = true;
		}

		if (this.keepFloatsStr.toLowerCase().equals("true")) {
			keepFloats = true;
		}

		if (this.addFriesStr != null && this.addFriesStr.toLowerCase().equals("true")) {
			addFries = true;
		} else {
			addFries = false;
		}

		this.collection = new BioCCollection();

		if (this.annot2Extract != null)
			this.patt = Pattern.compile(this.annot2Extract);

		//
		// A List of regular expressions to recognize
		// all figure legend codes appearing in text.
		//
		String b = "\\s*[Ff]ig(ure|.){0,1}";
		String e = "\\s*(\\)|\\w{2,}|\\p{Punct})";
		//
		// 0. No alphanumeric codes at all
		this.figPatt.add(Pattern.compile(b + "\\s*(\\d+)\\s*\\p{Punct}*\\s*\\w{2,}"));
		//
		// 1. Delineated by brackets
		this.figPatt.add(Pattern.compile("\\(" + b + "\\s*(\\d+.*?)\\)"));
		//
		// 2. Simple single alphanumeric codes, followed by punctuation.
		this.figPatt.add(Pattern.compile(b + "\\s*(\\d+\\s*[A-Za-z])\\p{Punct}"));

		//
		// 3. Single Alphanumeric codes, followed by words.
		this.figPatt.add(Pattern.compile(b + "\\s*(\\d+\\s*[A-Za-z])\\s+\\w{2,}"));

		//
		// 4. Fig 8, a and b).
		this.figPatt.add(Pattern.compile(b + "\\s*(\\d[\\s\\p{Punct}]*[A-Za-z] and [A-Za-z])" + e));

		//
		// 5. Fig. 3, a-c).
		this.figPatt.add(Pattern.compile(b + "\\s*(\\d[\\s\\p{Punct}]*[A-Z\\-a-z])" + e));

		//
		// 6. Fig. c).
		this.figPatt.add(Pattern.compile(b + "\\s*([\\s\\p{Punct}]*[A-Z\\-a-z])" + e));

		// Multiple Figures in sequence
		b = "\\s*[Ff]ig(ures|s.{0,1}|.){0,1}";
		// 1. Fig. c).
		this.figsPatt.add(Pattern.compile(b + "\\s*([\\s\\p{Punct}]*[A-Z\\-a-z])" + e));
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

				File outFile = new File(this.outDir.getPath() + "/" + id + "_" + this.annot2Extract + "_"
						+ bestA.getBegin() + "_" + bestA.getEnd() + ".tsv");

				PrintWriter out;
				try {
					out = new PrintWriter(new BufferedWriter(new FileWriter(outFile, true)));
				} catch (IOException e) {
					throw (new AnalysisEngineProcessException(e));
				}

				this.dumpSectionToFile(jCas, out, bestA.getBegin(), bestA.getEnd());

				out.close();

			} else {

				logger.error("Couldn't find a section heading corresponding to " + this.annot2Extract + " in "
						+ uiD.getId());

			}

		} else {

			File outFile = new File(this.outDir.getPath() + "/" + id + "_fulltext.tsv");
			PrintWriter out;
			try {
				out = new PrintWriter(new BufferedWriter(new FileWriter(outFile, true)));
			} catch (IOException e) {
				throw (new AnalysisEngineProcessException(e));
			}

			this.dumpSectionToFile(jCas, out, uiD.getBegin(), uiD.getEnd());

			out.close();

		}

	}

	private void dumpSectionToFile(JCas jCas, PrintWriter out, int start, int end)
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
			Set<String> expts = new HashSet<String>();

			for (UimaBioCAnnotation a : JCasUtil.selectCovered(UimaBioCAnnotation.class, s)) {
				Map<String, String> infons = UimaBioCUtils.convertInfons(a.getInfons());
				if (infons.containsKey("refType") && infons.get("refType").equals("bibr")) {
					codes.add("exLink");
				} else if (infons.containsKey("refType") && infons.get("refType").equals("fig")) {
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
			for (UimaBioCAnnotation a : JCasUtil.selectCovering(jCas, UimaBioCAnnotation.class, s.getBegin(),
					s.getEnd())) {
				Map<String, String> infons = UimaBioCUtils.convertInfons(a.getInfons());
				if (infons.get("value").equals("title")) {
					UimaBioCAnnotation secAnn = this.readSectionHeading(jCas, a);
					if (secAnn == null) {
						UimaBioCDocument uiD = JCasUtil.selectSingle(jCas, UimaBioCDocument.class);
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

			if (this.clauseLevel) {

				List<UimaBioCAnnotation> clauseList = new ArrayList<UimaBioCAnnotation>();
				for (UimaBioCAnnotation a : JCasUtil.selectCovered(jCas, UimaBioCAnnotation.class, s)) {
					Map<String, String> infons = UimaBioCUtils.convertInfons(a.getInfons());
					if (infons.get("type").equals("rubicon") && infons.get("value").equals("clause"))
						clauseList.add(a);
				}
				
				if(clauseList.size() == 0) {
					UimaBioCDocument uiD = JCasUtil.selectSingle(jCas, UimaBioCDocument.class);
					logger.warn("No Clauses Found in "+uiD.getId()+"("+s.getBegin() +"-"+s.getEnd()+"): " + s.getCoveredText());
				}

				for (UimaBioCAnnotation clause : clauseList) {

					out.print(sNumber);
					out.print("\t");
					out.print(readTokenizedText(jCas, clause));
					out.print("\t");
					out.print(codes.toString());
					out.print("\t");
					out.print(expts.toString());
					out.print("\t");
					out.print(pCode);

					if (addFries) {

						String friesSentenceId = "";
						String friesEventsIds = "";
						String friesEventsDetails = "";
						String friesEventText = "";

						for (UimaBioCAnnotation fa : JCasUtil.selectCovered(jCas, UimaBioCAnnotation.class, clause)) {
							Map<String, String> inf = UimaBioCUtils.convertInfons(fa.getInfons());
							if (inf.get("type").equals("FRIES_EventMention")) {

								if( friesSentenceId.length() == 0) 
									friesSentenceId += inf.get("sentId") + ":" + inf.get("score");

								if (friesEventsIds.length() > 0)
									friesEventsIds += ",";
								friesEventsIds += inf.get("eventId");

								if (friesEventsDetails.length() > 0)
									friesEventsDetails += ",";
								friesEventsDetails += inf.get("fType")
										+ (inf.containsKey("fSubType") ? "." + inf.get("fSubType") : "")
										+ inf.get("value");

								if (friesEventText.length() > 0)
									friesEventText += ",";
								friesEventText += inf.get("friesText");

							}
						}

						out.print("\t");
						out.print(friesSentenceId.length() > 0 ? friesSentenceId : "-");
						out.print("\t");
						out.print(friesEventsIds.length() > 0 ? friesEventsIds : "-");
						out.print("\t");
						out.print(friesEventsDetails.length() > 0 ? friesEventsDetails : "-");
						out.print("\t");
						out.print(friesEventText.length() > 0 ? friesEventText : "-");

					}
					out.print("\n");

				}

			} else {

				out.print(sNumber);
				out.print("\t");
				out.print(readTokenizedText(jCas, s));
				out.print("\t");
				out.print(codes.toString());
				out.print("\t");
				out.print(expts.toString());
				out.print("\t");
				out.print(pCode);
				if (addFries) {

					String friesSentenceId = "";
					String friesEventsIds = "";
					String friesEventsDetails = "";
					String friesEventText = "";

					for (UimaBioCAnnotation fa : JCasUtil.selectCovered(jCas, UimaBioCAnnotation.class, s)) {
						Map<String, String> inf = UimaBioCUtils.convertInfons(fa.getInfons());
						if (inf.get("type").equals("FRIES_EventMention")) {

							friesSentenceId += inf.get("sentId") + ":" + inf.get("score");

							if (friesEventsIds.length() > 0)
								friesEventsIds += ",";
							friesEventsIds += inf.get("eventId");

							if (friesEventsDetails.length() > 0)
								friesEventsDetails += ",";
							friesEventsDetails += inf.get("fType")
									+ (inf.containsKey("fSubType") ? "." + inf.get("fSubType") : "") + inf.get("value");

							if (friesEventText.length() > 0)
								friesEventText += ",";
							friesEventText += inf.get("friesEventText");

						}
					}

					out.print("\t");
					out.print(friesSentenceId.length() > 0 ? friesSentenceId : "-");
					out.print("\t");
					out.print(friesEventsIds.length() > 0 ? friesEventsIds : "-");
					out.print("\t");
					out.print(friesEventsDetails.length() > 0 ? friesEventsDetails : "-");
					out.print("\t");
					out.print(friesEventText.length() > 0 ? friesEventText : "-");

				}

				out.print("\n");

			}

		}

	}

	private String readExptCodes(JCas jCas, UimaBioCAnnotation s) {

		String exptCode = s.getCoveredText();
		int offset = 2;
		if (exptCode.toLowerCase().startsWith("fig")) {
			offset = 1;
		}

		List<Token> l = JCasUtil.selectCovered(Token.class, s);
		List<Token> f = JCasUtil.selectFollowing(jCas, Token.class, s, 10);
		List<Token> p = JCasUtil.selectPreceding(jCas, Token.class, s, offset);

		Token start = p.get(0);
		Token end = f.get(9);
		String figFrag = jCas.getDocumentText().substring(start.getBegin(), end.getEnd());

		for (Pattern patt : this.figPatt) {
			Matcher m = patt.matcher(figFrag);
			if (m.find()) {
				// use group 2 since all
				return m.group(2);
			}
		}

		return exptCode;
	}

	private UimaBioCAnnotation readSectionHeading(JCas jCas, UimaBioCAnnotation a) {

		// Looking for section headings
		for (UimaBioCAnnotation a1 : JCasUtil.selectCovering(jCas, UimaBioCAnnotation.class, a.getBegin(),
				a.getEnd())) {
			Map<String, String> infons = UimaBioCUtils.convertInfons(a1.getInfons());
			if (infons.containsKey("sectionHeading") && a1.getBegin() == a.getBegin()) {
				return a1;
			}
		}

		return null;

	}

	private int readHeadingLevel(JCas jCas, UimaBioCAnnotation a, int level) throws StackOverflowError {

		// Looking for section headings
		for (UimaBioCAnnotation a1 : JCasUtil.selectCovering(jCas, UimaBioCAnnotation.class, a.getBegin(),
				a.getEnd())) {
			if (a1.equals(a))
				continue;
			Map<String, String> infons = UimaBioCUtils.convertInfons(a1.getInfons());
			if (infons.containsKey("sectionHeading")) {
				level = readHeadingLevel(jCas, a1, level + 1);
				return level;
			}
		}

		return level;

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

}
