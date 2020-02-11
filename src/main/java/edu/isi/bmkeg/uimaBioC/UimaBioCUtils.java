package edu.isi.bmkeg.uimaBioC;

import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.FSArray;
import org.apache.uima.jcas.tcas.Annotation;
import org.cleartk.token.type.Sentence;
import org.cleartk.token.type.Token;
import org.uimafit.util.JCasUtil;

import com.google.gson.Gson;

import bioc.BioCAnnotation;
import bioc.BioCDocument;
import bioc.BioCLocation;
import bioc.BioCNode;
import bioc.BioCPassage;
import bioc.BioCRelation;
import bioc.BioCSentence;
import bioc.io.BioCDocumentReader;
import bioc.io.BioCFactory;
import bioc.type.MapEntry;
import bioc.type.UimaBioCAnnotation;
import bioc.type.UimaBioCDocument;
import bioc.type.UimaBioCLocation;
import bioc.type.UimaBioCNode;
import bioc.type.UimaBioCPassage;
import bioc.type.UimaBioCRelation;
import bioc.type.UimaBioCSentence;

public class UimaBioCUtils {

	private static Logger logger = Logger.getLogger(UimaBioCUtils.class);

	public static Pattern bra = Pattern.compile("(\\(.*?)\\Z");
	public static Pattern ket = Pattern.compile("\\A(.*?\\))");
	public static Pattern doiPatt = Pattern.compile("^[\\d\\.]+\\/\\S+$");
	public static String XML = "xml";
	public static String JSON = "json";

	public static BioCAnnotation convertUimaBioCAnnotation(UimaBioCAnnotation uiA, JCas jCas) {

		BioCAnnotation a = new BioCAnnotation();
		a.setInfons(convertInfons(uiA.getInfons()));
		a.setID(uiA.getId());

		Map<String, String> inf = a.getInfons();
		if( inf.containsKey("type") && !inf.get("type").equals("formatting") )
			a.setText(uiA.getCoveredText());
		else if(inf.containsKey("rdf:type") )
			a.setText(uiA.getCoveredText());
		
		FSArray locations = uiA.getLocations();
		if (locations != null) {
			for (int j = 0; j < locations.size(); j++) {
				UimaBioCLocation uiL = uiA.getLocations(j);
				BioCLocation l = new BioCLocation();
				l.setOffset(uiL.getOffset());
				l.setLength(uiL.getLength());
				a.addLocation(l);
			}
		}

		return a;

	}
	
	public static boolean isAnnotationInTableOrFigure(UimaBioCAnnotation a, JCas jCas) {

		for (UimaBioCAnnotation a1 : JCasUtil.selectCovering(jCas, 
				UimaBioCAnnotation.class, a.getBegin(), a.getEnd())) {
			Map<String, String> infons = UimaBioCUtils.convertInfons(a1.getInfons());
			if ( infons.get("value").equals("fig") || 
					infons.get("value").equals("table-wrap") ) {
				return true;
			}
		}

		return false;

	}

