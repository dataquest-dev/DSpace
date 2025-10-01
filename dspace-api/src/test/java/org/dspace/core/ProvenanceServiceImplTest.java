/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.core;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.UUID;

import org.dspace.AbstractUnitTest;
import org.dspace.authorize.AuthorizeException;
import org.dspace.authorize.ResourcePolicy;
import org.dspace.content.Bitstream;
import org.dspace.content.Item;
import org.dspace.content.MetadataSchemaEnum;
import org.dspace.content.service.BitstreamService;
import org.dspace.content.service.ItemService;
import org.dspace.content.service.clarin.ClarinItemService;
import org.dspace.content.service.clarin.ClarinLicenseResourceMappingService;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.Group;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * Test class for ProvenanceServiceImpl focusing on resource policy provenance
 *
 * @author Test Author
 */
@RunWith(MockitoJUnitRunner.class)
public class ProvenanceServiceImplTest extends AbstractUnitTest {

    @Mock
    private ItemService itemService;

    @Mock
    private ClarinItemService clarinItemService;

    @Mock
    private ClarinLicenseResourceMappingService clarinResourceMappingService;

    @Mock
    private BitstreamService bitstreamService;

    @InjectMocks
    private ProvenanceServiceImpl provenanceService;

    private Context context;
    private Item item;
    private Bitstream bitstream;
    private ResourcePolicy resourcePolicy;
    private EPerson eperson;
    private Group group;

    @Before
    public void setUp() throws Exception {
        context = new Context();
        
        // Create mock objects
        item = mock(Item.class);
        bitstream = mock(Bitstream.class);
        resourcePolicy = mock(ResourcePolicy.class);
        eperson = mock(EPerson.class);
        group = mock(Group.class);

        // Setup basic mock behavior
        when(item.getID()).thenReturn(UUID.randomUUID());
        when(bitstream.getID()).thenReturn(UUID.randomUUID());
        when(eperson.getEmail()).thenReturn("test@example.com");
        when(group.getName()).thenReturn("TestGroup");
    }

    @Test
    public void testCreateResourcePolicyForItem() throws SQLException, AuthorizeException {
        // Arrange
        when(resourcePolicy.getdSpaceObject()).thenReturn(item);
        when(resourcePolicy.getAction()).thenReturn(Constants.READ);
        when(resourcePolicy.getEPerson()).thenReturn(eperson);
        when(item.getType()).thenReturn(Constants.ITEM);

        // Act
        provenanceService.createResourcePolicy(context, resourcePolicy);

        // Assert
        verify(itemService, times(1)).addMetadata(
            eq(context), 
            eq(item), 
            eq(MetadataSchemaEnum.DC.getName()), 
            eq("description"), 
            eq("provenance"), 
            eq("en"), 
            any(String.class)
        );
        verify(itemService, times(1)).update(context, item);
    }

    @Test
    public void testCreateResourcePolicyForBitstream() throws SQLException, AuthorizeException {
        // Arrange
        when(resourcePolicy.getdSpaceObject()).thenReturn(bitstream);
        when(resourcePolicy.getAction()).thenReturn(Constants.READ);
        when(resourcePolicy.getGroup()).thenReturn(group);
        when(bitstream.getType()).thenReturn(Constants.BITSTREAM);
        when(clarinItemService.findByBitstreamUUID(context, bitstream.getID()))
            .thenReturn(Arrays.asList(item));

        // Act
        provenanceService.createResourcePolicy(context, resourcePolicy);

        // Assert
        verify(itemService, times(1)).addMetadata(
            eq(context), 
            eq(item), 
            eq(MetadataSchemaEnum.DC.getName()), 
            eq("description"), 
            eq("provenance"), 
            eq("en"), 
            any(String.class)
        );
        verify(itemService, times(1)).update(context, item);
    }

    @Test
    public void testUpdateResourcePolicyForItem() throws SQLException, AuthorizeException {
        // Arrange
        when(resourcePolicy.getdSpaceObject()).thenReturn(item);
        when(resourcePolicy.getAction()).thenReturn(Constants.WRITE);
        when(resourcePolicy.getEPerson()).thenReturn(eperson);
        when(item.getType()).thenReturn(Constants.ITEM);

        // Act
        provenanceService.updateResourcePolicy(context, resourcePolicy);

        // Assert
        verify(itemService, times(1)).addMetadata(
            eq(context), 
            eq(item), 
            eq(MetadataSchemaEnum.DC.getName()), 
            eq("description"), 
            eq("provenance"), 
            eq("en"), 
            any(String.class)
        );
        verify(itemService, times(1)).update(context, item);
    }

