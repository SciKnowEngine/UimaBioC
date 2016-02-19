/** $Id: OoevvExcelEngineTest.java 2628 2011-07-21 01:01:24Z tom $
 * 
 */
package edu.isi.bmkeg.uimaBioC.test;

import java.util.List;

import org.bigmech.fries.esViews.FRIES_EntityMentionView.FRIES_EntityMentionView__FRIES_EntityMention;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import edu.isi.bmkeg.uimaBioC.elasticSearch.UimaBioCAppConfiguration;
import edu.isi.bmkeg.uimaBioC.elasticSearch.EntityMentionRepository;

/**
 *
 * @author University of Southern California
 * @date $Date: 2011-07-20 18:01:24 -0700 (Wed, 20 Jul 2011) $
 * @version $Revision: 2628 $
 *
 */
@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = UimaBioCAppConfiguration.class)
public class FrameRepositoryTest {
	
	@Autowired
	ApplicationContext ctx;
		
	@Autowired
	EntityMentionRepository entityMentionRepo;
		
	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {	
	}
	
	@Test
	public void testQuery() throws Exception {

		List<FRIES_EntityMentionView__FRIES_EntityMention> geneMentionFrames = 
				entityMentionRepo.findByType("gene-or-gene-product");

		List<FRIES_EntityMentionView__FRIES_EntityMention> proteinMentionFrames = 
				entityMentionRepo.findByType("protein");

		int pause = 0;
		pause++;
		
	}
	
}