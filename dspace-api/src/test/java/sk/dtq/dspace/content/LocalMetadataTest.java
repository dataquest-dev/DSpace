package sk.dtq.dspace.content;

import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThat;

import org.dspace.AbstractUnitTest;
import org.dspace.content.MetadataField;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.MetadataFieldService;
import org.junit.Test;

/**
 * Unit Tests for class MetadataFieldTest
 *
 * @author milanmajchrak
 */
public class LocalMetadataTest extends AbstractUnitTest {

    private MetadataFieldService metadataFieldService = ContentServiceFactory.getInstance().getMetadataFieldService();

    /**
     * Test of existing custom metadata field `local.contact.person`
     */
    @Test
    public void existContactPerson() throws Exception {
        MetadataField field = metadataFieldService.findByString(context, "local.contact.person",
                '.');

        assertThat("existContactPerson 0", field, notNullValue());
    }

    /**
     * Test of existing custom metadata field `local.sponsor.null`
     */
    @Test
    public void existSponsor() throws Exception {
        MetadataField field = metadataFieldService.findByString(context, "local.sponsor",
                '.');

        assertThat("existSponsor 0", field, notNullValue());
    }

}
