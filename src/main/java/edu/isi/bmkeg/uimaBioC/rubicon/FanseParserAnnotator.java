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
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;
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

import bioc.type.UimaBioCAnnotation;
import bioc.type.UimaBioCDocument;
import bioc.type.UimaBioCPassage;
import edu.isi.bmkeg.uimaBioC.UimaBioCUtils;
import tratz.parse.FullSystemWrapper;
import tratz.parse.FullSystemWrapper.FullSystemResult;
import tratz.parse.io.SentenceWriter;
import tratz.parse.io.TokenizingSentenceReader;
import tratz.parse.types.Parse;
import tratz.parse.types.Sentence;
import tratz.parse.types.Token;

/**
 * Iterates the FANSE PARSER over all sentences in the CAS.
 */
public class FanseParserAnnotator extends JCasAnnotator_ImplBase {

	private static Logger logger = Logger
			.getLogger(FanseParserAnnotator.class);
	
	public static final String OPT_POS_MODEL = ConfigurationParameterFactory
			.createConfigurationParameterName(
					FanseParserAnnotator.class, "posModelFile");
	@ConfigurationParameter(mandatory = false, description = "part-of-speech tagging model file")
	protected String posModelFile;
	
	public static final String OPT_PARSE_MODEL = ConfigurationParameterFactory
			.createConfigurationParameterName(
					FanseParserAnnotator.class, "parseModelFile");
	@ConfigurationParameter(mandatory = false, description = "parser model file")
	protected String parseModelFile;
	
	public static final String OPT_POSSESSIVES_MODEL = ConfigurationParameterFactory
			.createConfigurationParameterName(
					FanseParserAnnotator.class, "possessivesModelFile");
	@ConfigurationParameter(mandatory = false, description = "possessives interpretation model file")
	protected String possessivesModelFile;
	
	public static final String OPT_NOUN_COMPOUND_MODEL = ConfigurationParameterFactory
			.createConfigurationParameterName(
					FanseParserAnnotator.class, "nounCompoundModelFile");
	@ConfigurationParameter(mandatory = false, description = "noun compound interpretation model file")
	protected String nounCompoundModelFile;

	public static final String OPT_PREPOSITIONS_MODEL = ConfigurationParameterFactory
			.createConfigurationParameterName(
					FanseParserAnnotator.class, "prepositionModelFile");
	@ConfigurationParameter(mandatory = false, description = "preposition disambiguation models file")
	protected String prepositionModelFile;
	
	public static final String OPT_SRL_ARGS_MODEL = ConfigurationParameterFactory
			.createConfigurationParameterName(
					FanseParserAnnotator.class, "srlArgsModelFile");
	@ConfigurationParameter(mandatory = false, description = "semantic role labeling model file")
	protected String srlArgsModelFile;

	public static final String OPT_SRL_PREDICATES_MODEL = ConfigurationParameterFactory
			.createConfigurationParameterName(
					FanseParserAnnotator.class, "srlPredicatesModelFile");
	@ConfigurationParameter(mandatory = false, description = "semantic role labeling model file")
	protected String srlPredicatesModelFile;
	
	public static final String OPT_WORDNET_DIR = ConfigurationParameterFactory
			.createConfigurationParameterName(
					FanseParserAnnotator.class, "wnDir");
	@ConfigurationParameter(mandatory = false, description = "WordNet dictionary (dict) directory")
	protected String wnDir;

	public final static String PARAM_SECTION_ANNOTATION = ConfigurationParameterFactory
			.createConfigurationParameterName(FanseParserAnnotator.class,
					"sectionAnnotation");
	@ConfigurationParameter(mandatory = false, description = "The subsection of the paper to be extracted")
	String sectionAnnotation;
	
	public final static String PARAM_OUT_FANSE_DIR_PATH = ConfigurationParameterFactory
			.createConfigurationParameterName(FanseParserAnnotator.class,
					"outFanseDirPath");
	@ConfigurationParameter(mandatory = true, description = "The place to put the parse files")
	String outFanseDirPath;
	
	FullSystemWrapper fullSystemWrapper;
	SentenceWriter sentenceWriter;
	
