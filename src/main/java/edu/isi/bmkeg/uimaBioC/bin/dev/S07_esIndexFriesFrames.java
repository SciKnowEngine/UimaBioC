package edu.isi.bmkeg.uimaBioC.bin.dev;

import java.io.File;
import java.io.FileReader;
import java.util.Collection;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.bigmech.fries.FRIES_EntityMention;
import org.bigmech.fries.FRIES_EventMention;
import org.bigmech.fries.FRIES_Frame;
import org.bigmech.fries.FRIES_FrameCollection;
import org.bigmech.fries.FRIES_Passage;
import org.bigmech.fries.FRIES_Sentence;
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

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.typeadapters.RuntimeTypeAdapterFactory;

import edu.isi.bmkeg.uimaBioC.elasticSearch.EntityMentionRepository;
import edu.isi.bmkeg.uimaBioC.elasticSearch.EventMentionRepository;
import edu.isi.bmkeg.uimaBioC.elasticSearch.SentenceRepository;
import edu.isi.bmkeg.utils.ViewConverter;

@Component
public class S07_esIndexFriesFrames {

	@Autowired
	EntityMentionRepository entityMentionRepo;

	@Autowired
	EventMentionRepository eventMentionRepo;

	@Autowired
	SentenceRepository sentRepo;
	
	public static class Options {

		@Option(name = "-inDir", usage = "Input Directory", required = true, metaVar = "IN-DIRECTORY")
		public File inDir;

	}

	private static Logger logger = Logger.getLogger(S07_esIndexFriesFrames.class);

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

		S07_esIndexFriesFrames main = ctx.getBean(S07_esIndexFriesFrames.class);

		logger.info("FRIES Frames File Location: " + options.inDir);

        final RuntimeTypeAdapterFactory<FRIES_Frame> typeFactory = RuntimeTypeAdapterFactory
                .of(FRIES_Frame.class, "frame-type")
                .registerSubtype(FRIES_EntityMention.class, "entity-mention")
                .registerSubtype(FRIES_Sentence.class, "sentence")
                .registerSubtype(FRIES_Passage.class, "passage")
                .registerSubtype(FRIES_EventMention.class, "event-mention");


		Gson gson = new GsonBuilder()
				.setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_DASHES)
				.registerTypeAdapterFactory(typeFactory)
				.create();

		// First list the sentences
		String[] extensions = { "json" };
		Collection<File> frameFiles = FileUtils.listFiles(options.inDir, extensions, true);
		int fileCount = 0;
		for (File file : frameFiles) {
			FRIES_FrameCollection fc = gson.fromJson(new FileReader(file), 
					FRIES_FrameCollection.class);	 
			
			for( FRIES_Frame frame : fc.getFrames() ) {
				
				if( frame instanceof FRIES_EntityMention ) {
					
					FRIES_EntityMention femFrame  = (FRIES_EntityMention) frame;
					FRIES_EntityMentionView__FRIES_EntityMention esFemFrame = 
							new FRIES_EntityMentionView__FRIES_EntityMention();
					
					ViewConverter vc = new ViewConverter(esFemFrame);
					esFemFrame = vc.baseObjectToView(femFrame, esFemFrame);
				
					main.entityMentionRepo.index(esFemFrame);
				
				} else if( frame instanceof FRIES_EventMention ) {
					
					FRIES_EventMention femFrame  = (FRIES_EventMention) frame;
					FRIES_EventMentionView__FRIES_EventMention esFemFrame = 
							new FRIES_EventMentionView__FRIES_EventMention();
					
					ViewConverter vc = new ViewConverter(esFemFrame);
					esFemFrame = vc.baseObjectToView(femFrame, esFemFrame);
				
					main.eventMentionRepo.index(esFemFrame);
				
				} else if( frame instanceof FRIES_Sentence ) {
						
						FRIES_Sentence sentFrame  = (FRIES_Sentence) frame;
						FRIES_SentenceView__FRIES_Sentence esSentFrame = 
								new FRIES_SentenceView__FRIES_Sentence();
						
						ViewConverter vc = new ViewConverter(esSentFrame);
						esSentFrame = vc.baseObjectToView(sentFrame, esSentFrame);
					
						main.sentRepo.index(esSentFrame);
					
				}
			
			}

			fileCount++;
			if( fileCount % 10 == 0 )
				System.out.println(fileCount + " files processed");
			
		}

	}

}