	public static BioCDocument convertUimaBioCDocument(UimaBioCDocument uiD, JCas jCas) {

		BioCDocument d = new BioCDocument();
		d.setID(uiD.getId());
		d.setInfons(convertInfons(uiD.getInfons()));

		for (UimaBioCPassage uiP : JCasUtil.selectCovered(jCas, UimaBioCPassage.class, uiD)) {

			BioCPassage p = new BioCPassage();
			p.setInfons(convertInfons(uiP.getInfons()));
			p.setOffset(uiP.getOffset());
			d.addPassage(p);

			Map<String, String> inf = p.getInfons();
			if ((inf.containsKey("type") && inf.get("type").equals("document"))
					|| (inf.containsKey("value") && inf.get("value").equals("document")))
				p.setText(uiP.getCoveredText());

			for (UimaBioCAnnotation uiA : JCasUtil.selectCovered(jCas, UimaBioCAnnotation.class, uiP)) {

				BioCAnnotation a = new BioCAnnotation();
				a.setInfons(convertInfons(uiA.getInfons()));
				a.setID(uiA.getId());

				try {

					inf = a.getInfons();
					if (inf.containsKey("type") && !inf.get("type").equals("formatting"))
						a.setText(uiA.getCoveredText());
					else if (inf.containsKey("rdf:type"))
						a.setText(uiA.getCoveredText());

				} catch (Exception e) {

					logger.error("ERROR in annotation: " + uiD.getId() + ":(" + uiA.getBegin() + "-" + uiA.getEnd()
							+ ")" + inf.toString() + "\n" + e.toString() + " - SKIPPING FROM SAVE");
					continue;

				}

				BioCLocation l = new BioCLocation();
				l.setOffset(uiA.getBegin());
				l.setLength(uiA.getEnd() - uiA.getBegin());
				a.addLocation(l);

				p.addAnnotation(a);

			}

			FSArray relations = uiP.getRelations();
			if (relations != null) {
				for (int j = 0; j < relations.size(); j++) {
					UimaBioCRelation uiR = (UimaBioCRelation) relations.get(j);

					BioCRelation r = new BioCRelation();
					r.setID(uiR.getId());
					p.addRelation(r);

					FSArray nodes = uiR.getNodes();
					for (int k = 0; k < nodes.size(); k++) {
						UimaBioCNode uiN = (UimaBioCNode) nodes.get(k);

						BioCNode n = new BioCNode();
						n.setRefid(uiN.getRefid());
						n.setRole(uiN.getRole());
						r.addNode(n);

					}

				}
			}

			for (UimaBioCSentence uiS : JCasUtil.selectCovered(jCas, UimaBioCSentence.class, uiP)) {
				BioCSentence s = new BioCSentence();
				s.setInfons(convertInfons(uiS.getInfons()));
				s.setOffset(uiS.getBegin());
				s.setText(uiS.getCoveredText());
				p.addSentence(s);
			}

		}

		return d;

	}

	public static void addBioCDocumentToUimaCas(BioCDocument d, JCas jcas) {

		UimaBioCDocument uiD = new UimaBioCDocument(jcas);
		for (BioCPassage bioP : d.getPassages()) {
			if ((bioP.getInfons().containsKey("type") && bioP.getInfons().get("type").equals("document"))
					|| (bioP.getInfons().containsKey("value") && bioP.getInfons().get("value").equals("document"))) {
				uiD.setBegin(0);
				uiD.setEnd(bioP.getText().length());
				jcas.setDocumentText(bioP.getText());
			}
		}

		uiD.setInfons(UimaBioCUtils.convertInfons(d.getInfons(), jcas));
		uiD.setId(d.getID());

		if (d.getPassages() != null) {
			FSArray passages = new FSArray(jcas, d.getPassages().size());
			int count = 0;
			for (BioCPassage p : d.getPassages()) {
				UimaBioCPassage uiP = convertBioCPassage(p, jcas);
				passages.set(count, uiP);
				count++;
			}
			uiD.setPassages(passages);
		}

		uiD.addToIndexes();

	}

	public static void addBioCDocumentToExistingUimaBioCDocument(BioCDocument d, JCas jCas) {

		UimaBioCDocument uiD = JCasUtil.selectSingle(jCas, UimaBioCDocument.class);
		UimaBioCPassage uiP = UimaBioCUtils.readDocument(jCas);
		FSArray annotations = uiP.getAnnotations();

		BioCPassage p = d.getPassages().get(0);
		int extra = p.getAnnotations().size();
		FSArray newAnnotations = new FSArray(jCas, annotations.size() + extra);

		for (int i = 0; i < annotations.size(); i++) {
			newAnnotations.set(i, annotations.get(i));
		}

		int count = 0;
		ADD_ANNOTATION: for (BioCAnnotation a : p.getAnnotations()) {

			// check for matches with any annotations that exist already
			BioCLocation loc = a.getLocations().get(0);
			int s = loc.getOffset();
			int e = loc.getOffset() + loc.getLength();
			List<UimaBioCAnnotation> existingAnnotations = JCasUtil.selectCovered(jCas, UimaBioCAnnotation.class, s, e);
			for (UimaBioCAnnotation ea : existingAnnotations) {
				if (false) // FIX THIS. NEED TO MAKE SURE THAT THIS WORKS.
					continue ADD_ANNOTATION;
			}

			UimaBioCAnnotation uiA = convertBioCAnnotation(a, jCas);
			annotations.set(count, uiA);
			count++;
			uiA.addToIndexes();
		}

	}