	public void initialize(UimaContext context) throws ResourceInitializationException {

		super.initialize(context);

		if( posModelFile == null )
			posModelFile = this.getClass().getResource("/fanseParserFiles/posTaggingModel.gz").getFile();
		
		if( wnDir == null )
			wnDir = this.getClass().getResource("/wordnet3").getFile();
		
		if( parseModelFile == null )
			parseModelFile = this.getClass().getResource("/fanseParserFiles/parseModel.gz").getFile();

		if( possessivesModelFile == null )
			possessivesModelFile = this.getClass().getResource("/fanseParserFiles/possessivesModel.gz").getFile();

		if( nounCompoundModelFile == null )
			nounCompoundModelFile = this.getClass().getResource("/fanseParserFiles/nnModel.gz").getFile();
		
		if( prepositionModelFile == null )
			prepositionModelFile = this.getClass().getResource("/fanseParserFiles/psdModels.gz").getFile();

		if( srlArgsModelFile == null )
			srlArgsModelFile = this.getClass().getResource("/fanseParserFiles/srlArgsWrapper.gz").getFile();

		if( srlPredicatesModelFile == null )
			srlPredicatesModelFile = this.getClass().getResource("/fanseParserFiles/srlPredWrapper.gz").getFile();

		try {
			
			fullSystemWrapper = new FullSystemWrapper(
					prepositionModelFile, 
					nounCompoundModelFile,
					possessivesModelFile, 
					srlArgsModelFile, 
					srlPredicatesModelFile, 
					posModelFile, 
					parseModelFile, 
					wnDir);
		
		
			sentenceWriter = new RubiconSentenceWriter();
			
		} catch (ClassNotFoundException e) {
			throw new ResourceInitializationException(e);
		} catch (IOException e) {
			throw new ResourceInitializationException(e);
		} 
	}

	@Override
	public void process(JCas jCas) throws AnalysisEngineProcessException {

		try {
			
			UimaBioCDocument uiD = JCasUtil.selectSingle(jCas,
					UimaBioCDocument.class);
			if( uiD.getId().equals("skip") )
				return;
			
			UimaBioCPassage docP = UimaBioCUtils
					.readDocumentUimaBioCPassage(jCas);
						
			List<org.cleartk.token.type.Sentence> sentences = null;
			if( sectionAnnotation != null ){

				String sa = sectionAnnotation.toLowerCase();
				
				List<UimaBioCAnnotation> annotations = JCasUtil.selectCovered(
						UimaBioCAnnotation.class, docP);
				for (UimaBioCAnnotation a : annotations) {
					Map<String, String> inf = UimaBioCUtils.convertInfons(a.getInfons());
					if( inf.containsKey("sectionHeading") 
							&& inf.get("sectionHeading").toLowerCase().startsWith(sa)) {
						sentences = JCasUtil.selectCovered(
								org.cleartk.token.type.Sentence.class, a
								);
						break;
					}
				}	
				
			} else {
				List<UimaBioCAnnotation> annotations = JCasUtil.selectCovered(
						UimaBioCAnnotation.class, docP);
				for (UimaBioCAnnotation a : annotations) {
					Map<String, String> inf = UimaBioCUtils.convertInfons(a.getInfons());
					if( inf.containsKey("value") 
							&& inf.get("value").toLowerCase().equals("body")) {
						sentences = JCasUtil.selectCovered(
								org.cleartk.token.type.Sentence.class, a
								);
						break;
					}
				}	
			}

			if( sentences == null) {
				List<UimaBioCAnnotation> annotations = JCasUtil.selectCovered(
						UimaBioCAnnotation.class, docP);
				for (UimaBioCAnnotation a : annotations) {
					Map<String, String> inf = UimaBioCUtils.convertInfons(a.getInfons());
					if( inf.containsKey("value") 
							&& inf.get("value").toLowerCase().equals("body")) {
						sentences = JCasUtil.selectCovered(
								org.cleartk.token.type.Sentence.class, a
								);
						break;
					}
				}
			}
			
			if (sentences == null) {
				Exception e = new Exception("Can't find text in document to parse");
				throw new AnalysisEngineProcessException(e);
			}
			
			String outFile = outFanseDirPath + "/" + uiD.getId() + "_crf_in" ;
			
			logger.info(uiD.getId());

			Map<String, String> writerArgsMap = new HashMap<String, String>();
			writerArgsMap.put(RubiconSentenceWriter.PARAM_OUTPUT_FILE, outFile);
			sentenceWriter.initialize(writerArgsMap);
			
			for (org.cleartk.token.type.Sentence ss : sentences) {
				Sentence sentence = convertClearTkToFanse(ss);

				List<Token> tokens = sentence.getTokens(); 

				FullSystemResult result = fullSystemWrapper.process(sentence,
						tokens.size() > 0 && tokens.get(0).getPos() == null, true, true, true, true, true);

				// Output sentence
				sentenceWriter.appendSentence(sentence, result.getParse(),
						result.getSrlParse() == null ? null : result.getSrlParse().getHeadArcs());
				
			}
			sentenceWriter.close();
			
			Map<String, String> docInfo = UimaBioCUtils.convertInfons(uiD.getInfons());
			docInfo.put("crf_in_file", outFile);
			
			String crfModelFile = this.getClass().getResource("/rubicon/para_model.crf").getFile();
			docInfo.put("crf_model_file", crfModelFile);
			
			uiD.setInfons(UimaBioCUtils.convertInfons(docInfo, jCas));
			
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