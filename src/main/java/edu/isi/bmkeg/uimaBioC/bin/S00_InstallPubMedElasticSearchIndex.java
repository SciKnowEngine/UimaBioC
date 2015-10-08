package edu.isi.bmkeg.uimaBioC.bin;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;

import org.apache.log4j.Logger;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import edu.isi.bmkeg.uimaBioC.PubMedESIndex;

/**
 * This script loads a file of PMC or PMID values and permits you to 
 * convert to the other. This requires that Elastic Search be running
 * on the server. 
 * 
 * @author Gully
 * 
 */
public class S00_InstallPubMedElasticSearchIndex {

	public static class Options {

		@Option(name = "-pubmedFiles", 
				usage = "Local File where files from PubMed can be stored",
				required = true, metaVar = "PUBMED_FILES")
		public File pubmedFiles;

		@Option(name = "-clusterNameFiles", 
				usage = "Name used for the Elastic Search Cluster",
				required = true, metaVar = "ES_CLUSTER_NAME")
		public String clusterName;
		
	}

	private static Logger logger = Logger
			.getLogger(S00_InstallPubMedElasticSearchIndex.class);

	/**0
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

		PubMedESIndex pmES = new PubMedESIndex(options.pubmedFiles, options.clusterName);
				
	}

}
