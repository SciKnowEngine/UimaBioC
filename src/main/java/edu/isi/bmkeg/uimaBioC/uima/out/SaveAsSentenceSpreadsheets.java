package edu.isi.bmkeg.uimaBioC.uima.out;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.bigmech.fries.FRIES_Argument;
import org.bigmech.fries.FRIES_Context;
import org.bigmech.fries.FRIES_EntityMention;
import org.bigmech.fries.FRIES_EventMention;
import org.bigmech.fries.FRIES_Frame;
import org.bigmech.fries.FRIES_FrameCollection;
import org.bigmech.fries.FRIES_Passage;
import org.bigmech.fries.FRIES_Sentence;
import org.bigmech.fries.FRIES_XRef;
import org.cleartk.token.type.Sentence;
import org.uimafit.component.JCasAnnotator_ImplBase;
import org.uimafit.descriptor.ConfigurationParameter;
import org.uimafit.factory.ConfigurationParameterFactory;
import org.uimafit.util.JCasUtil;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import com.google.gson.typeadapters.RuntimeTypeAdapterFactory;

import bioc.BioCCollection;
import bioc.type.UimaBioCAnnotation;
import bioc.type.UimaBioCDocument;
import bioc.type.UimaBioCSentence;
import edu.isi.bmkeg.uimaBioC.ReachUtils;
import edu.isi.bmkeg.uimaBioC.UimaBioCUtils;
import edu.isi.bmkeg.uimaBioC.utils.SubFigureNumberExtractor;

public class SaveAsSentenceSpreadsheets extends JCasAnnotator_ImplBase {

	private static Logger logger = Logger.getLogger(SaveAsSentenceSpreadsheets.class);

	public final static String PARAM_DIR_PATH = ConfigurationParameterFactory
			.createConfigurationParameterName(SaveAsSentenceSpreadsheets.class, "outDirPath");
	@ConfigurationParameter(mandatory = true, description = "The path to the output directory.")
	String outDirPath;

	public final static String PARAM_FRIES_DIR = ConfigurationParameterFactory
			.createConfigurationParameterName(SaveAsSentenceSpreadsheets.class, "friesDirPath");
	@ConfigurationParameter(mandatory = false, description = "FRIES data location?")
	String friesDirPath;
	
	public final static String PARAM_PMC_FILE_NAMES = ConfigurationParameterFactory
			.createConfigurationParameterName(SaveAsSentenceSpreadsheets.class, "pmcFileNamesStr");
	@ConfigurationParameter(mandatory = false, description = "Should we use PMC or PMID file names?")
	String pmcFileNamesStr;
	Boolean pmcFileNames = true;

	private File outDir;
	private File friesDir;
	private BioCCollection collection;
	
	private SubFigureNumberExtractor figExtractor;

	Map<String, Map<String, Integer>> table = new HashMap<String, Map<String, Integer>>();

	private HashMap<String, FRIES_Sentence> sentenceMap;

	private HashMap<String, Set<FRIES_EventMention>> eventMap;

	private HashMap<String, FRIES_EntityMention> entityMap;
	
	private RuntimeTypeAdapterFactory<FRIES_Frame> typeFactory;
	
	public void initialize(UimaContext context) throws ResourceInitializationException {

		super.initialize(context);

		this.outDirPath = (String) context.getConfigParameterValue(PARAM_DIR_PATH);
		this.outDir = new File(this.outDirPath);

		if (!this.outDir.exists())
			this.outDir.mkdirs();

		this.friesDirPath = (String) context.getConfigParameterValue(PARAM_FRIES_DIR);
		if( this.friesDirPath != null) {	
			this.friesDir = new File(this.friesDirPath);
			
		}
		
		if (this.pmcFileNamesStr != null && this.pmcFileNamesStr.toLowerCase().equals("false")) {
			pmcFileNames = false;
		} else {
			pmcFileNames = true;
		}

		this.collection = new BioCCollection();

		try {
			this.figExtractor = new SubFigureNumberExtractor();
		} catch (Exception e) {
			throw new ResourceInitializationException(e);
		}

		this.typeFactory = RuntimeTypeAdapterFactory
				.of(FRIES_Frame.class, "frame-type").registerSubtype(FRIES_EntityMention.class, "entity-mention")
				.registerSubtype(FRIES_Sentence.class, "sentence").registerSubtype(FRIES_Passage.class, "passage")
				.registerSubtype(FRIES_EventMention.class, "event-mention").registerSubtype(FRIES_Context.class, "context");		
		
	}

