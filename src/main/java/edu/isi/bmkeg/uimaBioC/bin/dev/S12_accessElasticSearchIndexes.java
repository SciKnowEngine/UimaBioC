package edu.isi.bmkeg.uimaBioC.bin.dev;

import static org.apache.uima.fit.factory.ExternalResourceFactory.bindExternalResource;

import org.apache.log4j.Logger;
import org.apache.uima.UIMAFramework;
import org.apache.uima.collection.CollectionReader;
import org.apache.uima.collection.CollectionReaderDescription;
import org.apache.uima.fit.factory.CollectionReaderFactory;
import org.apache.uima.fit.spring.SpringContextResourceManager;
import org.apache.uima.resource.metadata.TypeSystemDescription;
import org.cleartk.opennlp.tools.SentenceAnnotator;
import org.cleartk.token.tokenizer.TokenAnnotator;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.stereotype.Component;
import org.uimafit.factory.AggregateBuilder;
import org.uimafit.factory.AnalysisEngineFactory;
import org.uimafit.factory.TypeSystemDescriptionFactory;
import org.uimafit.pipeline.SimplePipeline;

import edu.isi.bmkeg.uimaBioC.elasticSearch.BioCDocumentRepository;
import edu.isi.bmkeg.uimaBioC.uima.ae.core.FixSentencesFromHeadings;
import edu.isi.bmkeg.uimaBioC.uima.readers.BioCDocumentElasticSearchReader;


/** 
 * 
 * @author Gully
 */

@Component
public class S12_accessElasticSearchIndexes {

	@Autowired
	BioCDocumentRepository biocRepo;

	public static class Options {

	}

	private static Logger logger = Logger.getLogger(S12_accessElasticSearchIndexes.class);

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
		ApplicationContext ctx = new AnnotationConfigApplicationContext(
				"edu.isi.bmkeg.uimaBioC"
				);
		
	    // Create resource manager
	    SpringContextResourceManager resMgr = new SpringContextResourceManager();
	    resMgr.setApplicationContext(ctx);
	    resMgr.setAutowireEnabled(true);
	    
		TypeSystemDescription typeSystem = TypeSystemDescriptionFactory.createTypeSystemDescription(
				"bioc.TypeSystem");
		CollectionReaderDescription crDesc = CollectionReaderFactory.createReaderDescription(
				BioCDocumentElasticSearchReader.class,
				typeSystem);
	    bindExternalResource(crDesc, "bioCRepository", 
	    		BioCDocumentElasticSearchReader.BIOC_ES_REPO);
	    
	    CollectionReader cr =  UIMAFramework.produceCollectionReader(crDesc, resMgr, null);
	    
		AggregateBuilder builder = new AggregateBuilder();

		builder.add(SentenceAnnotator.getDescription()); // Sentence
		builder.add(TokenAnnotator.getDescription()); // Tokenization

		// Some sentences include headers that don't end in periods
		builder.add(AnalysisEngineFactory.createPrimitiveDescription(FixSentencesFromHeadings.class));

		SimplePipeline.runPipeline(crDesc, builder.createAggregateDescription());

	}

}
