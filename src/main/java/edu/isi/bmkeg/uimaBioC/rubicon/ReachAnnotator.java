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


/**
 * 
 */
public class ReachAnnotator extends JCasAnnotator_ImplBase {

	private static Logger logger = Logger
			.getLogger(ReachAnnotator.class);

	public final static String PARAM_SECTION_ANNOTATION = ConfigurationParameterFactory
			.createConfigurationParameterName(ReachAnnotator.class,
					"sectionAnnotation");
	@ConfigurationParameter(mandatory = false, description = "The subsection of the paper to be extracted")
	String sectionAnnotation;
	
	public final static String PARAM_OUT_REACH_DIR_PATH = ConfigurationParameterFactory
			.createConfigurationParameterName(ReachAnnotator.class,
					"outReachDirPath");
	@ConfigurationParameter(mandatory = true, description = "The place to put the parse files")
	String outReachDirPath;
		
	//ReachSystem reach;
	
	public void initialize(UimaContext context) throws ResourceInitializationException {

		super.initialize(context);
				
		/*scala.Option<Rules> x = scala.Option.apply(null);
		scala.Option<BioNLPProcessor> y = scala.Option.apply(null);
		reach = new ReachSystem(x, y);*/
		
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
			
			String outFile = outReachDirPath + "/" + uiD.getId() ;
			
			String docId = uiD.getId();
			int chunkId = 0;

			/*for (Sentence ss : sentences) {
				Seq<Mention> seq = reach.extractFrom(ss.getCoveredText(), docId, chunkId + "");
				List<Mention> list = JavaConversions.seqAsJavaList(seq);
				chunkId++;
			}*/
			
			Map<String, String> docInfo = UimaBioCUtils.convertInfons(uiD.getInfons());
			docInfo.put("crf_in_file", outFile);
			
			String crfModelFile = this.getClass().getResource("/rubicon/para_model.crf").getFile();
			docInfo.put("crf_model_file", crfModelFile);
			
			uiD.setInfons(UimaBioCUtils.convertInfons(docInfo, jCas));
			
		} catch (Exception e) {

			throw new AnalysisEngineProcessException(e);

		}		
		
	}
	
	

}