	public void process(JCas jCas) throws AnalysisEngineProcessException {

		try {
			UimaBioCDocument uiD = JCasUtil.selectSingle(jCas, UimaBioCDocument.class);
			if (uiD.getId().equals("skip"))
				return;

			String id = uiD.getId();

			if( this.friesDir != null ) {
				this.loadFriesData(friesDir, uiD);
			}
			
			Map<String, String> infons = UimaBioCUtils.convertInfons(uiD.getInfons());
			String pmcID = "PMC" + infons.get("pmc");
			if (infons.containsKey("pmcid"))
				pmcID = infons.get("pmcid");
			if (infons.containsKey("accession"))
				pmcID = infons.get("accession");

			File outFile = null;
			if( this.pmcFileNames) 
				outFile = new File(this.outDir.getPath() + "/" + pmcID + ".scidp.discourse.tsv");
			else 
				outFile = new File(this.outDir.getPath() + "/" + id + ".tsv");

			PrintWriter out;
			try {
				out = new PrintWriter(new BufferedWriter(new FileWriter(outFile, true)));
			} catch (IOException e) {
				throw (new AnalysisEngineProcessException(e));
			}
			
			Gson gson = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_DASHES)
					.registerTypeAdapterFactory(typeFactory).create();

			Map<String, File> fileHash = ReachUtils.getReachFileHash(id, this.friesDir);
			FRIES_FrameCollection fc1 = gson.fromJson(new FileReader(fileHash.get("sentences")), FRIES_FrameCollection.class);
			Map<String, FRIES_Sentence> sentenceMap = new HashMap<String, FRIES_Sentence>();
			for (FRIES_Frame frame : fc1.getFrames()) {
				if (frame instanceof FRIES_Sentence) {
					FRIES_Sentence sn = (FRIES_Sentence) frame;
					sentenceMap.put(sn.getFrameId(), sn);
				}
			}
			
			FRIES_FrameCollection fc2 = gson.fromJson(new FileReader(fileHash.get("events")), FRIES_FrameCollection.class);
			for (FRIES_Frame frame : fc2.getFrames()) {
				if (frame instanceof FRIES_EventMention) {
					FRIES_EventMention em = (FRIES_EventMention) frame;
					if( !eventMap.containsKey(em.getSentence()) ) {
						eventMap.put(em.getSentence(), new HashSet<FRIES_EventMention>());
					} 
					Set<FRIES_EventMention> eventSet = eventMap.get(em.getSentence());
					eventSet.add(em);					
				}
			}

			FRIES_FrameCollection fc3 = gson.fromJson(new FileReader(fileHash.get("entities")), FRIES_FrameCollection.class);
			for (FRIES_Frame frame : fc3.getFrames()) {
				if (frame instanceof FRIES_EntityMention) {
					FRIES_EntityMention em = (FRIES_EntityMention) frame;
					entityMap.put(em.getFrameId(), em);
				}
			}
			
			this.dumpSectionToFile(jCas, out, uiD.getBegin(), uiD.getEnd());

			out.close();

		} catch (Exception e) {
			
			throw new AnalysisEngineProcessException(e);
			
		}

	}

	private void dumpSectionToFile(JCas jCas, PrintWriter out, int start, int end) throws Exception {

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

		// Column Headings
		out.print("SentenceId");
		out.print("\t");
		out.print("Sentence Text");
		out.print("\t");
		out.print("Codes");
		out.print("\t");
		out.print("ExperimentValues");
		out.print("\t");
		out.print("Paragraph");
		out.print("\t");
		out.print("Headings");
		out.print("\t");
		out.print("FloatingBox?");
		out.print("\t");
		out.print("Discourse Type");
		out.print("\t");
		out.print("Offset_Begin");
		out.print("\t");
		out.print("Offset_End");
		out.print("\t");
		out.print("Figure Assignment");

		if (this.friesDirPath != null) {
			out.print("\t");
			out.print("friesSentenceId");
			out.print("\t");
			out.print("friesEventsTypes");			
		}
		out.print("\n");

		List<Sentence> sentences = JCasUtil.selectCovered(jCas, Sentence.class, start, end);
		List<Sentence> floatingSentences = new ArrayList<Sentence>();
		SENTENCE_LOOP: for (Sentence s : sentences) {
			for (UimaBioCAnnotation f : floats) {
				if (s.getBegin() >= f.getBegin() && s.getEnd() <= f.getEnd()) {
					floatingSentences.add(s);
					continue SENTENCE_LOOP;
				}
			}
			printOutSentence(jCas, out, s, false);
		}

		SENTENCE_LOOP: for (Sentence s : floatingSentences) {
			printOutSentence(jCas, out, s, true);
		}
	
	}

	private void printOutSentence(JCas jCas, PrintWriter out, Sentence s, boolean floater)
			throws Exception, StackOverflowError {
		
		List<UimaBioCAnnotation> clauseList = new ArrayList<UimaBioCAnnotation>();
		List<UimaBioCAnnotation> friesSentenceList = new ArrayList<UimaBioCAnnotation>();
		for (UimaBioCAnnotation a : JCasUtil.selectCovered(jCas, UimaBioCAnnotation.class, s)) {
			Map<String, String> infons = UimaBioCUtils.convertInfons(a.getInfons());
			if (infons.get("type").equals("rubicon") && infons.get("value").equals("clause"))
				clauseList.add(a);
			if (infons.get("type").equals("fries_sentence") )
				friesSentenceList.add(a);
		}

		if (clauseList.size() == 0) {
			throw new Exception("Can't have sentences without clauses");
		}
		
		String sentenceId = "";
		String paragraphId = "";
		Set<String> inExHeading = new HashSet<String>();
		String cStr = "";
		String heading = "";
		String discourse = "";	
		Set<String> figAssignment = new HashSet<String>();	
		
		for (UimaBioCAnnotation clause : clauseList) {

			Map<String, String> infons = UimaBioCUtils.convertInfons(clause.getInfons());

			sentenceId = infons.get("scidp-sentence-number");
			paragraphId = infons.get("scidp-paragraph-number");
			
			String iehStr = infons.get("scidp-inExHeading-string");
			iehStr = iehStr.substring(1, iehStr.length()-1);
			for(String ieh : iehStr.split(",")) {
				inExHeading.add(ieh);				
			}
			
			Set<String> codeSet = this.figExtractor.extractExptsFromClause(jCas, clause);
			List<String> codes =  new ArrayList<String>(codeSet);
			java.util.Collections.sort(codes);
			for(String c : codes ) {
				if( cStr.length() > 0 ) 
					cStr += "|";
				cStr += c;	
			}
			
			heading = UimaBioCUtils.readHeadingString(jCas, clause, "");
			
			if( discourse.length() > 0 ) 
				discourse += "|";
			if (infons.containsKey("scidp-discourse-type"))
				discourse += infons.get("scidp-discourse-type");
			else
				discourse += "-";

			if (infons.containsKey("scidp-fig-assignment")) {
				for( String figAssign : infons.get("scidp-fig-assignment").split("\\|") ) {
					figAssignment.add(figAssign);				
				}
			}

		}
		
		String fries_sentence = "";
		for (UimaBioCAnnotation fs : friesSentenceList) {
			Map<String, String> infons = UimaBioCUtils.convertInfons(fs.getInfons());
			fries_sentence += infons.get("value");
		}
		
		if(inExHeading.contains("")) 
			inExHeading.remove("");
		
		out.print(sentenceId);
		out.print("\t");
		out.print(UimaBioCUtils.readTokenizedText(jCas, s));
		out.print("\t");
		out.print(inExHeading);
		out.print("\t");
		out.print(cStr);
		out.print("\t");	
		out.print(paragraphId);
		out.print("\t");
		out.print(heading);
		out.print("\t");
		out.print(floater);
		out.print("\t");
		out.print(discourse);

		out.print("\t");
		out.print(s.getBegin());
		out.print("\t");
		out.print(s.getEnd());

		out.print("\t");
		String fa_text = "";
		for( String fa : figAssignment) {
			if( fa_text.length() > 0 )
				fa_text += "|";
			fa_text += fa;
		}
		out.print(fa_text);			

		if (this.friesDirPath != null) {
			out.print("\t");
			out.print(fries_sentence);
			
			// We want to list out the FRIES events for this sentence.
			// We only expect to see one sentence per line. 
			out.print("\t");
			String e = ""; 
			for( UimaBioCAnnotation fs : friesSentenceList) {
				Map<String, String> infons = UimaBioCUtils.convertInfons(fs.getInfons());
				String fs_id = infons.get("value");
				for( FRIES_EventMention ev : eventMap.get(fs_id) ) {
					Set<String> pset = new HashSet<String>();
					for( FRIES_Argument arg : ev.getArguments() ) {
						if( arg.getArgumentType().equals("entity") ) {
							String entityId = arg.getArg();
							FRIES_EntityMention entityMention = entityMap.get(entityId);
							if( entityMention.getType().equals("protein") ) {
								for (FRIES_XRef xref : entityMention.getXrefs() ) {
									pset.add(xref.getNamespace() + ":" + xref.getId());
								}
							}
						}
					}
					if(e.length()>0)
						e += ";";
					String p = ""; 
					for(String pp:pset) {
						if(p.length()>0)
							p += ",";
						p += pp;
					}
					e += ReachUtils.getEventSummary(ev) + "(" + p + ")";
				}
			}
			out.print(e);
		
		}
		out.print("\n");
		
	}
	
	private void loadFriesData(File friesDir, UimaBioCDocument uiD ) 
			throws JsonSyntaxException, JsonIOException, FileNotFoundException {
		
		Map<String, String> infons = UimaBioCUtils.convertInfons(uiD.getInfons());
		String pmcID = "PMC" + infons.get("pmc");
		if (infons.containsKey("pmcid"))
			pmcID = infons.get("pmcid");
		if (infons.containsKey("accession"))
			pmcID = infons.get("accession");
		
		String[] fileTypes = { "json" };
		Collection<File> files = (Collection<File>) FileUtils.listFiles(this.friesDir, fileTypes, true);
		File sentenceFrames = null;
		File eventFrames = null;
		File entityFrames = null;
		for (File f : files) {
			if (f.getName().startsWith(pmcID + ".uaz.sentences") || 
					f.getName().startsWith(uiD.getId() + ".uaz.sentences") ) {
				sentenceFrames = f;
				break;
			}
		}
		for (File f : files) {
			if (f.getName().startsWith(pmcID + ".uaz.events") || 
					f.getName().startsWith(uiD.getId() + ".uaz.events") ) {
				eventFrames = f;
				break;
			}
		}
		for (File f : files) {
			if (f.getName().startsWith(pmcID + ".uaz.entities") || 
					f.getName().startsWith(uiD.getId() + ".uaz.entities") ) {
				entityFrames = f;
				break;
			}
		}

		if (eventFrames == null || sentenceFrames == null)
			return;

		final RuntimeTypeAdapterFactory<FRIES_Frame> typeFactory = RuntimeTypeAdapterFactory
				.of(FRIES_Frame.class, "frame-type").registerSubtype(FRIES_EntityMention.class, "entity-mention")
				.registerSubtype(FRIES_Sentence.class, "sentence").registerSubtype(FRIES_Passage.class, "passage")
				.registerSubtype(FRIES_EventMention.class, "event-mention").registerSubtype(FRIES_Context.class, "context");

		Gson gson = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_DASHES)
				.registerTypeAdapterFactory(typeFactory).create();

		FRIES_FrameCollection fc = gson.fromJson(new FileReader(entityFrames), FRIES_FrameCollection.class);
		this.sentenceMap = new HashMap<String, FRIES_Sentence>();
		for (FRIES_Frame frame : fc.getFrames()) {
			if (frame instanceof FRIES_Sentence) {
				FRIES_Sentence sn = (FRIES_Sentence) frame;
				this.sentenceMap.put(sn.getFrameId(), sn);
			}
		}

		FRIES_FrameCollection fc1 = gson.fromJson(new FileReader(eventFrames), FRIES_FrameCollection.class);
		this.eventMap = new HashMap<String, Set<FRIES_EventMention>>();
		for (FRIES_Frame frame : fc1.getFrames()) {
			if (frame instanceof FRIES_EventMention) {
				FRIES_EventMention em = (FRIES_EventMention) frame;
				String sn = em.getSentence();
				if (!eventMap.containsKey(sn))
					eventMap.put(sn, new HashSet<FRIES_EventMention>());
				eventMap.get(sn).add(em);
			}
		}

		FRIES_FrameCollection fc2 = gson.fromJson(new FileReader(entityFrames), FRIES_FrameCollection.class);

		entityMap = new HashMap<String, FRIES_EntityMention>();
		for (FRIES_Frame frame : fc2.getFrames()) {
			if (frame instanceof FRIES_EntityMention) {
				FRIES_EntityMention em = (FRIES_EntityMention) frame;
				entityMap.put(em.getFrameId(), em);
			}
		}
		
	}

}
