/*
 * Copyright 2011 University of Southern California 
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); 
 * you may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at 
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0 
 *      
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, 
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
 * See the License for the specific language governing permissions and
 * limitations under the License. 
 * 
 * Note that this code is adapted from tratz.parse.ParserScript and is 
 * intended as a UIMA wrapper around the core Tratz Fanse Parser System
 * 
 */

package edu.isi.bmkeg.uimaBioC.rubicon;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.Map;

import org.apache.log4j.Logger;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.uimafit.component.JCasAnnotator_ImplBase;
import org.uimafit.descriptor.ConfigurationParameter;
import org.uimafit.factory.ConfigurationParameterFactory;
import org.uimafit.util.JCasUtil;

import bioc.type.UimaBioCDocument;
import bioc.type.UimaBioCPassage;
import cc.mallet.fst.SimpleTagger;
import edu.isi.bmkeg.uimaBioC.UimaBioCUtils;
import tratz.parse.FullSystemWrapper;
import tratz.parse.io.SentenceWriter;
import tratz.parse.io.TokenizingSentenceReader;
import tratz.parse.types.Parse;
import tratz.parse.types.Sentence;

/**
 * 
 */
public class RunCRFAnnotator extends JCasAnnotator_ImplBase {

	private static Logger logger = Logger
			.getLogger(RunCRFAnnotator.class);
	
	public final static String PARAM_OUT_DIR_PATH = ConfigurationParameterFactory
			.createConfigurationParameterName(RunCRFAnnotator.class,
					"outCrfDirPath");
	@ConfigurationParameter(mandatory = true, description = "The place to put the CRF files")
	String outCrfDirPath;
	
	FullSystemWrapper fullSystemWrapper;
	SentenceWriter sentenceWriter;
	
	public void initialize(UimaContext context) throws ResourceInitializationException {

		super.initialize(context);

	}

	@Override
	public void process(JCas jCas) throws AnalysisEngineProcessException {

		try {
			
			UimaBioCDocument uiD = JCasUtil.selectSingle(jCas,
					UimaBioCDocument.class);
			UimaBioCPassage docP = UimaBioCUtils
					.readDocumentUimaBioCPassage(jCas);						

			Map<String, String> docInfo = UimaBioCUtils.convertInfons(uiD.getInfons());
			String crfIn = docInfo.get("crf_in_file");
			String crfModel = docInfo.get("crf_model_file");
			String crfOut = outCrfDirPath + "/" + uiD.getId() + "_crf_out";
			
			String[] argv = {
							"--train", "false",
							"--threads", "10",
							"--model-file", crfModel,
							crfIn
							};
			
			PrintStream stdout = System.out;
			System.setOut(new PrintStream(new File(crfOut)));
			SimpleTagger.main(argv);
			System.setOut(stdout);
			
		} catch (Exception e) {

			throw new AnalysisEngineProcessException(e);

		}		
		
	}
	
	public static Sentence convertClearTkToFanse(org.cleartk.token.type.Sentence ss) 
			throws IOException {
		
		TokenizingSentenceReader reader = new TokenizingSentenceReader();
		
		String str = "";
		for(org.cleartk.token.type.Token tt : JCasUtil.selectCovered(
				org.cleartk.token.type.Token.class, ss) ) {
			if( str.length() > 0 ) 
				str += " ";
			str += tt.getCoveredText();
		}
		if( !str.endsWith(".") )
			str += " .";
		
		InputStream is = new ByteArrayInputStream(str.getBytes());
		BufferedReader br = new BufferedReader(new InputStreamReader(is));
		Parse parse = reader.readSentence(br);
		
		return parse.getSentence();
	
	}

}