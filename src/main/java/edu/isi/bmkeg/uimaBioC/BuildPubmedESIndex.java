package edu.isi.bmkeg.uimaBioC;

import java.io.File;

import org.apache.log4j.Logger;
import org.apache.uima.pear.util.FileUtil;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import com.google.common.io.Files;

/**
 * This script loads a file of PMC or PMID values and permits you to 
 * convert to the other. This requires that Elastic Search be running
 * on the server. 
 * 
 * @author Gully
 * 
 */
public class BuildPubmedESIndex {

	public static class Options {

		@Option(name = "-pmcFileListDir", usage = "PMC File List Directory", required = true, metaVar = "DIR")
		public File pmcFileListDir;
				
	}

	private static Logger logger = Logger
			.getLogger(BuildPubmedESIndex.class);

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

		File temp = Files.createTempDir();

		PubMedESIndex pmES = new PubMedESIndex(temp);
		
	}

}
