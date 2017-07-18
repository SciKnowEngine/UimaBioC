package edu.isi.bmkeg.uimaBioC;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.bigmech.fries.FRIES_EventMention;

public class ReachUtils {

	private static Logger logger = Logger.getLogger(ReachUtils.class);

	public static Pattern bra = Pattern.compile("(\\(.*?)\\Z");
	public static Pattern ket = Pattern.compile("\\A(.*?\\))");
	public static Pattern doiPatt = Pattern.compile("^[\\d\\.]+\\/\\S+$");
	
	public static Map<String, File> getReachFileHash(String id, File inDir) {
		
		String[] fileTypes = { "json" };
		Collection<File> files = (Collection<File>) FileUtils.listFiles(inDir, fileTypes, true);
		File sentenceFrames = null;
		File eventFrames = null;
		File entityFrames = null;
		for (File f : files) {
			if (f.getName().startsWith(id + ".uaz.sentences") ) {
				sentenceFrames = f;
				break;
			}
		}
		for (File f : files) {
			if (f.getName().startsWith(id + ".uaz.events") ) {
				eventFrames = f;
				break;
			}
		}
		for (File f : files) {
			if (f.getName().startsWith(id + ".uaz.entities") ) {
				entityFrames = f;
				break;
			}
		}
		
		Map<String, File> fileHash = new HashMap<String, File>(); 
		fileHash.put("sentences", sentenceFrames);
		fileHash.put("events", eventFrames);
		fileHash.put("entities", entityFrames);

		return fileHash;
		
	}
	
	public static String getEventSummary(FRIES_EventMention ev) {
		
		String str = "";
		
		str += ev.getFrameId();
		str += "|" + ev.getType();
		if( ev.getSubtype() != null ) 
			str += "|" + ev.getSubtype();
		
		
		return str;
		
	}
	
}
