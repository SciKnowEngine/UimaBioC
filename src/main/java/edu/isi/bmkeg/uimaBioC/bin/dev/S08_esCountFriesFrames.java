package edu.isi.bmkeg.uimaBioC.bin.dev;

import java.io.File;
import java.io.FileReader;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.bigmech.fries.FRIES_EntityMention;
import org.bigmech.fries.FRIES_EventMention;
import org.bigmech.fries.FRIES_Frame;
import org.bigmech.fries.FRIES_FrameCollection;
import org.bigmech.fries.FRIES_Passage;
import org.bigmech.fries.FRIES_Sentence;
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

@Component
public class S08_esCountFriesFrames {

	public static class Options {

		@Option(name = "-inDir", usage = "Input Directory", required = true, metaVar = "IN-DIRECTORY")
		public File inDir;

	}

	private static Logger logger = Logger.getLogger(S08_esCountFriesFrames.class);

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

		S08_esCountFriesFrames main = ctx.getBean(S08_esCountFriesFrames.class);

		logger.info("FRIES Frames File Location: " + options.inDir);

		final RuntimeTypeAdapterFactory<FRIES_Frame> typeFactory = RuntimeTypeAdapterFactory
				.of(FRIES_Frame.class, "frame-type").registerSubtype(FRIES_EntityMention.class, "entity-mention")
				.registerSubtype(FRIES_Sentence.class, "sentence").registerSubtype(FRIES_Passage.class, "passage")
				.registerSubtype(FRIES_EventMention.class, "event-mention");

		Gson gson = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_DASHES)
				.registerTypeAdapterFactory(typeFactory).create();

		Set<String> eventSentences = new HashSet<String>();
		Set<String> allSentences = new HashSet<String>();
		String[] extensions = { "json" };
		Collection<File> frameFiles = FileUtils.listFiles(options.inDir, extensions, true);
		int count = 0;
		for (File file : frameFiles) {
			FRIES_FrameCollection fc = gson.fromJson(new FileReader(file), FRIES_FrameCollection.class);

			for (FRIES_Frame frame : fc.getFrames()) {
				if (frame instanceof FRIES_EventMention) {
					FRIES_EventMention f = (FRIES_EventMention) frame;
					eventSentences.add(f.getSentence());
				} else if (frame instanceof FRIES_EntityMention) {
					FRIES_EntityMention f = (FRIES_EntityMention) frame;
					allSentences.add(f.getFrameId());
				} else if (frame instanceof FRIES_Sentence) {
					FRIES_Sentence f = (FRIES_Sentence) frame;
					allSentences.add(f.getFrameId());
				}
				count++;
			}
		}
		
		System.out.println(eventSentences.size()+ "/" + allSentences.size());
		System.out.println(count + " frames");
		

	}

}
