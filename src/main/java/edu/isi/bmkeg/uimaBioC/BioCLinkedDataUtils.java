package edu.isi.bmkeg.uimaBioC;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.jena.ontology.DatatypeProperty;
import org.apache.jena.ontology.Individual;
import org.apache.jena.ontology.ObjectProperty;
import org.apache.jena.ontology.OntClass;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.log4j.Logger;

import bioc.BioCAnnotation;
import bioc.BioCDocument;
import bioc.BioCLocation;
import bioc.BioCPassage;
import bioc.BioCSentence;
import edu.isi.bmkeg.utils.MapCreate;

public class BioCLinkedDataUtils {

	private static Logger logger = Logger.getLogger(BioCLinkedDataUtils.class);

	public static Pattern bra = Pattern.compile("(\\(.*?)\\Z");
	public static Pattern ket = Pattern.compile("\\A(.*?\\))");
	public static Pattern doiPatt = Pattern.compile("^[\\d\\.]+\\/\\S+$");

	static Map<String, String> nsLookup = new HashMap<String, String>(
			MapCreate.asMap(new String[] { 
						"owl",
						"rdf",
						"xsd",
						"bioc",
						"rdfs",
						"vann",
						"schema",
						"dcterms",
						"prov",
						"biopax",
						"doco",
						"cito"
					}, new String[] {
						"http://www.w3.org/2002/07/owl#",
						"http://www.w3.org/1999/02/22-rdf-syntax-ns#",
						"http://www.w3.org/2001/XMLSchema#",
						"http://purl.org/bioc/",
						"http://www.w3.org/2000/01/rdf-schema#",
						"http://purl.org/vocab/vann/",
						"http://schema.org/",
						"http://purl.org/dc/terms/",
						"http://www.w3.org/ns/prov#",
						"http://www.biopax.org/release/biopax-level3.owl#",
						"http://purl.org/spar/doco/",
						"http://purl.org/spar/cito/"
					}));
	
	public static OntModel convertBioCDocumentToLinkedData(OntModel m, BioCDocument d, String rootNS) throws Exception {
		
		m.setNsPrefix("", rootNS);
		m.setNsPrefix("owl", nsLookup.get("owl"));
		m.setNsPrefix("rdf", nsLookup.get("rdf"));
		m.setNsPrefix("xsd", nsLookup.get("xsd"));
		m.setNsPrefix("bioc", nsLookup.get("bioc"));
		m.setNsPrefix("rdfs", nsLookup.get("rdfs"));
		m.setNsPrefix("vann", nsLookup.get("vann"));
		m.setNsPrefix("schema", nsLookup.get("schema"));
		m.setNsPrefix("dcterms", nsLookup.get("dcterms"));
		m.setNsPrefix("prov", nsLookup.get("prov"));
		m.setNsPrefix("biopax", nsLookup.get("biopax"));
		m.setNsPrefix("doco", nsLookup.get("doco"));
		m.setNsPrefix("cito", nsLookup.get("cito"));
		
		if(!rootNS.endsWith("/") && !rootNS.endsWith("#") )
			rootNS += "#";
		
		OntClass ldBiocD_class = m.createClass(nsLookup.get("bioc")+"Document");		
		Individual ldBiocD = m.createIndividual(rootNS + "pmid" + d.getID(), ldBiocD_class);
		
		int pCount = 0;
		for (BioCPassage biocP : d.getPassages()) {
			Individual ldBiocP = convertBioCPassage(m, biocP, rootNS, d.getID(), pCount+"");
			Property bioc_passages = m.createProperty(nsLookup.get("bioc")+"passages");
			m.add(ldBiocD, bioc_passages, ldBiocP);	
			pCount++;
		}
		
		return m;

	}
	
	public static Individual convertBioCPassage(OntModel m, BioCPassage p, String rootNS, String did, String pid) throws Exception {

		OntClass ldBiocP_class = m.createClass(nsLookup.get("bioc")+"Passage");
		Individual ldBiocP = m.createIndividual(rootNS+"pmid"+did+"_p"+pid, ldBiocP_class);

		addInfonsAsProperties(m, ldBiocP, p.getInfons() );

		Property bioc_offset = m.createProperty(nsLookup.get("bioc")+"offset");
		m.addLiteral(ldBiocP, bioc_offset, p.getOffset());	

		ObjectProperty bioc_text = m.createObjectProperty(nsLookup.get("bioc")+"text");
		m.add(ldBiocP, bioc_text, p.getText());	

		if (p.getAnnotations() != null) {
			
			//Bag annotations = m.createBag();
			ObjectProperty bioc_annotations = m.createObjectProperty(nsLookup.get("bioc")+"annotations");
			//m.add(ldBiocP, bioc_annotations, annotations);	
				
			int aCount = 0;
			for (BioCAnnotation biocA : p.getAnnotations()) {

				// skip simple formatting information
				String inf = biocA.getInfons().get("value");
				if (!doInfonsEncodeUris(biocA.getInfons())) 
					continue;

				Individual ldBiocA = convertBioCAnnotation(m, biocA, rootNS, did, aCount+"");
				m.add(ldBiocP, bioc_annotations, ldBiocA);	
				aCount++;
				
			}
			
		}
		
		if (p.getSentences() != null) {

			int sCount = 0;
			ObjectProperty bioc_sentences = m.createObjectProperty(nsLookup.get("bioc")+"sentences");
			
			for (BioCSentence biocS : p.getSentences()) {

				Individual ldBiocS = convertBioCSentence(m, biocS, rootNS, did, sCount+"");
				m.add(ldBiocP, bioc_sentences, ldBiocS);	
				sCount++;
			
			}
		}

		return ldBiocP;

	}