	public static UimaBioCPassage convertBioCPassage(BioCPassage p, JCas jcas) {

		UimaBioCPassage uiP = new UimaBioCPassage(jcas);

		uiP.setInfons(convertInfons(p.getInfons(), jcas));
		uiP.setOffset(p.getOffset());
		uiP.setText(p.getText());

		uiP.setBegin(p.getOffset());
		uiP.setEnd(p.getOffset() + p.getText().length());

		if (p.getAnnotations() != null) {
			FSArray annotations = new FSArray(jcas, p.getAnnotations().size());
			uiP.setAnnotations(annotations);
			int count = 0;
			for (BioCAnnotation a : p.getAnnotations()) {

				// skip simple formatting information
				String inf = a.getInfons().get("value");
				if (inf == null || inf.equals("bold") || inf.equals("italic") || inf.equals("sup") || inf.equals("sub")) {
					continue;
				}

				UimaBioCAnnotation uiA = convertBioCAnnotation(a, jcas);
				annotations.set(count, uiA);
				count++;
			}
		}

		if (p.getSentences() != null) {
			FSArray sentences = new FSArray(jcas, p.getSentences().size());
			uiP.setSentences(sentences);
			int count = 0;
			for (BioCSentence s : p.getSentences()) {

				// skip simple formatting information
				String inf = s.getInfons().get("value");
				if (inf.equals("bold") || inf.equals("italic") || inf.equals("sup") || inf.equals("sub")) {
					continue;
				}

				UimaBioCSentence uiS = convertBioCSentence(s, jcas);
				sentences.set(count, uiS);
				count++;
			}
		}

		uiP.addToIndexes();

		return uiP;

	}

	public static UimaBioCAnnotation convertBioCAnnotation(BioCAnnotation a, JCas jcas) {

		UimaBioCAnnotation uiA = new UimaBioCAnnotation(jcas);

		uiA.setInfons(convertInfons(a.getInfons(), jcas));
		uiA.setId(a.getID());
		uiA.setText(a.getText());

		if (a.getLocations() != null) {
			FSArray locations = new FSArray(jcas, a.getLocations().size());
			uiA.setLocations(locations);
			int count = 0;
			for (BioCLocation l : a.getLocations()) {
				UimaBioCLocation uiL = new UimaBioCLocation(jcas);
				uiL.setOffset(l.getOffset());
				uiL.setLength(l.getLength());
				locations.set(count, uiL);
				count++;

				uiA.setBegin(l.getOffset());
				uiA.setEnd(l.getOffset() + l.getLength());

				uiL.setBegin(l.getOffset());
				uiL.setEnd(l.getOffset() + l.getLength());

				uiL.addToIndexes();

			}
		}

		uiA.addToIndexes();

		return uiA;

	}

	public static UimaBioCSentence convertBioCSentence(BioCSentence s, JCas jcas) {

		UimaBioCSentence uiS = new UimaBioCSentence(jcas);

		uiS.setInfons(convertInfons(s.getInfons(), jcas));
		uiS.setOffset(s.getOffset());
		uiS.setText(s.getText());

		uiS.addToIndexes();

		return uiS;

	}

	public static Map<String, String> convertInfons(FSArray fsArray) {
		Map<String, String> map = new HashMap<String, String>();
		if (fsArray != null) {
			for (int i = 0; i < fsArray.size(); i++) {
				MapEntry me = (MapEntry) fsArray.get(i);
				map.put(me.getKey(), me.getValue());
			}
		}
		return map;
	}

	public static FSArray convertInfons(Map<String, String> infons, JCas jcas) {
		FSArray fsArray = new FSArray(jcas, infons.size());
		int count = 0;
		if (infons != null) {
			for (String key : infons.keySet()) {
				String value = infons.get(key);
				MapEntry me = new MapEntry(jcas);
				me.setKey(key);
				me.setValue(value);
				fsArray.set(count, me);
				count++;
			}
		}
		return fsArray;
	}

	public static String readInfons(FSArray fsArray, String key) {

		Map<String, String> infons = UimaBioCUtils.convertInfons(fsArray);
		if (infons.containsKey(key))
			return infons.get(key);
		else
			return null;
	}

