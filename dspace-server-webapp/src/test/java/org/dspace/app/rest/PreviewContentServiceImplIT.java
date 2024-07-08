package org.dspace.app.rest;

import org.apache.commons.codec.CharEncoding;
import org.apache.commons.io.IOUtils;
import org.dspace.app.rest.test.AbstractControllerIntegrationTest;
import org.dspace.authorize.AuthorizeException;
import org.dspace.builder.*;
import org.dspace.content.*;
import org.dspace.content.PreviewContent;
import org.dspace.content.service.PreviewContentService;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;

public class PreviewContentServiceImplIT extends AbstractControllerIntegrationTest {

    @Autowired
    PreviewContentService previewContentService;
    PreviewContent previewContent;

    @Before
    public void setup() throws SQLException, AuthorizeException, IOException {
        context.turnOffAuthorisationSystem();
        // create bitstream
        Community comm = CommunityBuilder.createCommunity(context)
                .withName("Community Test")
                .build();
        Collection col = CollectionBuilder.createCollection(context, comm).withName("Collection Test").build();
        Item publicItem = ItemBuilder.createItem(context, col)
                .withTitle("Test")
                .withIssueDate("2010-10-17")
                .withAuthor("Smith, Donald")
                .withSubject("ExtraEntry")
                .build();
        Bundle bundle1 = BundleBuilder.createBundle(context, publicItem)
                .withName("Bundle Test")
                .build();
        String bitstreamContent = "ThisIsSomeDummyText";
        Bitstream bitstream = null;
        try (InputStream is = IOUtils.toInputStream(bitstreamContent, CharEncoding.UTF_8)) {
            bitstream = BitstreamBuilder.
                    createBitstream(context, bundle1, is)
                    .withName("Bitstream Test")
                    .withDescription("description")
                    .withMimeType("text/plain")
                    .build();
        }
        // create content preview
        previewContent = PreviewContentBuilder.createPreviewContent(context, bitstream).build();
    }

    @Test
    public void testFind() throws Exception {
       // Find created handle
        Assert.assertEquals(previewContent, previewContentService
                .find(context, previewContent.getID()));
    }
}