	public static Individual convertBioCAnnotation(OntModel m, BioCAnnotation biocA, String rootNS, String did, String aid) throws Exception {

		OntClass ldBiocA_class = m.createClass(nsLookup.get("bioc")+"Annotation");
		Individual ldBiocA = m.createIndividual(rootNS+"pmid"+did+"_a"+aid, ldBiocA_class);
		
		addInfonsAsProperties(m, ldBiocA, biocA.getInfons() );
		
		DatatypeProperty bioc_text = m.createDatatypeProperty(nsLookup.get("bioc")+"text");
		m.add(ldBiocA, bioc_text, biocA.getText());	
		
		if (biocA.getLocations() != null) {
			int lCount = 0;
			for (BioCLocation l : biocA.getLocations()) {
				
				OntClass ldBiocL_class = m.createClass(nsLookup.get("bioc")+"Location");
				Individual ldBiocL = m.createIndividual(rootNS+"pmid"+did+"_a"+aid+"_l"+lCount, ldBiocL_class);
				ObjectProperty bioc_locations = m.createObjectProperty(nsLookup.get("bioc")+"locations");
				m.add(ldBiocA, bioc_locations, ldBiocL);	
				
				DatatypeProperty bioc_offset = m.createDatatypeProperty(nsLookup.get("bioc")+"offset");				
				m.addLiteral(ldBiocL, bioc_offset, l.getOffset());	
				
				DatatypeProperty bioc_length = m.createDatatypeProperty(nsLookup.get("bioc")+"length");				
				m.addLiteral(ldBiocL, bioc_length, l.getLength());	
				lCount++;
			}
		}

		return ldBiocA;

	}
	
	
	public static Individual convertBioCSentence(OntModel m, BioCSentence biocS, String rootNS, String did, String id) throws Exception {

		OntClass ldBiocS_class = m.createClass(nsLookup.get("bioc")+"Sentence");
		Individual ldBiocS = m.createIndividual(rootNS+"pmid"+did+"_s" +id, ldBiocS_class);

		addInfonsAsProperties(m, ldBiocS, biocS.getInfons() );

		DatatypeProperty bioc_offset = m.createDatatypeProperty(nsLookup.get("bioc")+"offset");				
		m.addLiteral(ldBiocS, bioc_offset, biocS.getOffset());	
		
		DatatypeProperty bioc_text = m.createDatatypeProperty(nsLookup.get("bioc")+"text");				
		m.add(ldBiocS, bioc_text, biocS.getText());	

		return ldBiocS;

	}

	public static boolean doInfonsEncodeUris(Map<String, String> infons) {
		if (infons != null) {
			for (String key : infons.keySet()) {
				if(key.contains(":")) {
					return true;
				}				
			}
		}
		return false;
	}	

	
	public static void addInfonsAsProperties(OntModel m, Resource stem, Map<String, String> infons) throws Exception {
		if (infons != null) {
			for (String key : infons.keySet()) {
				if(key.contains(":")) {
					
					int colonPos = key.indexOf(":");
					String ns = key.substring(0,colonPos);
					if(!nsLookup.containsKey(ns))
						throw new Exception("Namespace " + ns + " not defined!");
					String propUri = nsLookup.get(ns) + key.substring(colonPos+1,key.length());
					
					//
					// We either expect to see 
					//    "instanceUri a classUri"
					// or 
					//    "classUri" in the infons representation 
					// to translate BioC infons to linked data. 
					// 
					String[] tuple = infons.get(key).split(" ");
					if(key.startsWith("rdfs:label") ){
						Property p = m.createProperty(propUri);
						m.add(stem, p, infons.get(key));
					}else if(!infons.get(key).startsWith("http:")){
						DatatypeProperty p = m.createDatatypeProperty(propUri);
						m.add(stem, p, infons.get(key));
					} else if(tuple.length == 1) { 
						OntClass c = m.createClass(tuple[0]);
						ObjectProperty p = m.createObjectProperty(propUri);
						m.add(stem, p, c);													
					} else if(tuple.length == 3) { 
						OntClass targetClass = m.createClass(tuple[2]);
						ObjectProperty p = m.createObjectProperty(propUri);
						Individual targetIndividual = m.createIndividual(tuple[0], targetClass);
						m.add(stem, p, targetIndividual);							
					} else {
						throw new Exception("Can't parse Class / Individual from Infons:" + infons.get(key));
					}
					
				}	
				
			}
			
		}
		
	}	
	
}
