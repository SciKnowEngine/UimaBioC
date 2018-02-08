package edu.isi.bmkeg.uimaBioC.uima.ae.core;

import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.FSArray;
import org.apache.uima.jcas.tcas.DocumentAnnotation;
import org.apache.uima.resource.ResourceInitializationException;
import org.cleartk.ml.feature.extractor.CleartkExtractor;
import org.cleartk.token.type.Sentence;
import org.cleartk.token.type.Token;
import org.simmetrics.StringMetric;
import org.simmetrics.StringMetricBuilder;
import org.simmetrics.metrics.CosineSimilarity;
import org.simmetrics.metrics.Levenshtein;
import org.simmetrics.simplifiers.CaseSimplifier;
import org.simmetrics.simplifiers.NonDiacriticSimplifier;
import org.simmetrics.tokenizers.QGramTokenizer;
import org.simmetrics.tokenizers.WhitespaceTokenizer;
import org.uimafit.component.JCasAnnotator_ImplBase;
import org.uimafit.descriptor.ConfigurationParameter;
import org.uimafit.factory.ConfigurationParameterFactory;
import org.uimafit.util.JCasUtil;

import com.google.gson.Gson;

import bioc.BioCDocument;
import bioc.io.BioCDocumentReader;
import bioc.io.BioCFactory;
import bioc.type.UimaBioCAnnotation;
import bioc.type.UimaBioCDocument;
import bioc.type.UimaBioCLocation;
import bioc.type.UimaBioCPassage;
import edu.isi.bmkeg.lapdf.xml.model.LapdftextXMLChunk;
import edu.isi.bmkeg.lapdf.xml.model.LapdftextXMLDocument;
import edu.isi.bmkeg.lapdf.xml.model.LapdftextXMLPage;
import edu.isi.bmkeg.lapdf.xml.model.LapdftextXMLWord;
import edu.isi.bmkeg.uimaBioC.UimaBioCUtils;
import edu.isi.bmkeg.uimaBioC.uima.readers.BioCCollectionReader;
import edu.isi.bmkeg.utils.xml.XmlBindingTools;

public class AddBioCDirectory extends
		JCasAnnotator_ImplBase {

	public static final String SECONDARY_BIOC_DIR = ConfigurationParameterFactory
			.createConfigurationParameterName(
					AddBioCDirectory.class, "biocDir");
	@ConfigurationParameter(mandatory = true, description = "BioC Directory")
	protected File biocDir;
	
	private static Logger logger = Logger
			.getLogger(AddBioCDirectory.class);

	public void initialize(UimaContext context)
			throws ResourceInitializationException {

		super.initialize(context);

	}

	public void process(JCas jCas) throws AnalysisEngineProcessException {

		try {
			
			UimaBioCDocument uiD = JCasUtil.selectSingle(jCas,
					UimaBioCDocument.class);
			if( uiD.getId().equals("skip") )
				return;
			
			UimaBioCPassage docP = UimaBioCUtils.readDocument(jCas);

			logger.info(uiD.getId());

			// Want to locate the 'body' section.
			int bodyBegin = -1;
			int bodyEnd = -1;
			List<UimaBioCAnnotation> annotations = JCasUtil.selectCovered(
					UimaBioCAnnotation.class, docP);
			for (UimaBioCAnnotation uiA : annotations) {
				Map<String, String> infons = UimaBioCUtils.convertInfons(uiA
						.getInfons());
				if (infons.containsKey("type")
						&& infons.get("type").equals("formatting") 
						&& infons.get("value").equals("body")) {
					bodyBegin = uiA.getBegin();
					bodyEnd = uiA.getEnd();
				}
			}

			if (bodyBegin == -1) {
				logger.error("Can't find body section of paper.");
				return;
			}

			File bioCFile = new File(this.biocDir.getPath() + "/"
					+ uiD.getId() + ".xml");
			BioCDocument bioD = UimaBioCUtils.readBioCFile(bioCFile);	
			
			UimaBioCUtils.addBioCDocumentToExistingUimaBioCDocument(bioD, jCas);

		} catch (Exception e) {

			throw new AnalysisEngineProcessException(e);

		}

	}


}
