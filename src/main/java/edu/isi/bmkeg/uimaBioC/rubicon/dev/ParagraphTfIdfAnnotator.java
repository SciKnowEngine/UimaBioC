package edu.isi.bmkeg.uimaBioC.rubicon.dev;

import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.bigmech.fries.FRIES_Argument;
import org.bigmech.fries.FRIES_EntityMention;
import org.bigmech.fries.FRIES_EventMention;
import org.bigmech.fries.FRIES_Frame;
import org.bigmech.fries.FRIES_FrameCollection;
import org.bigmech.fries.FRIES_Passage;
import org.bigmech.fries.FRIES_Sentence;
import org.bigmech.fries.FRIES_XRef;
import org.cleartk.ml.CleartkAnnotator;
import org.cleartk.ml.CleartkProcessingException;
import org.cleartk.ml.Feature;
import org.cleartk.ml.Instance;
import org.cleartk.ml.feature.extractor.CleartkExtractor;
import org.cleartk.ml.feature.extractor.CleartkExtractorException;
import org.cleartk.ml.feature.extractor.CoveredTextExtractor;
import org.cleartk.ml.feature.transform.extractor.TfidfExtractor;
import org.cleartk.token.type.Sentence;
import org.cleartk.token.type.Token;
import org.uimafit.descriptor.ConfigurationParameter;
import org.uimafit.factory.ConfigurationParameterFactory;
import org.uimafit.util.JCasUtil;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.typeadapters.RuntimeTypeAdapterFactory;

import bioc.type.UimaBioCAnnotation;
import bioc.type.UimaBioCDocument;
import bioc.type.UimaBioCPassage;
import edu.isi.bmkeg.uimaBioC.UimaBioCUtils;

public class ParagraphTfIdfAnnotator extends CleartkAnnotator<String> {

	CleartkExtractor<UimaBioCAnnotation, Token> countsExtractor;
	TfidfExtractor<String, UimaBioCAnnotation> tfidfExtractor;

	private static Logger logger = Logger.getLogger(ParagraphTfIdfAnnotator.class);

	public void initialize(UimaContext context) throws ResourceInitializationException {
		super.initialize(context);

		this.countsExtractor = new CleartkExtractor<UimaBioCAnnotation, Token>(Token.class,
				new CoveredTextExtractor<Token>(), new CleartkExtractor.Count(new CleartkExtractor.Covered()));

		this.tfidfExtractor = new TfidfExtractor<String, UimaBioCAnnotation>("tfidf", countsExtractor);

	}

	public void process(JCas jCas) throws AnalysisEngineProcessException {

		try {

			UimaBioCDocument uiD = JCasUtil.selectSingle(jCas, UimaBioCDocument.class);
			if (uiD.getId().equals("skip"))
				return;

			List<UimaBioCAnnotation> passages = UimaBioCUtils.readAllReadablePassagesAndFloats(jCas);
			
			if (this.isTraining()) {
				
				train(jCas, passages);
				
			} else {
				
				test(jCas, passages);
				
			}

		} catch (Exception e) {

			throw new AnalysisEngineProcessException(e);

		}

	}

	private void train(JCas jCas, List<UimaBioCAnnotation> passages) throws CleartkExtractorException, CleartkProcessingException {

		for (UimaBioCAnnotation a: passages) {

			Instance<String> instance = extractFeatures(jCas, a);
			this.dataWriter.write(instance);
			
		}

	}

	private Instance<String> extractFeatures(JCas jCas, UimaBioCAnnotation a)
			throws CleartkExtractorException, CleartkProcessingException {
		
		List<Feature> features = this.tfidfExtractor.extract(jCas, a);
		String outcome = a.getBegin() + "_" + a.getEnd();
		Instance<String> instance = new Instance<String>(outcome, features);
		
		return instance;
			
	}

	private void test(JCas jCas,  List<UimaBioCAnnotation> passages) throws CleartkExtractorException, CleartkProcessingException {
	
		for (UimaBioCAnnotation a: passages) {

			Instance<String> rawInstance = extractFeatures(jCas, a) ;
			Instance<String> tranformedInstance = this.tfidfExtractor.transform(rawInstance);
			
			// This is where we actually do something with the tfidf scores.
			int i = 0;
			i++;
			
		}
		
	}	
	
	public void collectionProcessComplete() throws AnalysisEngineProcessException {
		try {
			this.dataWriter.finish();
		} catch (Exception e) {
			throw new AnalysisEngineProcessException(e);
		}
	}

}
