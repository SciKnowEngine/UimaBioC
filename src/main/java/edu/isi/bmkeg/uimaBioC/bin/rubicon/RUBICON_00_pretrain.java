package edu.isi.bmkeg.uimaBioC.bin.rubicon;

import java.io.File;

import org.apache.log4j.Logger;
import org.apache.uima.collection.CollectionReaderDescription;
import org.apache.uima.resource.metadata.TypeSystemDescription;
import org.cleartk.ml.CleartkAnnotator;
import org.cleartk.ml.feature.transform.InstanceDataWriter;
import org.cleartk.ml.jar.DefaultDataWriterFactory;
import org.cleartk.ml.jar.DirectoryDataWriterFactory;
import org.cleartk.opennlp.tools.SentenceAnnotator;
import org.cleartk.snowball.DefaultSnowballStemmer;
import org.cleartk.token.tokenizer.TokenAnnotator;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.uimafit.factory.AggregateBuilder;
import org.uimafit.factory.AnalysisEngineFactory;
import org.uimafit.factory.CollectionReaderFactory;
import org.uimafit.factory.CpeBuilder;
import org.uimafit.factory.TypeSystemDescriptionFactory;
import org.uimafit.pipeline.SimplePipeline;

import edu.isi.bmkeg.uimaBioC.rubicon.RemoveSentencesNotInTitleAbstractBody;
import edu.isi.bmkeg.uimaBioC.rubicon.dev.ParagraphTfIdfAnnotator;
import edu.isi.bmkeg.uimaBioC.uima.ae.core.FixSentencesFromHeadings;
import edu.isi.bmkeg.uimaBioC.uima.readers.BioCCollectionReader;


/**
 * Extract features for paragraphs for matching.
 * 
 * @author Gully
 *
 */
public class RUBICON_00_pretrain {

	public static class Options {

		@Option(name = "-biocDir", usage = "Input Directory", required = true, metaVar = "IN-DIRECTORY")
		public File biocDir;

		@Option(name = "-modelDir", usage = "Output Directory", required = true, metaVar = "OUT-FILE")
		public File modelDir;

	}

	private static Logger logger = Logger.getLogger(RUBICON_00_pretrain.class);

	/**
	 * @param args
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {

		long startTime = System.currentTimeMillis();

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

		TypeSystemDescription typeSystem = TypeSystemDescriptionFactory.createTypeSystemDescription("bioc.TypeSystem");

		CollectionReaderDescription crDesc = CollectionReaderFactory.createDescription(BioCCollectionReader.class,
				typeSystem, BioCCollectionReader.INPUT_DIRECTORY, options.biocDir.getPath(), 
				BioCCollectionReader.PARAM_FORMAT, BioCCollectionReader.JSON);

		CpeBuilder cpeBuilder = new CpeBuilder();
		cpeBuilder.setReader(crDesc);

		AggregateBuilder builder = new AggregateBuilder();

		builder.add(SentenceAnnotator.getDescription()); // Sentence
		
		builder.add(AnalysisEngineFactory.createPrimitiveDescription(TokenAnnotator.class,
				TokenAnnotator.PARAM_TOKENIZER_NAME, 
				"edu.isi.bmkeg.uimaBioC.rubicon.tokenizer.PennTreebankTokenizer")); // Tokenization that we can modify if we need to.

		//
		// Some sentences include headers that don't end in periods
		//
		builder.add(AnalysisEngineFactory.createPrimitiveDescription(FixSentencesFromHeadings.class));

		builder.add(AnalysisEngineFactory.createPrimitiveDescription(RemoveSentencesNotInTitleAbstractBody.class,
				RemoveSentencesNotInTitleAbstractBody.PARAM_KEEP_FLOATING_BOXES, "true"));

		builder.add(DefaultSnowballStemmer.getDescription("English"));

		builder.add(AnalysisEngineFactory.createPrimitiveDescription(ParagraphTfIdfAnnotator.class,
				CleartkAnnotator.PARAM_IS_TRAINING, true,
				DefaultDataWriterFactory.PARAM_DATA_WRITER_CLASS_NAME, InstanceDataWriter.class.getName(),
				DirectoryDataWriterFactory.PARAM_OUTPUT_DIRECTORY, options.modelDir.getPath()));
		
		SimplePipeline.runPipeline(crDesc, builder.createAggregateDescription());
		
	}

}
