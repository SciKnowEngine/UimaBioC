package edu.isi.bmkeg.uimaBioC.bin.dev;

import java.io.File;

import org.apache.log4j.Logger;
import org.apache.uima.collection.CollectionReader;
import org.apache.uima.resource.metadata.TypeSystemDescription;
import org.cleartk.opennlp.tools.SentenceAnnotator;
import org.cleartk.token.tokenizer.TokenAnnotator;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.uimafit.factory.AggregateBuilder;
import org.uimafit.factory.AnalysisEngineFactory;
import org.uimafit.factory.CollectionReaderFactory;
import org.uimafit.factory.TypeSystemDescriptionFactory;
import org.uimafit.pipeline.SimplePipeline;

import edu.isi.bmkeg.uimaBioC.rubicon.ReachAnnotator;
import edu.isi.bmkeg.uimaBioC.uima.ae.core.FixSentencesFromHeadings;
import edu.isi.bmkeg.uimaBioC.uima.out.SaveExtractedAnnotations;
import edu.isi.bmkeg.uimaBioC.uima.readers.BioCCollectionReader;

public class S09_runReach {

	public static class Options {

		@Option(name = "-inDir", usage = "Input Directory", required = true, metaVar = "IN-DIRECTORY")
		public File inDir;
		
		@Option(name = "-outDir", usage = "Output Directory", required = true, metaVar = "OUT-DIRECTORY")
		public File outDir;

		@Option(name = "-ann2Extract", usage = "Annotation Type to Extract", required = false, metaVar = "ANNOTATION")
		public String ann2Ext;
	}

	private static Logger logger = Logger.getLogger(S09_runReach.class);

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

		TypeSystemDescription typeSystem = TypeSystemDescriptionFactory
				.createTypeSystemDescription("bioc.TypeSystem");

		CollectionReader cr = CollectionReaderFactory.createCollectionReader(
				BioCCollectionReader.class, typeSystem,
				BioCCollectionReader.INPUT_DIRECTORY, options.inDir,
				BioCCollectionReader.PARAM_FORMAT, BioCCollectionReader.JSON);

		AggregateBuilder builder = new AggregateBuilder();

		builder.add(SentenceAnnotator.getDescription()); // Sentence
		builder.add(TokenAnnotator.getDescription());   // Tokenization

		// Some sentences include headers that don't end in periods
		builder.add(AnalysisEngineFactory.createPrimitiveDescription(
				FixSentencesFromHeadings.class));		

		builder.add(AnalysisEngineFactory.createPrimitiveDescription(
					ReachAnnotator.class,
					ReachAnnotator.PARAM_OUT_REACH_DIR_PATH, 
					options.outDir,					
					ReachAnnotator.PARAM_SECTION_ANNOTATION,
					options.ann2Ext
					));

		SimplePipeline.runPipeline(cr, builder.createAggregateDescription());

	}

}
