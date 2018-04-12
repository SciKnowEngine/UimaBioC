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

import java.util.List;

import org.bigmech.fries.esViews.FRIES_EventMentionView.FRIES_EventMentionView__FRIES_EventMention;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

/**
 * @author Gully Burns
 */
public interface EventMentionRepository extends ElasticsearchRepository
		<FRIES_EventMentionView__FRIES_EventMention, String> {
	
	List<FRIES_EventMentionView__FRIES_EventMention> 
			findByType(String type);

	List<FRIES_EventMentionView__FRIES_EventMention> 
			findByFrameIdLike(String docId);
			
}
