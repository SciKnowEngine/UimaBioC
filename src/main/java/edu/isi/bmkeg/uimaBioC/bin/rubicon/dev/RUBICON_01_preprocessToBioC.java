package edu.isi.bmkeg.uimaBioC.bin.rubicon.dev;

import java.io.File;
import java.net.URI;

import org.apache.log4j.Logger;
import org.apache.uima.collection.CollectionProcessingEngine;
import org.apache.uima.collection.CollectionReaderDescription;
import org.apache.uima.collection.StatusCallbackListener;
import org.apache.uima.resource.metadata.TypeSystemDescription;
import org.cleartk.ml.CleartkAnnotator;
import org.cleartk.ml.Instance;
import org.cleartk.ml.feature.extractor.CleartkExtractor;
import org.cleartk.ml.feature.extractor.CoveredTextExtractor;
import org.cleartk.ml.feature.transform.InstanceDataWriter;
import org.cleartk.ml.feature.transform.InstanceStream;
import org.cleartk.ml.feature.transform.extractor.TfidfExtractor;
import org.cleartk.ml.jar.DefaultDataWriterFactory;
import org.cleartk.ml.jar.DirectoryDataWriterFactory;
import org.cleartk.opennlp.tools.SentenceAnnotator;
import org.cleartk.token.tokenizer.TokenAnnotator;
import org.cleartk.token.type.Token;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.uimafit.factory.AggregateBuilder;
import org.uimafit.factory.AnalysisEngineFactory;
import org.uimafit.factory.CollectionReaderFactory;
import org.uimafit.factory.CpeBuilder;
import org.uimafit.factory.TypeSystemDescriptionFactory;

import bioc.type.UimaBioCAnnotation;
import edu.isi.bmkeg.uimaBioC.rubicon.RemoveSentencesNotInTitleAbstractBody;
import edu.isi.bmkeg.uimaBioC.rubicon.SeparateClauses;
import edu.isi.bmkeg.uimaBioC.rubicon.StanfordParse;
import edu.isi.bmkeg.uimaBioC.rubicon.dev.AddReachAnnotations;
import edu.isi.bmkeg.uimaBioC.rubicon.dev.ParagraphTfIdfAnnotator;
import edu.isi.bmkeg.uimaBioC.uima.ae.core.FixSentencesFromHeadings;
import edu.isi.bmkeg.uimaBioC.uima.out.SaveAsBioCDocuments;
import edu.isi.bmkeg.uimaBioC.uima.readers.BioCCollectionReader;
import edu.isi.bmkeg.uimaBioC.utils.StatusCallbackListenerImpl;

public class RUBICON_01_preprocessToBioC {

	public static class Options {

		@Option(name = "-nThreads", usage = "Number of threads", required = true, metaVar = "IN-DIRECTORY")
		public int nThreads;

		@Option(name = "-maxSentenceLength", usage = "Maximum length of sentences to be parsed", required = false, metaVar = "MAX-PARSE-LENGTH")
		public int maxSentenceLength;

		@Option(name = "-biocDir", usage = "Input Directory", required = true, metaVar = "IN-DIRECTORY")
		public File biocDir;

		@Option(name = "-modelDir", usage = "Training Model Directory", required = true, metaVar = "MODEL-DIRECTORY")
		public File modelDir;

		@Option(name = "-friesDir", usage = "Fries Directory", required = true, metaVar = "FRIES-DATA")
		public File friesDir;

		@Option(name = "-ann2Extract", usage = "Annotation Type to Extract", required = false, metaVar = "ANNOTATION")
		public File ann2Ext;

		@Option(name = "-outDir", usage = "Output Directory", required = true, metaVar = "OUT-FILE")
		public File outDir;

		@Option(name = "-outFormat", usage = "Output Format", required = true, metaVar = "OUT-FORMAT")
		public String outFormat;
		
		@Option(name = "-clauseLevel", usage = "Should we split text into clauses?", metaVar = "CLAUSE-LEVEL")
		public Boolean clauseLevel = true;

	}

	private static Logger logger = Logger.getLogger(RUBICON_01_preprocessToBioC.class);

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
		
		
		//
		// Part 1, raw feature extraction for paragraph instances.
		//
		CollectionReaderDescription crDesc = CollectionReaderFactory.createDescription(BioCCollectionReader.class,
				typeSystem, BioCCollectionReader.INPUT_DIRECTORY, options.biocDir.getPath(), 
				BioCCollectionReader.PARAM_FORMAT, BioCCollectionReader.JSON);

		CpeBuilder cpeBuilder = new CpeBuilder();
		cpeBuilder.setReader(crDesc);

		AggregateBuilder builder = new AggregateBuilder();
		builder.add(SentenceAnnotator.getDescription()); // Sentence

		// Tokenization that we can modify if we need to.
		builder.add(AnalysisEngineFactory.createPrimitiveDescription(TokenAnnotator.class,
				TokenAnnotator.PARAM_TOKENIZER_NAME, 
				"edu.isi.bmkeg.uimaBioC.rubicon.tokenizer.PennTreebankTokenizer")); 

		// Some sentences include headers that don't end in periods
		builder.add(AnalysisEngineFactory.createPrimitiveDescription(FixSentencesFromHeadings.class));

		builder.add(AnalysisEngineFactory.createPrimitiveDescription(RemoveSentencesNotInTitleAbstractBody.class,
				RemoveSentencesNotInTitleAbstractBody.PARAM_KEEP_FLOATING_BOXES, "true"));

