package edu.isi.bmkeg.uimaBioC.bin;

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

import edu.isi.bmkeg.uimaBioC.uima.out.SimpleOneLinePerDocWriter;
import edu.isi.bmkeg.uimaBioC.uima.readers.TxtFilesCollectionReader;

/**
 * This script provides a simple demonstration of loading BioC data from 
 * text derived from NXML files with the added annotations on top of them.
 * It then dumps the output as BioC files in the specified output directory. 
 * 
 * @author Gully
 * 
 */
public class S03_TxtCollection_to_1LinePerDocTextFile {

	public static class Options {

		@Option(name = "-inDir", usage = "Input Directory", required = true, metaVar = "IN-DIRECTORY")
		public File inDir;

		@Option(name = "-outFile", usage = "Output File", required = true, metaVar = "OUT-DIRECTORY")
		public File outFile;

	}

	private static Logger logger = Logger
			.getLogger(S03_TxtCollection_to_1LinePerDocTextFile.class);

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

		if (!options.outFile.getParentFile().exists())
			options.outFile.getParentFile().mkdirs();

		TypeSystemDescription typeSystem = TypeSystemDescriptionFactory
				.createTypeSystemDescription("bioc.TypeSystem");

		CollectionReader cr = CollectionReaderFactory.createCollectionReader(
				TxtFilesCollectionReader.class, typeSystem,
				TxtFilesCollectionReader.PARAM_INPUT_DIRECTORY, options.inDir);

		AggregateBuilder builder = new AggregateBuilder();

		builder.add(AnalysisEngineFactory.createPrimitiveDescription(
				SimpleOneLinePerDocWriter.class, 
				SimpleOneLinePerDocWriter.PARAM_OUT_FILE_PATH,
				options.outFile.getPath() 
				));

		SimplePipeline.runPipeline(cr, builder.createAggregateDescription());

	}

}