    @Test
    public void testDeleteResourcePolicyForItem() throws SQLException, AuthorizeException {
        // Arrange
        when(resourcePolicy.getdSpaceObject()).thenReturn(item);
        when(resourcePolicy.getAction()).thenReturn(Constants.DELETE);
        when(resourcePolicy.getGroup()).thenReturn(group);
        when(item.getType()).thenReturn(Constants.ITEM);

        // Act
        provenanceService.deleteResourcePolicy(context, resourcePolicy);

        // Assert
        verify(itemService, times(1)).addMetadata(
            eq(context), 
            eq(item), 
            eq(MetadataSchemaEnum.DC.getName()), 
            eq("description"), 
            eq("provenance"), 
            eq("en"), 
            any(String.class)
        );
        verify(itemService, times(1)).update(context, item);
    }

    @Test
    public void testResourcePolicyWithNullDSpaceObject() throws SQLException, AuthorizeException {
        // Arrange
        when(resourcePolicy.getdSpaceObject()).thenReturn(null);

        // Act
        provenanceService.createResourcePolicy(context, resourcePolicy);
        provenanceService.updateResourcePolicy(context, resourcePolicy);
        provenanceService.deleteResourcePolicy(context, resourcePolicy);

        // Assert - No provenance should be added when dSpaceObject is null
        verify(itemService, never()).addMetadata(any(Context.class), any(Item.class), any(String.class), any(String.class), any(String.class), any(String.class), any(String.class));
        verify(itemService, never()).update(any(), any());
    }

    @Test
    public void testBitstreamWithNoAssociatedItem() throws SQLException, AuthorizeException {
        // Arrange
        when(resourcePolicy.getdSpaceObject()).thenReturn(bitstream);
        when(bitstream.getType()).thenReturn(Constants.BITSTREAM);
        when(clarinItemService.findByBitstreamUUID(context, bitstream.getID()))
            .thenReturn(Arrays.asList()); // Empty list - no associated item

        // Act
        provenanceService.createResourcePolicy(context, resourcePolicy);

        // Assert - No provenance should be added when bitstream has no associated item
        verify(itemService, never()).addMetadata(any(Context.class), any(Item.class), any(String.class), any(String.class), any(String.class), any(String.class), any(String.class));
        verify(itemService, never()).update(any(), any());
    }

    @Test
    public void testResourcePolicyWithComplexDetails() throws SQLException, AuthorizeException {
        // Arrange
        when(resourcePolicy.getdSpaceObject()).thenReturn(item);
        when(resourcePolicy.getAction()).thenReturn(Constants.READ);
        when(resourcePolicy.getEPerson()).thenReturn(eperson);
        when(resourcePolicy.getGroup()).thenReturn(group);
        when(resourcePolicy.getRpDescription()).thenReturn("Test policy description");
        when(resourcePolicy.getStartDate()).thenReturn(new java.util.Date());
        when(resourcePolicy.getEndDate()).thenReturn(new java.util.Date());
        when(item.getType()).thenReturn(Constants.ITEM);

        // Act
        provenanceService.createResourcePolicy(context, resourcePolicy);

        // Assert
        verify(itemService, times(1)).addMetadata(
            eq(context), 
            eq(item), 
            eq(MetadataSchemaEnum.DC.getName()), 
            eq("description"), 
            eq("provenance"), 
            eq("en"), 
            any(String.class)
        );
        verify(itemService, times(1)).update(context, item);
    }

    @Test
    public void testHandlingSQLException() throws SQLException, AuthorizeException {
        // Arrange
        when(resourcePolicy.getdSpaceObject()).thenReturn(item);
        when(item.getType()).thenReturn(Constants.ITEM);
        when(itemService.addMetadata(any(Context.class), any(Item.class), any(String.class), any(String.class), any(String.class), any(String.class), any(String.class)))
            .thenThrow(new SQLException("Database error"));

        // Act - Should not throw exception, but log error
        provenanceService.createResourcePolicy(context, resourcePolicy);

        // Assert - Exception should be caught and logged
        verify(itemService, times(1)).addMetadata(any(Context.class), any(Item.class), any(String.class), any(String.class), any(String.class), any(String.class), any(String.class));
        verify(itemService, never()).update(any(), any()); // Should not reach update due to exception
    }

    @Test
    public void testHandlingAuthorizeException() throws SQLException, AuthorizeException {
        // Arrange
        when(resourcePolicy.getdSpaceObject()).thenReturn(item);
        when(item.getType()).thenReturn(Constants.ITEM);
        when(itemService.addMetadata(any(Context.class), any(Item.class), any(String.class), any(String.class), any(String.class), any(String.class), any(String.class)))
            .thenThrow(new AuthorizeException("Authorization error"));

        // Act - Should not throw exception, but log error
        provenanceService.updateResourcePolicy(context, resourcePolicy);

        // Assert - Exception should be caught and logged
        verify(itemService, times(1)).addMetadata(any(Context.class), any(Item.class), any(String.class), any(String.class), any(String.class), any(String.class), any(String.class));
        verify(itemService, never()).update(any(), any()); // Should not reach update due to exception
    }
}