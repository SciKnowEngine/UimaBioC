package edu.isi.bmkeg.uicBioC.test;

import java.io.File;
import java.net.URL;

import junit.framework.TestCase;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import edu.isi.bmkeg.uimaBioC.UimaBioCProperties;
import edu.isi.bmkeg.uimaBioC.bin.S02_SimpleRunNxml2Txt;

public class T01_SimpleRunNxmlToTextTest extends TestCase {
		
	File inDir, outDir, execPath;
	
	@Before
	public void setUp() throws Exception {
		
		URL url = ClassLoader.getSystemResource("01_plOpenAccess_data/coprecipitation/coprecipitationPmids.txt");
		
		File pmidFile = new File(url.getFile());
		inDir = pmidFile.getParentFile();
		outDir = new File(inDir.getParentFile().getPath() + "/txt");
		outDir.mkdirs();

		execPath = new File(UimaBioCProperties.readNxml2txtLocation(true));
				
	}

	@After
	public void tearDown() throws Exception {
		
		super.tearDown();
		
	}
	
	@Test
	public final void testBuildTriageCorpusFromScratch() throws Exception {

		String[] args = new String[] { 
				"-inDir", inDir.getPath(), 
				"-outDir", outDir.getPath(), 
				"-execPath", execPath.getPath(), 
				};

		S02_SimpleRunNxml2Txt.main(args);
						
	}
		
}

