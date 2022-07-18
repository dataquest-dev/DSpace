/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */

package org.dspace.app.oai;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;

//import com.hp.hpl.jena.reasoner.rulesys.builtins.Print;
import com.lyncode.xoai.dataprovider.services.api.ResourceResolver;
import com.lyncode.xoai.dataprovider.services.impl.BaseDateProvider;
//import org.apache.solr.client.solrj.SolrClient;
//import org.apache.solr.client.solrj.SolrClient;
//import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.dspace.app.rest.test.AbstractControllerIntegrationTest;
import org.dspace.builder.CollectionBuilder;
import org.dspace.builder.CommunityBuilder;
import org.dspace.builder.ItemBuilder;
import org.dspace.content.Collection;
import org.dspace.content.Community;
//import org.dspace.content.Item;
//import org.dspace.discovery.MockSolrSearchCore;
//import org.dspace.discovery.MockSolrSearchCore;
//import org.dspace.discovery.SolrSearchCore;
import org.dspace.content.Item;
import org.dspace.discovery.MockSolrOaiCore;
import org.dspace.services.ConfigurationService;
//import org.dspace.solr.MockSolrServer;
//import org.dspace.solr.MockSolrServer;
import org.dspace.xoai.app.XOAI;
import org.dspace.xoai.services.api.EarliestDateResolver;
import org.dspace.xoai.services.api.cache.XOAICacheService;
import org.dspace.xoai.services.api.cache.XOAIItemCacheService;
import org.dspace.xoai.services.api.config.XOAIManagerResolver;
//import org.dspace.xoai.services.api.solr.SolrServerResolver;
import org.dspace.xoai.services.api.solr.SolrServerResolver;
import org.dspace.xoai.services.api.xoai.DSpaceFilterResolver;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.TestPropertySource;

/**
 * Integration test to verify the /oai endpoint is responding as a valid OAI-PMH endpoint.
 * This tests that our dspace-oai module is running at this endpoint.
 * <P>
 * This is an AbstractControllerIntegrationTest because dspace-oai makes use of Controllers.
 *
 * @author Tim Donohue
 */
// Ensure the OAI SERVER IS ENABLED before any tests run.
// This annotation overrides default DSpace config settings loaded into Spring Context
@TestPropertySource(properties = {"oai.enabled = true"})
//public class OAIOpenAIREIT extends AbstractWebClientIntegrationTest {
public class OAIOpenAIREIT extends AbstractControllerIntegrationTest {

    @Autowired
    private ConfigurationService configurationService;

    // All OAI-PMH paths that we test against
    private final String ROOT_PATH = "/oai";
    private final String DEFAULT_CONTEXT_PATH = "request";
    private final String DEFAULT_CONTEXT = ROOT_PATH + "/" + DEFAULT_CONTEXT_PATH;

    // Mock to ensure XOAI caching is disabled for all tests (see @Before method)
    @MockBean
    private XOAICacheService xoaiCacheService;

    @MockBean
    private XOAIItemCacheService xoaiItemCacheService;

    // Spy on the current EarliestDateResolver bean, to allow us to change behavior in tests below
    @SpyBean
    private EarliestDateResolver earliestDateResolver;

    // XOAI's BaseDateProvider (used for date-based testing below)
    private static final BaseDateProvider baseDateProvider = new BaseDateProvider();

    // Spy on the current XOAIManagerResolver bean, to allow us to change behavior of XOAIManager in tests
    // See also: createMockXOAIManager() method
    @SpyBean
    private XOAIManagerResolver xoaiManagerResolver;

    // Beans required by createMockXOAIManager()
    @Autowired
    private ResourceResolver resourceResolver;
    @Autowired
    private DSpaceFilterResolver filterResolver;

    @Autowired
    private MockSolrOaiCore mockSolrOaiCore;
//    private SolrOaiCore solrOaiCore;
//    private static SolrClient myCore;

    private static PrintStream ps;

