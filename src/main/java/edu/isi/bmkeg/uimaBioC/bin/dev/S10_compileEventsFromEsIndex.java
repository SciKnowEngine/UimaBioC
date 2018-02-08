package edu.isi.bmkeg.uimaBioC.bin.dev;

import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.bigmech.fries.esViews.FRIES_EntityMentionView.FRIES_EntityMentionView__FRIES_EntityMention;
import org.bigmech.fries.esViews.FRIES_EventMentionView.FRIES_EventMentionView__FRIES_EventMention;
import org.bigmech.fries.esViews.FRIES_SentenceView.FRIES_SentenceView__FRIES_Sentence;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.stereotype.Component;

import bioc.esViews.BioCDocument.BioCDocument__BioCDocument;
import edu.isi.bmkeg.uimaBioC.elasticSearch.BioCDocumentRepository;
import edu.isi.bmkeg.uimaBioC.elasticSearch.EntityMentionRepository;
import edu.isi.bmkeg.uimaBioC.elasticSearch.EventMentionRepository;
import edu.isi.bmkeg.uimaBioC.elasticSearch.SentenceRepository;

@Component
public class S10_compileEventsFromEsIndex {

	@Autowired
	EntityMentionRepository entityMentionRepo;

	@Autowired
	EventMentionRepository eventMentionRepo;

	@Autowired
	SentenceRepository sentRepo;

	@Autowired
	BioCDocumentRepository biocRepo;
	
	public static class Options {

		
		@Option(name = "-docId", usage = "Document Id", required = true, metaVar = "IN-DIRECTORY")
		public String docId;

	}

	private static Logger logger = Logger.getLogger(S10_compileEventsFromEsIndex.class);

	/**
	 * @param args
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {

		Options options = new Options();

		CmdLineParser parser = new CmdLineParser(options);

		try {

			parser.parseArgument(args);

		} catch (CmdLineException e) {

			System.err.println(e.getMessage());
			System.err.print("Arguments: ");
			parser.printSingleLineUsage(System.err);
			System.err.println("\n\n Options: \n");
			parser.printUsage(System.err);
			System.exit(-1);

		}
		
		// Use annotated beans from the specified package
		ApplicationContext ctx = new AnnotationConfigApplicationContext("edu.isi.bmkeg.uimaBioC");

		/*PubMedESIndex pmES = new PubMedESIndex("temp");
		
		Map<String,Object> nxmlMap = null;
		if( options.docId.startsWith("PMC") )
			nxmlMap = pmES.getMapFromTerm("pmcId", options.docId, "nxml");
		else 
			nxmlMap = pmES.getMapFromTerm("pmid", options.docId, "nxml");
		String pmcId = (String) nxmlMap.get("pmcId");
		String pmid = (String) nxmlMap.get("pmid");*/
		
		// TODO - THERE ARE MUCH BETTER WAYS OF OBTAINING PMID + PMCID VALUES THAN USING LOCAL ES STORE
		String pmcId = "";
		String pmid = "";
		
		S10_compileEventsFromEsIndex main = ctx.getBean(S10_compileEventsFromEsIndex.class);

		List<FRIES_EntityMentionView__FRIES_EntityMention> entities = main.entityMentionRepo.findByFrameIdLike("*-" + pmcId + "-*");
		List<FRIES_EventMentionView__FRIES_EventMention> events = main.eventMentionRepo.findByFrameIdLike("*-" + pmcId + "-*");
		List<FRIES_SentenceView__FRIES_Sentence> sentences = main.sentRepo.findByFrameIdLike("*-" + pmcId + "-*");
		
		BioCDocument__BioCDocument biocd = main.biocRepo.findOne(pmid);
		
		int pause = 1;
		
	}

}