	public static UimaBioCPassage readDocument(JCas jCas) {

		UimaBioCDocument uiD = JCasUtil.selectSingle(jCas, UimaBioCDocument.class);
		for (UimaBioCPassage p : JCasUtil.selectCovered(UimaBioCPassage.class, uiD)) {
			Map<String, String> infons = UimaBioCUtils.convertInfons(p.getInfons());
			if (infons.get("type").equals("document")) {
				return p;
			}
		}

		return null;

	}

	public static UimaBioCAnnotation readArticleTitle(JCas jCas) {

		UimaBioCDocument uiD = JCasUtil.selectSingle(jCas, UimaBioCDocument.class);
		for (UimaBioCAnnotation a : JCasUtil.selectCovered(UimaBioCAnnotation.class, uiD)) {
			Map<String, String> infons = UimaBioCUtils.convertInfons(a.getInfons());
			if (infons.get("type").equals("formatting") && infons.get("value").equals("article-title")) {
				return a;
			}
		}

		return null;

	}

	public static UimaBioCAnnotation readAbstract(JCas jCas) {

		UimaBioCDocument uiD = JCasUtil.selectSingle(jCas, UimaBioCDocument.class);
		for (UimaBioCAnnotation a : JCasUtil.selectCovered(UimaBioCAnnotation.class, uiD)) {
			Map<String, String> infons = UimaBioCUtils.convertInfons(a.getInfons());
			if (infons.get("type").equals("formatting") && infons.get("value").equals("abstract")) {
				return a;
			}
		}

		return null;

	}

	public static List<UimaBioCAnnotation> readFloats(JCas jCas) {

		UimaBioCDocument uiD = JCasUtil.selectSingle(jCas, UimaBioCDocument.class);
		List<UimaBioCAnnotation> floats = new ArrayList<UimaBioCAnnotation>();
		for (UimaBioCAnnotation a : JCasUtil.selectCovered(jCas, UimaBioCAnnotation.class, uiD)) {
			Map<String, String> infons = UimaBioCUtils.convertInfons(a.getInfons());
			if (infons.containsKey("position") && infons.get("position").equals("float")) {
				floats.add(a);
			}

		}

		return floats;

	}

	public static List<UimaBioCAnnotation> readParags(JCas jCas) {

		UimaBioCDocument uiD = JCasUtil.selectSingle(jCas, UimaBioCDocument.class);
		List<UimaBioCAnnotation> parags = new ArrayList<UimaBioCAnnotation>();
		for (UimaBioCAnnotation a : JCasUtil.selectCovered(jCas, UimaBioCAnnotation.class, uiD)) {
			Map<String, String> infons = UimaBioCUtils.convertInfons(a.getInfons());
			if (infons.containsKey("value")
					&& (infons.get("value").equals("p") || 
							infons.get("value").equals("title") || 
							infons.get("value").equals("label") || 
							infons.get("value").equals("article-title")) 
					&& (a.getEnd()-a.getBegin()) > 1) {
				parags.add(a);
			}

		}

		return parags;

	}

	public static List<UimaBioCAnnotation> readAllReadablePassagesAndFloats(JCas jCas) {

		UimaBioCDocument uiD = JCasUtil.selectSingle(jCas, UimaBioCDocument.class);
		Set<UimaBioCAnnotation> passages = new HashSet<UimaBioCAnnotation>();

		for (UimaBioCAnnotation section : JCasUtil.selectCovered(jCas, UimaBioCAnnotation.class, uiD)) {

			Map<String, String> infons = UimaBioCUtils.convertInfons(section.getInfons());
			if ((infons.get("type").equals("formatting") && infons.get("value").equals("article-title"))
					|| (infons.get("type").equals("formatting") && infons.get("value").equals("abstract"))) {

				passages.add(section);

			} else if (infons.get("type").equals("formatting") && infons.get("value").equals("body")) {

				for (UimaBioCAnnotation a : JCasUtil.selectCovered(jCas, UimaBioCAnnotation.class, section)) {
					Map<String, String> infons2 = UimaBioCUtils.convertInfons(a.getInfons());

					if (infons2.containsKey("value")
							&& (infons2.get("value").equals("p") || infons2.get("value").equals("title"))) {

						passages.add(a);

					}
					// note that we discard labels for 'Tables'
					else if (infons2.get("type").equals("formatting") && infons2.get("value").equals("label")
							&& !a.getCoveredText().toLowerCase().startsWith("table")) {

						passages.add(a);

					} else if (infons2.get("type").equals("formatting") && infons2.get("value").equals("caption")) {

						passages.add(a);

					}

				}

			}

		}

		List<UimaBioCAnnotation> pList = new ArrayList<UimaBioCAnnotation>(passages);

		Collections.sort(pList, UimaBioCUtils.AnnotationComparator);

		return pList;

	}

