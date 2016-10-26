package edu.isi.bmkeg.uimaBioC.io.scienceDirect;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;

import org.apache.log4j.Logger;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

/**
 * This script runs through serialized JSON files from the model and converts
 * them to VPDMf KEfED models, including the data.
 * 
 * @author Gully
 * 
 */
public class SciDir03_RetrieveFullTextFromScienceDirect {

	public static class Options {

		@Option(name = "-searchFile", usage = "Input File", required = true, metaVar = "INPUT")
		public File input;

		@Option(name = "-pmidColumnNumber", usage = "PMID Column Number", required = true, metaVar = "COL")
		public int pmidCol;

		@Option(name = "-apiKey", usage = "API String", required = true, metaVar = "APIKEY")
		public String apiKey;

		@Option(name = "-outDir", usage = "Output", required = true, metaVar = "OUTPUT")
		public File outDir;

		@Option(name = "-format", usage = "Format", required = false, metaVar = "FORMAT")
		public String format = "text/xml";

	}

	private static Logger logger = Logger.getLogger(SciDir03_RetrieveFullTextFromScienceDirect.class);

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

		SciDir03_RetrieveFullTextFromScienceDirect readScienceDirect = new SciDir03_RetrieveFullTextFromScienceDirect();

		if (!options.outDir.exists())
			options.outDir.mkdirs();

		// http://api.elsevier.com/content/article/pii/S0001457515000780?apiKey=97f607fc347bd27badcddcab8decb51f&httpAccept=application/json

		BufferedReader in = new BufferedReader(new FileReader(options.input));
		String inputLine;
		while ((inputLine = in.readLine()) != null) {
			String[] fields = inputLine.split("\\t");

			if (fields.length < options.pmidCol)
				continue;

			String pmid = fields[options.pmidCol - 1];
			String piiUrl = "http://api.elsevier.com/content/article/pubmed_id/" + pmid;

			URL url = new URL(piiUrl + "?apiKey=" + options.apiKey + "&httpAccept=" + options.format);
			URLConnection urlConnect = url.openConnection();
			urlConnect.setDoInput(true);
			urlConnect.setDoOutput(true);

			byte[] buffer = new byte[8 * 1024];

			String f = options.format;
			String suffix = f.substring(f.length() - 3, f.length());
			File ff = new File(options.outDir.getPath() + "/" + pmid + "." + suffix);
			if (ff.exists())
				continue;

			InputStream input = null;
			OutputStream output = null;
			
			try {
				input = urlConnect.getInputStream();
				output = new FileOutputStream(ff);
					int bytesRead;
				while ((bytesRead = input.read(buffer)) != -1) {
					output.write(buffer, 0, bytesRead);
				}

				logger.info(pmid + " download complete.");

			} catch(FileNotFoundException e) {

				logger.info(pmid + " not found on ScienceDirect.");
			
			} catch(IOException e2) {

				logger.info(pmid + " download error.");
			
			} finally {
				if( output != null)
					output.close();
				if( input != null)
					input.close();
			}

			/*
			 * PrintWriter out = new PrintWriter(new BufferedWriter(new
			 * FileWriter(ff, true))); BufferedReader in2 = new
			 * BufferedReader(new InputStreamReader(url.openStream())); String
			 * inputLine2; while ((inputLine2 = in2.readLine()) != null)
			 * out.println(inputLine2); in2.close(); out.close();
			 */

			logger.info("File Downloaded: " + piiUrl);

		}
		in.close();

	}

}
