package edu.isi.bmkeg.uimaBioC;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Properties;

public class UimaBioCProperties {

	public static final String PROP_NXML2TXT_LOCATION = "nxml2txt.location";
	
	private String nxml2txtLocation;
	
	public static String readNxml2txtLocation(boolean isTest) throws IOException  {
		UimaBioCProperties p = new UimaBioCProperties(isTest);
		return p.getNxml2txtLocation();
	}
	

	/**
	 * This is the default constructor that is needed for Spring
	 */
	public UimaBioCProperties() {}

	/**
	 * A modified constructor for nonSpring use, this is looking in 
	 * Spring-like places for your properties file. 
	 * 1. Your home directory: ~/bmkeg
	 * 2. environment variable: BMKEG_PROPERTIESFILE
	 */
	public UimaBioCProperties(boolean isTest) throws IOException {
		
		Properties properties = new Properties();
		
		Map<String, String> env = System.getenv();
		String homeDir = System.getProperty("user.home");
		
		String fileName = ".uimabioc.properties";
		if( isTest )
			fileName = ".uimabioctest.properties";
		
		File propFile = new File(homeDir + "/" + fileName);
		
		if( propFile.exists() ) {

			properties.load(new FileInputStream(propFile));			

		} else if (env.containsKey("UIMABIOC_PROPERTIES")){

			String propFileEnv = env.get("UIMABIOC_PROPERTIES");
			propFile = new File(propFileEnv + fileName);
			if( propFile.exists() )
				properties.load(new FileInputStream(propFile));
			else 
				throw new IOException(propFile.getPath() + "does not exist.");
			
		} else {

			throw new IOException("Properties file not specified");
			
		}
					
	    this.setNxml2txtLocation((String) properties.get(PROP_NXML2TXT_LOCATION));
		
	}

	/**
	 * A modified constructor for nonSpring use, this is looking in 
	 * Spring-like places for your properties file. 
	 * 1. Your home directory: ~/.uimabioc.properties
	 * 2. environment variable: UIMABIOC_PROPERTIES
	 */
	public UimaBioCProperties(File propFile) throws IOException {
		
		Properties properties = new Properties();

		if( propFile.exists() ) {

			properties.load(new FileInputStream(propFile));			

		} else {

			throw new IOException("Properties file not specified");
			
		}
					
	    this.setNxml2txtLocation((String) properties.get(PROP_NXML2TXT_LOCATION));
	    
	}

	public String getNxml2txtLocation() {
		return nxml2txtLocation;
	}

	public void setNxml2txtLocation(String nxml2txtLocation) {
		this.nxml2txtLocation = nxml2txtLocation;
	}
	
}