	public static List<Sentence> readAllReadableSentences(JCas jCas) {

		Set<Sentence> sSet = new HashSet<Sentence>();

		Set<UimaBioCAnnotation> pSet = new HashSet<UimaBioCAnnotation>(readAllReadablePassagesAndFloats(jCas));
		List<UimaBioCAnnotation> passages = new ArrayList<UimaBioCAnnotation>(pSet);
		Collections.sort(passages, UimaBioCUtils.AnnotationComparator);

		for (UimaBioCAnnotation p : passages) {
			sSet.addAll(JCasUtil.selectCovered(jCas, Sentence.class, p));
		}
		List<Sentence> sentences = new ArrayList<Sentence>(sSet);
		Collections.sort(sentences, UimaBioCUtils.AnnotationComparator);

		return sentences;

	}

	public static List<Sentence> readAllFloatSentences(JCas jCas) {

		List<Sentence> sentences = new ArrayList<Sentence>();

		List<UimaBioCAnnotation> passages = readFloats(jCas);
		for (UimaBioCAnnotation p : passages) {
			sentences.addAll(JCasUtil.selectCovered(jCas, Sentence.class, p));
		}

		return sentences;

	}

	public static void debugSentences(JCas jCas) {

		UimaBioCDocument uiD = JCasUtil.selectSingle(jCas, UimaBioCDocument.class);

		for (Sentence s : JCasUtil.selectCovered(Sentence.class, uiD)) {
			logger.debug(s.getBegin() + " " + s.getEnd() + " " + s.getCoveredText());
		}

	}

	public static void debugSentences(JCas jCas, List<Sentence> sentences) {

		for (Sentence s : sentences) {
			logger.debug(s.getBegin() + " " + s.getEnd() + " " + s.getCoveredText());
		}

	}

	public static Comparator<Annotation> AnnotationComparator = new Comparator<Annotation>() {
		public int compare(Annotation s1, Annotation s2) {
			return s1.getBegin() - s2.getBegin();
		}
	};

	public static UimaBioCAnnotation createNewAnnotation(JCas jcas, int begin, int end, Map<String, String> infons) {

		UimaBioCAnnotation uiA = new UimaBioCAnnotation(jcas);
		uiA.setBegin(begin);
		uiA.setEnd(end);

		uiA.setInfons(UimaBioCUtils.convertInfons(infons, jcas));
		uiA.addToIndexes();

		FSArray locations = new FSArray(jcas, 1);
		uiA.setLocations(locations);
		UimaBioCLocation uiL = new UimaBioCLocation(jcas);
		locations.set(0, uiL);
		uiL.setOffset(begin);
		uiL.setLength(end - begin);

		return uiA;

	}

