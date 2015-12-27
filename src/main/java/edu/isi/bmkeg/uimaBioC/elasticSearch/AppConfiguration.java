/*
 * Copyright 2014-2015 the original author or authors.
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
 */
package edu.isi.bmkeg.uimaBioC.elasticSearch;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.URL;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.apache.log4j.Logger;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;

/**
 * @author Gully Burns
 */
@Configuration
@EnableElasticsearchRepositories("edu.isi.bmkeg.elasticNlm.repos")
@PropertySource("classpath:/application.properties")
@EnableAutoConfiguration
public class AppConfiguration {

	private static Logger logger = Logger.getLogger(AppConfiguration.class);
	
	
	@PreDestroy
	public void deleteIndex() {
		
	}

	@PostConstruct
	public void buildAllFilesAndIndices() throws Exception {
		
	}

	 void urlToTextFile(URL url, File f) throws IOException {
		PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(f, true)));
		BufferedReader in2 = new BufferedReader(new InputStreamReader(url.openStream()));
		String inputLine2;
		while ((inputLine2 = in2.readLine()) != null)
			out.println(inputLine2);
		in2.close();
		out.close();
	}

	String afterLastSlash(String s) {
		return s.substring(s.lastIndexOf("/") + 1, s.length());
	}

}
