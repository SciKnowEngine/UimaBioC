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
public class S00_PmcidPmidConverter {

	public static class Options {

		@Option(name = "-inFile", usage = "Input", required = true, metaVar = "INPUT")
		public File inFile;

		@Option(name = "-outFile", usage = "Output", required = true, metaVar = "OUTPUT")
		public File outFile;
		
		@Option(name = "-pmcDir", usage = "OA Pubmed Central Dump Location", required = false, metaVar = "DATA")
		public File pmcDir;

		@Option(name = "-outDir", usage = "Directory where pdfs + nxmls should be written", required = false, metaVar = "OUTPUT DIR")
		public File outDir;

		@Option(name = "-getPdfs", usage = "Flag to determine if PDFs are downloaded too", required = false, metaVar = "PDFS?")
		public boolean getPdfs = false;

		
	}

	private static Logger logger = Logger
			.getLogger(S00_PmcidPmidConverter.class);

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

		PubMedESIndex pmES = new PubMedESIndex();
		
		if( !options.outFile.getParentFile().exists() )
			options.outFile.getParentFile().mkdirs();
		
		BufferedReader in = new BufferedReader(new InputStreamReader(
				new FileInputStream(options.inFile)));
		
		if(options.outFile.exists())
			options.outFile.delete();
				
		PrintWriter out = new PrintWriter(
				new BufferedWriter(
						new FileWriter(
				options.outFile, true)));
		String inputLine;
		
		while ((inputLine = in.readLine()) != null) {

			try {
				
				boolean isPmid = true;
				if( inputLine.startsWith("PMC") ) {
					isPmid = false;
				}
				
				if( (isPmid && pmES.hasEntry("pmid", inputLine, "nxml")) ||
						(!isPmid && pmES.hasEntry("pmcId", inputLine, "nxml")) ) {
							
					Map<String,Object> nxmlMap = null;
					if( isPmid ) {
						nxmlMap = pmES.getMapFromTerm("pmid", inputLine, "nxml");
					} else { 
						nxmlMap = pmES.getMapFromTerm("pmcId", inputLine, "nxml");
					}
					out.println(nxmlMap.get("pmcId") + "\t" + nxmlMap.get("pmid") );
					
					if( nxmlMap == null )
						continue;
					
					String pmid = (String) nxmlMap.get("pmid");
					
					if( options.pmcDir != null ) {
						String nxmlLoc = (String) nxmlMap.get("nxml_location");
						File xml = new File(options.pmcDir.getPath() + "/" + nxmlLoc);
						File target = new File(options.outDir.getPath() + "/" + pmid + ".nxml");
						if(!target.exists() ) {
							if(xml.exists()) {
								// Copy the file over the local file subsystem.
								target.getParentFile().mkdirs();
								Files.copy(xml.toPath(), 
									target.toPath(), 
									StandardCopyOption.REPLACE_EXISTING);
							}
						}
					}
										
					if( options.getPdfs) {

						File outPdf = new File(options.outDir.getPath() + 
								"/" + pmid + ".pdf");

						if( !outPdf.exists() ) {
							
							Map<String,Object> pdfMap = null;
							if( isPmid )
								pdfMap = pmES.getMapFromTerm("pmid", inputLine, "pdf");
							else 
								pdfMap = pmES.getMapFromTerm("pmcId", inputLine, "pdf");
							
							if( pdfMap == null )
								continue;
							
							URL pdfUrl = new URL( (String) pdfMap.get("pdf_location") );
							InputStream input = pdfUrl.openStream();
							byte[] buffer = new byte[4096];
							int n = - 1;
							
							if( !outPdf.getParentFile().exists() )
								outPdf.getParentFile().mkdirs();
							OutputStream output = new FileOutputStream( outPdf );
							while ( (n = input.read(buffer)) != -1) {
								output.write(buffer, 0, n);
							}
							input.close();
						}
						
					}
					
				} else {
					
					logger.info(inputLine + " not in open access index");
				
				}
				
			} catch (NumberFormatException e) {
				// just ignore these, skip to the next.
			}

		}

		out.close();
		
	}

}