	public static String readUntokenizedText(JCas jCas, Annotation a) {
		
		String txt = a.getCoveredText();

		//
		// Look for exLinks and remove the text associated with them.
		//
		List<UimaBioCAnnotation> bibRefs = new ArrayList<UimaBioCAnnotation>();
		for (UimaBioCAnnotation a1 : JCasUtil.selectCovered(jCas, UimaBioCAnnotation.class, a.getBegin(), a.getEnd())) {
			Map<String, String> infons = UimaBioCUtils.convertInfons(a1.getInfons());
			if (infons.containsKey("refType") && infons.get("refType").startsWith("bib")) {
				String pre = txt.substring(0, a1.getBegin()-a.getBegin());
				String filler = StringUtils.leftPad("", a1.getCoveredText().length(), ' '); 
				String post = txt.substring(a1.getEnd()-a.getBegin(), txt.length());
				txt = pre+filler+post;
				int i = 1;
				i++;
			}
		}

		return txt;

	}
	
	
	public static String readTokenizedText(JCas jCas, Annotation a) {
		String txt = "";

		// Look for exLinks
		List<UimaBioCAnnotation> bibRefs = new ArrayList<UimaBioCAnnotation>();
		for (UimaBioCAnnotation a1 : JCasUtil.selectCovered(jCas, UimaBioCAnnotation.class, a.getBegin(), a.getEnd())) {
			Map<String, String> infons = UimaBioCUtils.convertInfons(a1.getInfons());
			if (infons.containsKey("refType") && infons.get("refType").startsWith("bib")) {
				bibRefs.add(a1);
			}
		}

		String lastToken = "";
		for (Token t : JCasUtil.selectCovered(jCas, Token.class, a)) {

			boolean noRef = true;
			for (UimaBioCAnnotation bibRef : bibRefs) {
				if (t.getBegin() >= bibRef.getBegin() && t.getEnd() <= bibRef.getEnd()) {
					noRef = false;
					break;
				}
			}

			if (noRef) {
				txt += t.getCoveredText() + " ";
				lastToken = t.getCoveredText();
			} else if (lastToken != "exLink") {
				txt += "exLink" + " ";
				lastToken = "exLink";
			}

		}

		if (txt.length() == 0)
			return txt;
		return txt.substring(0, txt.length() - 1);
	}

	public static UimaBioCAnnotation readPrecedingClause(JCas jCas, UimaBioCAnnotation clause) {

		List<UimaBioCAnnotation> precedingList = JCasUtil.selectPreceding(jCas, UimaBioCAnnotation.class, clause, 100);
		for (UimaBioCAnnotation precede : precedingList) {
			Map<String, String> infons = UimaBioCUtils.convertInfons(precede.getInfons());
			if (infons.get("type").equals("rubicon") && infons.get("value").equals("clause")
					&& infons.containsKey("scidp-experiment-labels")) {
				return precede;
			}
		}

		return null;

	}

	public static UimaBioCAnnotation readFollowingClause(JCas jCas, UimaBioCAnnotation clause) {

		List<UimaBioCAnnotation> followingingList = JCasUtil.selectFollowing(jCas, UimaBioCAnnotation.class, clause,
				10);
		for (UimaBioCAnnotation follow : followingingList) {
			Map<String, String> infons = UimaBioCUtils.convertInfons(follow.getInfons());
			if (infons.get("type").equals("rubicon") && infons.get("value").equals("clause")
					&& infons.containsKey("scidp-experiment-labels")) {
				return follow;
			}
		}

		return null;

	}

	public static String stringify(UimaBioCAnnotation a) {
		Map<String, String> infons = UimaBioCUtils.convertInfons(a.getInfons());
		String s = infons.toString();
		s += "[" + a.getBegin() + ":" + a.getEnd() + "]";
		return s;
	}

	public static String stringify(BioCAnnotation a) {
		String s = a.getInfons().toString();
		BioCLocation loc = a.getLocations().get(0);
		int start = loc.getOffset();
		int end = loc.getOffset() + loc.getLength();
		s += "[" + start + ":" + end + "]";
		return s;
	}

	public static BioCDocument readBioCFile(File bioCFile) throws Exception {

		BioCDocument bioD;
		String fn = bioCFile.getName();
		String suffix = fn.substring(fn.lastIndexOf(".") + 1);
		if (suffix.equals(XML)) {

			BioCDocumentReader reader = BioCFactory.newFactory(BioCFactory.STANDARD)
					.createBioCDocumentReader(new FileReader(bioCFile));

			bioD = reader.readDocument();

			reader.close();

		} else if (suffix.equals(JSON)) {

			Gson gson = new Gson();
			bioD = gson.fromJson(new FileReader(bioCFile), BioCDocument.class);

		} else {

			throw new Exception("Please write to an *.xml or a *.json file");

		}
		return bioD;
	}

	public static Set<String> extractExptsFromClause(JCas jCas, Annotation clause) {
		Set<String> expts = new HashSet<String>();
		for (UimaBioCAnnotation a : JCasUtil.selectCovered(UimaBioCAnnotation.class, clause)) {
			Map<String, String> infons = UimaBioCUtils.convertInfons(a.getInfons());
			if( infons.containsKey("refType") ) {
				if( infons.get("refType").equals("fig") || 
						infons.get("refType").equals("supplementary-material")) {
					String exptCodes = readExptCodes(jCas, a);
					expts.add(exptCodes);
				}
			} 
		}
		return expts;
	}
	
