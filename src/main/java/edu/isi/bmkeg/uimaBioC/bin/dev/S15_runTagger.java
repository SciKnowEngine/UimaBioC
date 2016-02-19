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
import org.uimafit.factory.TypeSystemDescriptionFactory;
import org.uimafit.pipeline.SimplePipeline;

import edu.isi.bmkeg.uimaBioC.elasticSearch.BioCRepository;
import edu.isi.bmkeg.uimaBioC.rubicon.RemoveSentencesFromOtherSections;
import edu.isi.bmkeg.uimaBioC.rubicon.StanfordTag;
import edu.isi.bmkeg.uimaBioC.uima.ae.core.FixSentencesFromHeadings;
import edu.isi.bmkeg.uimaBioC.uima.readers.BioCCollectionReader;


/** 
 * 
 * REQUIRES UIMA FIT SPRING AND DOES NOT WORK
 * 
 * @author Gully
 *
 */

@Component
public class S15_runTagger {

	@Autowired
	BioCRepository biocRepo;

	public static class Options {
		
		@Option(name = "-biocDir", usage = "Input Directory", required = true, metaVar = "IN-DIRECTORY")
		public File biocDir;
		
		@Option(name = "-ann2Extract", usage = "Annotation Type to Extract", required = true, metaVar = "ANNOTATION")
		public File ann2Ext;
	
	}
	private static Logger logger = Logger.getLogger(S15_runTagger.class);

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
		builder.add(AnalysisEngineFactory.createPrimitiveDescription(
				RemoveSentencesFromOtherSections.class,
				RemoveSentencesFromOtherSections.PARAM_ANNOT_2_EXTRACT,
				options.ann2Ext,					
				RemoveSentencesFromOtherSections.PARAM_KEEP_FLOATING_BOXES, 
				"false"));

		// Rerun Pradeep's system to create clauses from the text
		builder.add(AnalysisEngineFactory.createPrimitiveDescription(
				StanfordTag.class));
		
		SimplePipeline.runPipeline(crDesc, builder.createAggregateDescription());

	}

}
