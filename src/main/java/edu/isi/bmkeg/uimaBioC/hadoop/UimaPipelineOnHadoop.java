/*******************************************************************************
 * Copyright 2013
 * Ubiquitous Knowledge Processing (UKP) Lab
 * Technische Universität Darmstadt
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package edu.isi.bmkeg.uimaBioC.hadoop;

import de.tudarmstadt.ukp.dkpro.bigdata.hadoop.DkproHadoopDriver;
import de.tudarmstadt.ukp.dkpro.bigdata.hadoop.DkproMapper;
import de.tudarmstadt.ukp.dkpro.bigdata.hadoop.DkproReducer;
import de.tudarmstadt.ukp.dkpro.core.io.text.TextReader;
import de.tudarmstadt.ukp.dkpro.core.io.text.TextWriter;
import de.tudarmstadt.ukp.dkpro.core.snowball.SnowballStemmer;
import de.tudarmstadt.ukp.dkpro.core.tokit.BreakIteratorSegmenter;
import edu.isi.bmkeg.uimaBioC.bin.UIMABIOC_01_Nxml2txt_to_BioC;
import edu.isi.bmkeg.uimaBioC.bin.UIMABIOC_01_Nxml2txt_to_BioC.Options;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.SequenceFileInputFormat;
import org.apache.hadoop.util.ToolRunner;
import org.apache.log4j.Logger;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.collection.CollectionReader;
import org.apache.uima.resource.ResourceInitializationException;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import static de.tudarmstadt.ukp.dkpro.core.api.io.ResourceCollectionReaderBase.INCLUDE_PREFIX;
import static org.apache.uima.fit.factory.AnalysisEngineFactory.createEngineDescription;
import static org.apache.uima.fit.factory.CollectionReaderFactory.createReader;

import java.io.File;

public class UimaPipelineOnHadoop extends DkproHadoopDriver {


	private static Logger logger = Logger.getLogger(UimaPipelineOnHadoop.class);

	public UimaPipelineOnHadoop(String sourceLocation, String targetLocation) {
		this.sourceLocation = sourceLocation;
		this.targetLocation = targetLocation;
	}

	private String sourceLocation = "";
	private String targetLocation = "";

	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

	@Override
	public CollectionReader buildCollectionReader() throws ResourceInitializationException {

		return createReader(TextReader.class, TextReader.PARAM_SOURCE_LOCATION, this.sourceLocation,
				TextReader.PARAM_PATTERNS, new String[] { INCLUDE_PREFIX + "*.txt" }, TextReader.PARAM_LANGUAGE, "en");

	}

	@Override
	public AnalysisEngineDescription buildMapperEngine(Configuration job) throws ResourceInitializationException {

		AnalysisEngineDescription tokenizer = createEngineDescription(BreakIteratorSegmenter.class);

		AnalysisEngineDescription stemmer = createEngineDescription(SnowballStemmer.class,
				SnowballStemmer.PARAM_LANGUAGE, "en");

		AnalysisEngineDescription writer = createEngineDescription(TextWriter.class, TextWriter.PARAM_TARGET_LOCATION,
				"target");

		return createEngineDescription(tokenizer, stemmer, writer);

	}

	@Override
	public AnalysisEngineDescription buildReducerEngine(Configuration job) throws ResourceInitializationException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Class getInputFormatClass() {
		return SequenceFileInputFormat.class;
	}

	@Override
	public void configure(JobConf job) {

	}

	public static void main(String[] args) throws Exception {

		UimaPipelineOnHadoop pipeline = new UimaPipelineOnHadoop(args[0], args[1]);
		pipeline.setMapperClass(DkproMapper.class);
		pipeline.setReducerClass(DkproReducer.class);
		ToolRunner.run(new Configuration(), pipeline, args);

	}

}
