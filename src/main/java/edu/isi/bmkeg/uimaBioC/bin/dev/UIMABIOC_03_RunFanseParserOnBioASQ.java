package edu.isi.bmkeg.uimaBioC.bin.dev;

import java.io.File;
import java.io.IOException;

import javax.xml.stream.XMLStreamException;

import org.apache.uima.UIMAException;
import org.apache.uima.collection.CollectionReader;
import org.apache.uima.resource.metadata.TypeSystemDescription;
import org.cleartk.opennlp.tools.SentenceAnnotator;
import org.cleartk.token.tokenizer.TokenAnnotator;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.uimafit.factory.AggregateBuilder;
import org.uimafit.factory.AnalysisEngineFactory;
import org.uimafit.factory.CollectionReaderFactory;
import org.uimafit.factory.TypeSystemDescriptionFactory;
import org.uimafit.pipeline.SimplePipeline;

import edu.isi.bmkeg.uimaBioC.rubicon.FanseParserAnnotator;
import edu.isi.bmkeg.uimaBioC.rubicon.RunCRFAnnotator;
import edu.isi.bmkeg.uimaBioC.uima.ae.core.AddAnnotationsFromNxmlFormatting;
import edu.isi.bmkeg.uimaBioC.uima.ae.core.FixSentencesFromHeadings;
import edu.isi.bmkeg.uimaBioC.uima.readers.BioCCollectionReader;

//@Component
public class UIMABIOC_03_RunFanseParserOnBioASQ {

	private static final Logger logger = LoggerFactory.getLogger(UIMABIOC_03_RunFanseParserOnBioASQ.class);

	//@Autowired
	//BioCRepository biocRepo;

	public static class Options {

		@Option(name = "-biocDir", usage = "BioC Director", required = true, metaVar = "BIOC_DIRECTORY")
		public File biocDir;

		@Option(name = "-outDir", usage = "Output Directory", required = true, metaVar = "OUT_DIRECTORY")
		public File outDir;

		@Option(name = "-section", usage = "Document Section", required = false, metaVar = "SECTION")
		public String section;
		
	}

	public static void main(String[] args) throws 
			IOException, XMLStreamException, CmdLineException, UIMAException {

		Options options = new Options();

		CmdLineParser parser = new CmdLineParser(options);

		try {

			parser.parseArgument(args);

		} catch (CmdLineException e) {

			System.err.print("Arguments: ");
			parser.printSingleLineUsage(System.err);
			System.err.println("\n\n Options: \n");
			parser.printUsage(System.err);
			throw e;

		}

		UIMABIOC_03_RunFanseParserOnBioASQ main = new UIMABIOC_03_RunFanseParserOnBioASQ();

		logger.info("BioC File Location: " + options.biocDir);

		if (!options.outDir.getParentFile().exists())
			options.outDir.getParentFile().mkdirs();

		TypeSystemDescription typeSystem = TypeSystemDescriptionFactory
				.createTypeSystemDescription("bioc.TypeSystem");

		CollectionReader cr = CollectionReaderFactory.createCollectionReader(
				BioCCollectionReader.class, typeSystem,
				BioCCollectionReader.INPUT_DIRECTORY, options.biocDir,
				BioCCollectionReader.PARAM_FORMAT, "json");

		AggregateBuilder builder = new AggregateBuilder();

		builder.add(AnalysisEngineFactory.createPrimitiveDescription(
				AddAnnotationsFromNxmlFormatting.class));		
		
		builder.add(SentenceAnnotator.getDescription()); // Sentence
														// segmentation
		builder.add(TokenAnnotator.getDescription());  // Tokenization

		// Some sentences include headers that don't end in periods
		builder.add(AnalysisEngineFactory.createPrimitiveDescription(
				FixSentencesFromHeadings.class));		
		
		builder.add(AnalysisEngineFactory.createPrimitiveDescription(
				FanseParserAnnotator.class, 
				FanseParserAnnotator.PARAM_OUT_FANSE_DIR_PATH,options.outDir.getPath(),
				FanseParserAnnotator.PARAM_SECTION_ANNOTATION,options.section
				));

		builder.add(AnalysisEngineFactory.createPrimitiveDescription(
				RunCRFAnnotator.class, 
				RunCRFAnnotator.PARAM_OUT_DIR_PATH,options.outDir.getPath()
				));
		
		SimplePipeline.runPipeline(cr, builder.createAggregateDescription());

	}
	
}