    @Before
    public void onlyRunIfConfigExists() {
        // These integration tests REQUIRE that OAIWebConfig is found/available (as this class deploys OAI)
        // If this class is not available, the below "Assume" will cause all tests to be SKIPPED
        // NOTE: OAIWebConfig is provided by the 'dspace-oai' module
        try {
            Class.forName("org.dspace.app.configuration.OAIWebConfig");
        } catch (ClassNotFoundException ce) {
            Assume.assumeNoException(ce);
        }

        // Disable XOAI Caching for ALL tests
        when(xoaiCacheService.isActive()).thenReturn(false);
        when(xoaiCacheService.hasCache(anyString())).thenReturn(false);
    }

    @BeforeClass
    public static void makePrintStream() throws FileNotFoundException {
        File f = new File("C:\\Users\\MariánBerger\\Documents/out.txt");
        ps = new PrintStream(f);
//        myCore = new MockSolrServer("search").getSolrServer();
    }

    @AfterClass
    public static void tidyUp() {
        ps.close();
    }

    @Test
    public void testIfOpenAireIsSeen() throws Exception {
        //Turn off the authorization system, otherwise we can't make the objects
        context.turnOffAuthorisationSystem();
        // Create 3 Communities (1 as a subcommunity) & 2 Collections
        Community firstCommunity = CommunityBuilder.createCommunity(context)
                .withName("First Community")
                .build();
        Community secondCommunity = CommunityBuilder.createSubCommunity(context, firstCommunity)
                .withName("Second Community")
                .build();
        CommunityBuilder.createCommunity(context)
                .withName("Third Community")
                .build();
        Collection firstCollection = CollectionBuilder.createCollection(context, firstCommunity)
                .withName("First Collection")
                .build();
        CollectionBuilder.createCollection(context, secondCommunity)
                .withName("Second Collection")
                .build();
        Item one = ItemBuilder.createItem(context, firstCollection)
                .withTitle("Test item 1")
                .withMetadata("dc","relation","","info:eu-repo/grantAgreement/")
                .build();
        ItemBuilder.createItem(context, firstCollection)
                .withTitle("Test item 2")
                .withdrawn()
                .build();
        ItemBuilder.createItem(context, firstCollection)
                .withTitle("Test item 3")
                .withIssueDate("abcd")
                .build();
        ItemBuilder.createItem(context, firstCollection)
                .withTitle("Test item 4")
                .withAuthor("Ben Kenobi")
                .build();
        ps.println("visibility check before indexing");
//        XOAI indexer = new XOAI(context,true, true, true);
        XOAI indexer = new XOAI(context, false, new SolrServerResolver() {
//
            @Override
            public SolrClient getServer() throws SolrServerException {
                return mockSolrOaiCore.getSolr();
            }

        });
//        AnnotationConfigApplicationContext applicationContext = new AnnotationConfigApplicationContext(new Class[] {
//                BasicConfiguration.class
//        });
//        applicationContext.getAutowireCapableBeanFactory().autowireBean(indexer);
        ps.println("Item one is seen: " + indexer.checkVisible(one));

        int imported = indexer.index();
        if (imported > 0) {
            indexer.test_cleanCache(xoaiItemCacheService, xoaiCacheService);
        }
        context.restoreAuthSystemState();
        xoaiCacheService.deleteAll();
        xoaiItemCacheService.deleteAll();
//        indexer.index();
        ps.println("visibility check after indexing");
        ps.println("Item one is seen: " + indexer.checkVisible(one));
//        solrOaiCore.getSolr().commit(false,false);
//        context.restoreAuthSystemState();
//        solrOaiCore.getSolr().commit(false,false);
        String token = getAuthToken(admin.getEmail(), password);
        ps.println("rest items");
        getClient(token).perform(get("/api/core/items")).andExpect(
                (result) -> ps.println(result.getResponse().getContentAsString())
        );
        ps.println("list records");
        getClient().perform(get(DEFAULT_CONTEXT).param("verb", "ListRecords").param("metadataPrefix", "oai_dc"));
        ps.println("xoai");
        getClient().perform(get(DEFAULT_CONTEXT).param("verb", "ListRecords").param("metadataPrefix", "xoai"))
                .andExpect((result) -> ps.println(result.getResponse().getContentAsString()))
        ;
        ps.println("end");
        ps.close();
        ps.println("closed");

    }
}
