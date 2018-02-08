package edu.isi.bmkeg.uimaBioC.bin;

import java.io.File;

import org.apache.log4j.Logger;
import org.apache.uima.collection.CollectionProcessingEngine;
import org.apache.uima.collection.CollectionReader;
import org.apache.uima.collection.CollectionReaderDescription;
import org.apache.uima.collection.StatusCallbackListener;
import org.apache.uima.resource.metadata.TypeSystemDescription;
import org.cleartk.opennlp.tools.SentenceAnnotator;
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

import edu.isi.bmkeg.uimaBioC.uima.ae.core.AddAnnotationsFromNxmlFormatting;
import edu.isi.bmkeg.uimaBioC.uima.ae.core.FixSentencesFromHeadings;
import edu.isi.bmkeg.uimaBioC.uima.out.SaveAsSentenceSpreadsheets;
import edu.isi.bmkeg.uimaBioC.uima.out.SaveAsSimpleSentenceSpreadsheets;
import edu.isi.bmkeg.uimaBioC.uima.readers.BioCCollectionReader;
import edu.isi.bmkeg.uimaBioC.uima.readers.Nxml2TxtFilesCollectionReader;
import edu.isi.bmkeg.uimaBioC.utils.StatusCallbackListenerImpl;

/**
 * This script provides a simple demonstration of loading BioC data from 
 * text derived from NXML files with the added annotations on top of them.
 * It then dumps the output as BioC files in the specified output directory. 
 * 
 * @author Gully
 * 
 */
public class UIMABIOC_01_Nxml2txt_to_SentenceTsv {

	public static class Options {

		@Option(name = "-inDir", usage = "Input Directory", required = true, metaVar = "IN-DIRECTORY")
		public File inDir;

		@Option(name = "-outDir", usage = "Output Directory", required = false, metaVar = "OUT-DIRECTORY")
		public File outDir;

		@Option(name = "-refDir", usage = "Reference Directory", required = false, metaVar = "REFERENCE-FILES-DIRECTORY")
		public File refDir;
		
		@Option(name = "-outType", usage = "Output Type", required = false, metaVar = "OUTPUT-FILE-TYPE")
		public String outType;

		@Option(name = "-nThreads", usage = "Number of threads", required = true, metaVar = "IN-DIRECTORY")
		public int nThreads;

	}

	private static Logger logger = Logger
			.getLogger(UIMABIOC_01_Nxml2txt_to_SentenceTsv.class);

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

		if (options.outDir == null )
			options.outDir = options.inDir;

		if (!options.outDir.getParentFile().exists())
			options.outDir.getParentFile().mkdirs();

		TypeSystemDescription typeSystem = TypeSystemDescriptionFactory
				.createTypeSystemDescription("bioc.TypeSystem");

		CollectionReaderDescription cr = CollectionReaderFactory.createDescription(
				Nxml2TxtFilesCollectionReader.class, typeSystem,
				Nxml2TxtFilesCollectionReader.PARAM_INPUT_DIRECTORY, options.inDir);

		boolean refsSpecified = (options.refDir != null);
		boolean outSpecified = (options.outDir != null && options.outType != null);
		if( refsSpecified && !outSpecified ) 
			cr = CollectionReaderFactory.createDescription(
					Nxml2TxtFilesCollectionReader.class, typeSystem,
					Nxml2TxtFilesCollectionReader.PARAM_INPUT_DIRECTORY, options.inDir,
					Nxml2TxtFilesCollectionReader.PARAM_REF_DIRECTORY, options.refDir);
		else if( !refsSpecified && outSpecified ) 
			cr = CollectionReaderFactory.createDescription(
					Nxml2TxtFilesCollectionReader.class, typeSystem,
					Nxml2TxtFilesCollectionReader.PARAM_INPUT_DIRECTORY, options.inDir,
					Nxml2TxtFilesCollectionReader.PARAM_OUTPUT_DIRECTORY, options.outDir,
					Nxml2TxtFilesCollectionReader.PARAM_OUTPUT_TYPE, options.outType);
		else if( refsSpecified && !outSpecified ) 
			cr = CollectionReaderFactory.createDescription(
					Nxml2TxtFilesCollectionReader.class, typeSystem,
					Nxml2TxtFilesCollectionReader.PARAM_INPUT_DIRECTORY, options.inDir,
					Nxml2TxtFilesCollectionReader.PARAM_REF_DIRECTORY, options.refDir,
					Nxml2TxtFilesCollectionReader.PARAM_OUTPUT_DIRECTORY, options.outDir,
					Nxml2TxtFilesCollectionReader.PARAM_OUTPUT_TYPE, options.outType);
					

		CpeBuilder cpeBuilder = new CpeBuilder();
		cpeBuilder.setReader(cr);

		AggregateBuilder builder = new AggregateBuilder();

		builder.add(AnalysisEngineFactory.createPrimitiveDescription(
				AddAnnotationsFromNxmlFormatting.class));		
		
		builder.add(SentenceAnnotator.getDescription()); // Sentence
														// segmentation
		
		builder.add(TokenAnnotator.getDescription()); // Tokenization

		//
		// Some sentences include headers that don't end in periods
		//
		builder.add(AnalysisEngineFactory.createPrimitiveDescription(FixSentencesFromHeadings.class));

		builder.add(AnalysisEngineFactory.createPrimitiveDescription(SaveAsSimpleSentenceSpreadsheets.class,
				SaveAsSimpleSentenceSpreadsheets.PARAM_DIR_PATH, options.outDir.getPath())
				);

		
		cpeBuilder.setAnalysisEngine(builder.createAggregateDescription());

		cpeBuilder.setMaxProcessingUnitThreatCount(options.nThreads);
		StatusCallbackListener callback = new StatusCallbackListenerImpl();
		CollectionProcessingEngine cpe = cpeBuilder.createCpe(callback);
		System.out.println("Running CPE");
		cpe.process();

		try {
			Thread.sleep(500);
		} catch (InterruptedException e) {
		}

		while (cpe.isProcessing())
			try {
				Thread.sleep(200);
			} catch (InterruptedException e) {
			}

		System.out.println("\n\n ------------------ PERFORMANCE REPORT ------------------\n");
		System.out.println(cpe.getPerformanceReport().toString());

		long endTime = System.currentTimeMillis();
		float duration = (float) (endTime - startTime);
		System.out.format("\n\nTOTAL EXECUTION TIME: %.3f s", duration / 1000);
		
		SimplePipeline.runPipeline(cr, builder.createAggregateDescription());
		
	}

}