	public static Set<String> extractCodesFromClause(JCas jCas, Annotation clause) {
		Set<String> codes = new HashSet<String>();
		for (UimaBioCAnnotation a : JCasUtil.selectCovered(UimaBioCAnnotation.class, clause)) {
			Map<String, String> infons = UimaBioCUtils.convertInfons(a.getInfons());
			if (infons.containsKey("refType") && infons.get("refType").startsWith("bib")) {
				codes.add("exLink");
			} else if (infons.containsKey("refType") && infons.get("refType").equals("fig")) {
				codes.add("inLink");
			} else if (infons.containsKey("refType") && infons.get("refType").equals("supplementary-material")) {
				codes.add("inLink");
			} else if (infons.get("value").equals("label")) {
				codes.add("label");
			}
		}
		return codes;
	}

	public static Set<String> extractPmidFromReference(JCas jCas, Annotation clause) {
		Set<String> pmids = new HashSet<String>();
		for (UimaBioCAnnotation a : JCasUtil.selectCovered(UimaBioCAnnotation.class, clause)) {
			Map<String, String> infons = UimaBioCUtils.convertInfons(a.getInfons());
			if (infons.containsKey("refType") && infons.get("refType").startsWith("bib") && 
					infons.containsKey("pmid")) {
				pmids.add(infons.get("pmid"));
			} 
		}
		return pmids;
	}

	public static String readExptCodes(JCas jCas, UimaBioCAnnotation s) {

		String exptCode = s.getCoveredText();
		int offset = 2;
		if (exptCode.toLowerCase().startsWith("fig")) {
			offset = 1;
		}

		List<Token> l = JCasUtil.selectCovered(Token.class, s);
		List<Token> f = JCasUtil.selectFollowing(jCas, Token.class, s, 10);
		List<Token> p = JCasUtil.selectPreceding(jCas, Token.class, s, offset);
		
		Token start;
		Token end;
		try {
			start = p.get(0);
			end = f.get(9);
		} catch (Exception e) {
			e.printStackTrace();
			return "";
		}
		String figFrag = jCas.getDocumentText().substring(start.getBegin(), end.getEnd());

		FigurePatternList fpl = FigurePatternList.getInstance();
		for (Pattern patt : fpl.figPatt) {
			Matcher m = patt.matcher(figFrag);
			if (m.find()) {
				
				// use group 2 since all
				exptCode = m.group(2).replaceAll("\\n", "");
				return exptCode;
				
			}
		}

		return exptCode;
	}

	public static UimaBioCAnnotation readSectionHeading(JCas jCas, UimaBioCAnnotation a) {

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

	public static int readHeadingLevel(JCas jCas, UimaBioCAnnotation a, int level) throws StackOverflowError {

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
	
	public static String readHeadingString(JCas jCas, UimaBioCAnnotation a, String heading) throws StackOverflowError {

		// Looking for section headings
		for (UimaBioCAnnotation a1 : JCasUtil.selectCovering(jCas, UimaBioCAnnotation.class, a.getBegin(),
				a.getEnd())) {
			if (a1.equals(a))
				continue;
			Map<String, String> infons = UimaBioCUtils.convertInfons(a1.getInfons());
			if (infons.containsKey("sectionHeading")) {
				if( !heading.equals("") )
					heading += "|";
				return heading + infons.get("sectionHeading");
			}
		}

		return heading;

	}

	public static String readHeadingString(JCas jCas, Sentence a, String heading) throws StackOverflowError {

		// Looking for section headings
		for (UimaBioCAnnotation a1 : JCasUtil.selectCovering(jCas, UimaBioCAnnotation.class, a.getBegin(),
				a.getEnd())) {
			if (a1.equals(a))
				continue;
			Map<String, String> infons = UimaBioCUtils.convertInfons(a1.getInfons());
			if (infons.containsKey("sectionHeading")) {
				if( !heading.equals("") )
					heading += "|";
				return heading + infons.get("sectionHeading");
			} else if(infons.get("value").equals("article-title") ||
					infons.get("value").equals("abstract") ) {
				return infons.get("value");
			}

		}

		return heading;

	}
	
	public static String readTokenizedText(JCas jCas, Sentence s) {
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
