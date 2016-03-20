package edu.isi.bmkeg.uimaBioC;

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
import org.uimafit.util.JCasUtil;

import bioc.BioCAnnotation;
import bioc.BioCDocument;
import bioc.BioCLocation;
import bioc.BioCNode;
import bioc.BioCPassage;
import bioc.BioCRelation;
import bioc.BioCSentence;
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
				
				} catch (Exception e) {
					
					logger.error("ERROR in annotation: " + uiD.getId() + 
							":(" + uiA.getBegin() + "-" + uiA.getEnd() + ")" + inf.toString() + "\n" +
							e.toString() + " - SKIPPING FROM SAVE");
					continue;
					
				}

				p.addAnnotation(a);

				BioCLocation l = new BioCLocation();
				l.setOffset(uiA.getBegin());
				l.setLength(uiA.getEnd() - uiA.getBegin());
				a.addLocation(l);

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

			FSArray sentences = uiP.getSentences();
			if (sentences != null) {
				for (int j = 0; j < sentences.size(); j++) {
					UimaBioCSentence uiS = (UimaBioCSentence) sentences.get(j);

					BioCSentence s = new BioCSentence();
					s.setInfons(convertInfons(uiS.getInfons()));
					s.setOffset(uiS.getOffset());
					// s.setText(uiS.getCoveredText());
					p.addSentence(s);

				}
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
				if (inf.equals("bold") || inf.equals("italic") || inf.equals("sup") || inf.equals("sub")) {
					continue;
				}

				UimaBioCAnnotation uiA = convertBioCAnnotation(a, jcas);
				annotations.set(count, uiA);
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
					&& (infons.get("value").equals("p") || infons.get("value").equals("title"))) {
				parags.add(a);
			}

		}

		return parags;

	}

	public static String friesifySentence(JCas jCas, Sentence s) {

		String text = s.getCoveredText();

		UimaBioCDocument uiD = JCasUtil.selectSingle(jCas, UimaBioCDocument.class);
		for (UimaBioCAnnotation a : JCasUtil.selectCovered(jCas, UimaBioCAnnotation.class, s)) {
			Map<String, String> infons = UimaBioCUtils.convertInfons(a.getInfons());
			if (infons.containsKey("refType") && 
					infons.containsKey("value") && 
					infons.get("value").equals("xref")) {
				if (infons.get("refType").equals("fig") ||
						infons.get("refType").equals("bibr")) {
					
					// Can we find enclosing brackets for Figures?
					String s1 = text.substring(0, a.getBegin() - s.getBegin());
					/*Matcher m1 = bra.matcher(s1);

					if (m1.find()) {
						
						String r1 = m1.group(1);
						if( s1.endsWith("(") )
							r1 = "(";

						String s2 = text.substring(a.getEnd() - s.getBegin(), text.length());
						Matcher m2 = ket.matcher(s2);
						if (m2.find()) {
							String r2 = m2.group(1);
							if( s2.startsWith(")") )
								r2 = ")";

							int gapLength = r1.length() + r2.length() + a.getEnd() - a.getBegin();
							text = s1.substring(0, s1.length()-r1.length()) +
									StringUtils.leftPad("", gapLength, ' ') +
									s2.substring(r2.length(), s2.length());
							
						}
						
					} else {*/
						
						String s2 = text.substring(a.getEnd() - s.getBegin(), text.length());
						text = s1 + StringUtils.leftPad("", a.getEnd() - a.getBegin(), ' ') + s2;
						
					//}

				}
			}
		}
		
		text = text.replaceAll("(Figure|Fig.)" , "");
		text = text.replaceAll("\\([,;\\s]*\\)" , "");
		text = text.replaceAll("\\s{2,}", "  ");
		
		return text;

	}

	public static List<UimaBioCAnnotation> readAllReadablePassagesAndFloats(JCas jCas) {

		UimaBioCDocument uiD = JCasUtil.selectSingle(jCas, UimaBioCDocument.class);
		Set<UimaBioCAnnotation> passages = new HashSet<UimaBioCAnnotation>();
		for (UimaBioCAnnotation a : JCasUtil.selectCovered(jCas, UimaBioCAnnotation.class, uiD)) {
			Map<String, String> infons = UimaBioCUtils.convertInfons(a.getInfons());
			if (infons.containsKey("value")
					&& (infons.get("value").equals("p") || infons.get("value").equals("title"))) {
				passages.add(a);
			} else if (infons.get("type").equals("formatting") && infons.get("value").equals("abstract")) {
				passages.add(a);
			} else if(infons.get("type").equals("formatting") && infons.get("value").equals("article-title")) {
				passages.add(a);
			} else if(infons.containsKey("position") && infons.get("position").equals("float")) {
				passages.add(a);
			}

		}
				
		List<UimaBioCAnnotation> pList = new ArrayList<UimaBioCAnnotation>(passages);
		
		Collections.sort(pList, UimaBioCUtils.AnnotationComparator);
		
		return pList;

	}
	
	public static List<Sentence> readAllReadableSentences(JCas jCas) {

		Set<Sentence> sSet = new HashSet<Sentence>();
		
		Set<UimaBioCAnnotation> pSet = new HashSet<UimaBioCAnnotation>(
				readAllReadablePassagesAndFloats(jCas));
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
		
		for (Sentence s : JCasUtil.selectCovered(Sentence.class, uiD) ) {			
			logger.debug(s.getBegin() + " " + s.getEnd() + " " + s.getCoveredText());		
		}
			
	}
	
	public static void debugSentences(JCas jCas, List<Sentence> sentences) {

		for (Sentence s : sentences ) {			
			logger.debug(s.getBegin() + " " + s.getEnd() + " " + s.getCoveredText());		
		}
			
	}
	
	public static Comparator<Annotation> AnnotationComparator = new Comparator<Annotation>() {
		public int compare(Annotation s1, Annotation s2) {
			return s1.getBegin() - s2.getBegin();
		}
	};

}
