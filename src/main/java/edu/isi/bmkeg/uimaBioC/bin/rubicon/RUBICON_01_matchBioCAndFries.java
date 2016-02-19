package edu.isi.bmkeg.uimaBioC.bin.rubicon;

import java.io.File;

import org.apache.log4j.Logger;
import org.apache.uima.collection.CollectionProcessingEngine;
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

import edu.isi.bmkeg.uimaBioC.rubicon.SeparateClauses;
import edu.isi.bmkeg.uimaBioC.uima.ae.core.FilterUnwantedSections;
import edu.isi.bmkeg.uimaBioC.uima.ae.core.FixSentencesFromHeadings;
import edu.isi.bmkeg.uimaBioC.uima.ae.core.MatchReachAndNxmlText;
import edu.isi.bmkeg.uimaBioC.uima.out.SaveExtractedAnnotations;
import edu.isi.bmkeg.uimaBioC.uima.readers.BioCCollectionReader;
import edu.isi.bmkeg.uimaBioC.utils.StatusCallbackListenerImpl;

public class RUBICON_01_matchBioCAndFries {

	public static class Options {

		@Option(name = "-nThreads", usage = "Number of threads", required = true, metaVar = "IN-DIRECTORY")
		public int nThreads;
		
		@Option(name = "-biocDir", usage = "Input Directory", required = true, metaVar = "IN-DIRECTORY")
		public File biocDir;

		@Option(name = "-friesDir", usage = "Fries Directory", required = true, metaVar = "FRIES-DATA")
		public File friesDir;

		@Option(name = "-ann2Extract", usage = "Annotation Type to Extract", required = true, metaVar = "ANNOTATION")
		public File ann2Ext;

		@Option(name = "-outDir", usage = "Output Directory", required = true, metaVar = "OUT-FILE")
		public File outDir;

		@Option(name = "-headerLink", usage = "Output Directory", required = true, metaVar = "OUT-FILE")
		public Boolean headerLink = false;

	}

	private static Logger logger = Logger.getLogger(RUBICON_01_matchBioCAndFries.class);

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
				//BioCCollectionReader.OUTPUT_DIRECTORY, options.outDir.getPath(), 
				BioCCollectionReader.PARAM_FORMAT, BioCCollectionReader.JSON);

		CpeBuilder cpeBuilder = new CpeBuilder();
		cpeBuilder.setReader(crDesc);

		AggregateBuilder builder = new AggregateBuilder();
		
		builder.add(SentenceAnnotator.getDescription()); // Sentence
		builder.add(TokenAnnotator.getDescription()); // Tokenization

		//
		// Some sentences include headers that don't end in periods
		//
		builder.add(AnalysisEngineFactory.createPrimitiveDescription(FixSentencesFromHeadings.class));

		builder.add(AnalysisEngineFactory.createPrimitiveDescription(FilterUnwantedSections.class,
				FilterUnwantedSections.PARAM_ANNOT_2_EXTRACT, options.ann2Ext,
				FilterUnwantedSections.PARAM_KEEP_FLOATING_BOXES, "false"));

		//
		// Rerun Pradeep's system to create clauses from the text
		//
		builder.add(AnalysisEngineFactory.createPrimitiveDescription(SeparateClauses.class));

		builder.add(AnalysisEngineFactory.createPrimitiveDescription(MatchReachAndNxmlText.class,
				MatchReachAndNxmlText.PARAM_INPUT_DIRECTORY, options.friesDir.getPath()));

		if (options.headerLink) {

			builder.add(AnalysisEngineFactory.createPrimitiveDescription(SaveExtractedAnnotations.class,
					SaveExtractedAnnotations.PARAM_ANNOT_2_EXTRACT, options.ann2Ext,
					SaveExtractedAnnotations.PARAM_DIR_PATH, options.outDir.getPath(),
					SaveExtractedAnnotations.PARAM_KEEP_FLOATING_BOXES, "false",
					SaveExtractedAnnotations.PARAM_ADD_FRIES_CODES, "true", SaveExtractedAnnotations.PARAM_SKIP_BODY,
					"true", SaveExtractedAnnotations.PARAM_HEADER_LINKS, "true"));

		} else {

			builder.add(AnalysisEngineFactory.createPrimitiveDescription(SaveExtractedAnnotations.class,
					SaveExtractedAnnotations.PARAM_ANNOT_2_EXTRACT, options.ann2Ext,
					SaveExtractedAnnotations.PARAM_DIR_PATH, options.outDir.getPath(),
					SaveExtractedAnnotations.PARAM_KEEP_FLOATING_BOXES, "false",
					SaveExtractedAnnotations.PARAM_ADD_FRIES_CODES, "true", SaveExtractedAnnotations.PARAM_SKIP_BODY,
					"true", SaveExtractedAnnotations.PARAM_HEADER_LINKS, "false"));
		}
		cpeBuilder.setAnalysisEngine( builder.createAggregateDescription() );
		
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
		System.out.format("\n\nTOTAL EXECUTION TIME: %.3f s", duration/1000);
	
	}

}
