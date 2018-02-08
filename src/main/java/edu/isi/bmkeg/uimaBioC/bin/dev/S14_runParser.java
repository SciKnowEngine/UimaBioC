package edu.isi.bmkeg.uimaBioC.bin.dev;

import java.io.File;

import org.apache.log4j.Logger;
import org.apache.uima.UIMAFramework;
import org.apache.uima.collection.CollectionReader;
import org.apache.uima.collection.CollectionReaderDescription;
import org.apache.uima.fit.factory.CollectionReaderFactory;
import org.apache.uima.resource.metadata.TypeSystemDescription;
import org.cleartk.opennlp.tools.SentenceAnnotator;
import org.cleartk.token.tokenizer.TokenAnnotator;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.uimafit.factory.AggregateBuilder;
import org.uimafit.factory.AnalysisEngineFactory;
import org.uimafit.factory.CpeBuilder;
import org.uimafit.factory.TypeSystemDescriptionFactory;
import org.uimafit.pipeline.SimplePipeline;

import edu.isi.bmkeg.uimaBioC.elasticSearch.BioCDocumentRepository;
import edu.isi.bmkeg.uimaBioC.rubicon.RemoveSentencesNotInTitleAbstractBody;
import edu.isi.bmkeg.uimaBioC.rubicon.StanfordParse;
import edu.isi.bmkeg.uimaBioC.uima.ae.core.FixSentencesFromHeadings;
import edu.isi.bmkeg.uimaBioC.uima.readers.BioCCollectionReader;


/** 
 *  * @author Gully
 *
 */

@Component
public class S14_runParser {

	@Autowired
	BioCDocumentRepository biocRepo;

	public static class Options {
		
		@Option(name = "-biocDir", usage = "Input Directory", required = true, metaVar = "IN-DIRECTORY")
		public File biocDir;
	
	}
	private static Logger logger = Logger.getLogger(S14_runParser.class);

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
	    
		TypeSystemDescription typeSystem = TypeSystemDescriptionFactory.createTypeSystemDescription(
				"bioc.TypeSystem");
		CollectionReaderDescription crDesc = CollectionReaderFactory.createReaderDescription(
				BioCCollectionReader.class, typeSystem,
				BioCCollectionReader.INPUT_DIRECTORY, options.biocDir,
				BioCCollectionReader.PARAM_FORMAT, BioCCollectionReader.JSON);
	    
	    CollectionReader cr =  UIMAFramework.produceCollectionReader(crDesc, null, null);	    
	    
		AggregateBuilder builder = new AggregateBuilder();

		builder.add(SentenceAnnotator.getDescription()); // Sentence
		builder.add(TokenAnnotator.getDescription()); // Tokenization

		// Some sentences include headers that don't end in periods
		builder.add(AnalysisEngineFactory.createPrimitiveDescription(FixSentencesFromHeadings.class));

		// Strip out not results sections where we aren't interested in them
		builder.add(AnalysisEngineFactory.createPrimitiveDescription(RemoveSentencesNotInTitleAbstractBody.class));

		// Rerun Pradeep's system to create clauses from the text
		builder.add(AnalysisEngineFactory.createPrimitiveDescription(
				StanfordParse.class));
		
		CpeBuilder builder2 = new CpeBuilder();
		builder2.setMaxProcessingUnitThreatCount(10);
		SimplePipeline.runPipeline(crDesc, builder.createAggregateDescription());

	}

}
