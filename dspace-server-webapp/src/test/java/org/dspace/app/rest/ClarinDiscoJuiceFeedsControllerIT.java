/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest;

import static org.dspace.app.rest.repository.ClarinDiscoJuiceFeedsController.APPLICATION_JAVASCRIPT_UTF8;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.dspace.app.rest.test.AbstractControllerIntegrationTest;
import org.dspace.services.ConfigurationService;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Test class for the controller ClarinDiscoJuiceFeedsController
 *
 * @author Milan Majchrak (milan.majchrak at dataquest.sk)
 */
public class ClarinDiscoJuiceFeedsControllerIT extends AbstractControllerIntegrationTest {

    @Autowired
    ConfigurationService configurationService;

    @Test
    public void getDiscoFeeds() throws Exception {
        String authTokenAdmin = getAuthToken(eperson.getEmail(), password);

        // Expected response created from the test file: `discofeedResponse.json`
        String responseString = "dj_md_1([{\"country\":\"CZ\",\"keywords\":[\"Identity Provider for employees and " +
                "readers of the Archiepiscopal Gymnasium in Kromeriz - Library\",\"Identity Provider pro" +
                " zamÄ\\u203Astnance a ÄŤtenĂˇĹ™e knihovny ArcibiskupskĂ©ho gymnĂˇzia v KromÄ\\u203AĹ™Ă\u00ADĹľi\"," +
                "\"ArcibiskupskĂ© gymnĂˇzium v KromÄ\\u203AĹ™Ă\u00ADĹľi - Knihovna\"],\"entityID\":\"https:" +
                "\\/\\/agkm.cz\\/idp\\/shibboleth\",\"title\":\"Archiepiscopal Gymnasium in Kromeriz - Library\"}," +
                "{\"country\":\"CZ\",\"keywords\":[\"Identity Provider for staff of the Institute of Agricultural " +
                "Economics and Information and patrons of the AntonĂ\u00ADn Ĺ vehla Library\",\"Identity Provider" +
                " pro zamÄ\\u203Astnance ĂšZEI a ÄŤtenĂˇĹ™e Knihovny AntonĂ\u00ADna Ĺ vehly\",\"Ăšstav " +
                "zemÄ\\u203AdÄ\\u203AlskĂ© ekonomiky a informacĂ\u00AD\"],\"entityID\":\"https:\\/\\/aleph" +
                ".uzei.cz\\/idp\\/shibboleth\",\"title\":\"Institute of Agricultural Economics and Information\"}," +
                "{\"country\":\"CZ\",\"keywords\":[\"Identity Provider for patrons and staff of the Research Library" +
                " in Hradec KrĂˇlovĂ©\",\"Identity Provider pro ÄŤtenĂˇĹ™e a zamÄ\\u203Astance StudijnĂ\u00AD a" +
                " vÄ\\u203AdeckĂ© knihovny v Hradci KrĂˇlovĂ©\",\"StudijnĂ\u00AD a vÄ\\u203AdeckĂˇ knihovna v" +
                " Hradci KrĂˇlovĂ©\"],\"entityID\":\"https:\\/\\/aleph.svkhk.cz\\/idp\\/shibboleth\",\"title\":" +
                "\"The Research Library in Hradec KrĂˇlovĂ©\"}])";

        // Load bitstream from the item.
        getClient(authTokenAdmin).perform(get("/api/discojuice/feeds?callback=dj_md_1"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(APPLICATION_JAVASCRIPT_UTF8))
                .andExpect(content().string(responseString));
    }

    // 204 NO CONTENT
    @Test
    public void shouldReturnNoContent() throws Exception {
        String authTokenAdmin = getAuthToken(eperson.getEmail(), password);

        configurationService.setProperty("shibboleth.discofeed.url", "non existing endpoint");
        // Load bitstream from the item.
        getClient(authTokenAdmin).perform(get("/api/discojuice/feeds?callback=dj_md_1"))
                .andExpect(status().isNoContent());
    }
}