		builder.add(AnalysisEngineFactory.createPrimitiveDescription(ParagraphTfIdfAnnotator.class,
				CleartkAnnotator.PARAM_IS_TRAINING, true,
				DefaultDataWriterFactory.PARAM_DATA_WRITER_CLASS_NAME, InstanceDataWriter.class.getName(),
				DirectoryDataWriterFactory.PARAM_OUTPUT_DIRECTORY, options.modelDir.getPath()));
		
		cpeBuilder.setAnalysisEngine(builder.createAggregateDescription());
		cpeBuilder.setMaxProcessingUnitThreatCount(options.nThreads);
		StatusCallbackListener callback = new StatusCallbackListenerImpl();
		CollectionProcessingEngine cpe = cpeBuilder.createCpe(callback);
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

		System.out.println("\n\n ------------------ PRELIMINARY DATA PROCESSING ------------------\n");
		System.out.println(cpe.getPerformanceReport().toString());

		long endTime = System.currentTimeMillis();
		float duration = (float) (endTime - startTime);
		System.out.format("\n\nTOTAL EXECUTION TIME: %.3f s", duration / 1000);
		
		startTime = System.currentTimeMillis();
		
		//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
		// Part 2, Extract TF-IDF.
		//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
		// Load the serialized instance data
		Iterable<Instance<String>> instances = InstanceStream.loadFromDirectory(options.modelDir);
		
		// Collect TF*IDF stats for computing tf*idf values on extracted tokens
		URI unigramTfIdfDataURI= new File(options.modelDir, "tfidf_extractor.dat").toURI();
		CleartkExtractor<UimaBioCAnnotation, Token> countsExtractor = 
				new CleartkExtractor<UimaBioCAnnotation, Token>(
						Token.class, 
						new CoveredTextExtractor<Token>(),
						new CleartkExtractor.Count(
								new CleartkExtractor.Covered()
								));		
		TfidfExtractor<String, UimaBioCAnnotation> tfidfExtractor = 
				new TfidfExtractor<String, UimaBioCAnnotation>(
						"tfidf", 
						countsExtractor);
		
		tfidfExtractor.train(instances);
		tfidfExtractor.save(unigramTfIdfDataURI);
		//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
		
		CpeBuilder cpeBuilder2 = new CpeBuilder();
		cpeBuilder2.setReader(crDesc);

		AggregateBuilder builder2 = new AggregateBuilder();

		builder2.add(SentenceAnnotator.getDescription()); // Sentence
		
		builder2.add(AnalysisEngineFactory.createPrimitiveDescription(TokenAnnotator.class,
				TokenAnnotator.PARAM_TOKENIZER_NAME, 
				"edu.isi.bmkeg.uimaBioC.rubicon.tokenizer.PennTreebankTokenizer")); // Tokenization

		//
		// Some sentences include headers that don't end in periods
		//
		builder2.add(AnalysisEngineFactory.createPrimitiveDescription(FixSentencesFromHeadings.class));

		builder2.add(AnalysisEngineFactory.createPrimitiveDescription(RemoveSentencesNotInTitleAbstractBody.class,
				RemoveSentencesNotInTitleAbstractBody.PARAM_KEEP_FLOATING_BOXES, "true"));

		if (options.friesDir != null) {
			builder2.add(AnalysisEngineFactory.createPrimitiveDescription(AddReachAnnotations.class,
					AddReachAnnotations.PARAM_INPUT_DIRECTORY, options.friesDir.getPath()));
		}
		
		builder2.add(AnalysisEngineFactory.createPrimitiveDescription(StanfordParse.class,
				StanfordParse.PARAM_MAX_LENGTH, options.maxSentenceLength));
//		builder2.add(AnalysisEngineFactory.createPrimitiveDescription(StanfordTag.class));

		if (options.clauseLevel) {			
			builder2.add(AnalysisEngineFactory.createPrimitiveDescription(SeparateClauses.class));
		}

		String outFormat = null;
		if( options.outFormat.toLowerCase().equals("xml") ) 
			outFormat = SaveAsBioCDocuments.XML;
		else if( options.outFormat.toLowerCase().equals("json") ) 
			outFormat = SaveAsBioCDocuments.JSON;
		else 
			throw new Exception("Output format " + options.outFormat + " not recognized");

		builder2.add(AnalysisEngineFactory.createPrimitiveDescription(
				SaveAsBioCDocuments.class, 
				SaveAsBioCDocuments.PARAM_FILE_PATH, options.outDir.getPath(),
				SaveAsBioCDocuments.PARAM_FORMAT, outFormat));
		
		cpeBuilder2.setAnalysisEngine(builder.createAggregateDescription());

		cpeBuilder2.setMaxProcessingUnitThreatCount(options.nThreads);
		StatusCallbackListener callback2 = new StatusCallbackListenerImpl();
		CollectionProcessingEngine cpe2 = cpeBuilder.createCpe(callback2);
		cpe2.process();

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

		endTime = System.currentTimeMillis();
		duration = (float) (endTime - startTime);
		System.out.format("\n\nTOTAL EXECUTION TIME: %.3f s", duration / 1000);

	}

	public static URI createTokenTfIdfDataURI(File outputDirectoryName, String code) {
		File f = new File(outputDirectoryName, code + "_tfidf_extractor.dat");
		return f.toURI();
	}
	
}
