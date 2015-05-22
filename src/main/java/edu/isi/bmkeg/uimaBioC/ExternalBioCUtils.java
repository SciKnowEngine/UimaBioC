package edu.isi.bmkeg.uimaBioC;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.FSArray;
import org.uimafit.util.JCasUtil;

import bioc.BioCAnnotation;
import bioc.BioCDocument;
import bioc.BioCLocation;
import bioc.BioCNode;
import bioc.BioCPassage;
import bioc.BioCRelation;
import bioc.BioCSentence;
import bioc.type.MapEntry;
import bioc.type.UimaBioCAnnotation;
import bioc.type.UimaBioCDocument;
import bioc.type.UimaBioCLocation;
import bioc.type.UimaBioCNode;
import bioc.type.UimaBioCPassage;
import bioc.type.UimaBioCRelation;
import bioc.type.UimaBioCSentence;

public class ExternalBioCUtils {

	public static void runNxml2TextPython(File f, 
			String suffix, File inDir, File outDir,
			File execPath) throws Exception {

		String newPath = f.getPath().replaceAll(
				inDir.getPath(),
				outDir.getPath());
		String s = "." + suffix + "$";
		File txtFile = new File(newPath.replaceAll(s, ".txt"));
		File annFile = new File(newPath.replaceAll(s, ".so"));
		File logFile = new File(newPath.replaceAll(s, "_nxml2txt.log"));
		txtFile.getParentFile().mkdirs();

		String command = "python " + execPath.getPath() + " " + f.getPath() 
				+ " " + txtFile.getPath()
				+ " " + annFile.getPath() + "";

		if( txtFile.getPath().contains(" ") )
			command = "python " + execPath.getPath() + " \"" + f.getPath() 
			+ "\" \"" + txtFile.getPath()
			+ "\" \"" + annFile.getPath() + "\"";

		ProcessBuilder pb = new ProcessBuilder(command.split(" "));
		Map<String,String> env = pb.environment();
		env.put("PYTHONPATH", "/usr/local/lib/python2.7/site-packages");
		Process p = pb.start();
		
		if (p == null) {
			throw new Exception("Can't execute " + command);
		}

		InputStream in = p.getErrorStream();
		BufferedInputStream buf = new BufferedInputStream(in);
		InputStreamReader inread = new InputStreamReader(buf);
		BufferedReader bufferedreader = new BufferedReader(inread);
		String line, out = "";

		while ((line = bufferedreader.readLine()) != null) {
			out += line;
		}
		
		try {
			if (p.waitFor() != 0) {
				System.err.println("CMD: " + command);
				System.err.println("RETURNED ERROR: " + out);
			}
		} catch (Exception e) {
			System.err.println(out);
		} finally {
			// Close the InputStream
			bufferedreader.close();
			inread.close();
			buf.close();
			in.close();
		}

	}

}
