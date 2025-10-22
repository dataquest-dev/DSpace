/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.authorize;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

import org.dspace.AbstractUnitTest;
import org.dspace.authorize.dao.ResourcePolicyDAO;
import org.dspace.authorize.service.AuthorizeService;
import org.dspace.content.Bitstream;
import org.dspace.content.Item;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.core.Context;
import org.dspace.core.ProvenanceService;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.Group;
import org.dspace.eperson.service.GroupService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * Test class for ResourcePolicyServiceImpl focusing on provenance integration
 *
 * @author Test Author
 */
@RunWith(MockitoJUnitRunner.class)
public class ResourcePolicyServiceImplTest extends AbstractUnitTest {

    @Mock
    private ContentServiceFactory contentServiceFactory;

    @Mock
    private ResourcePolicyDAO resourcePolicyDAO;

    @Mock
    private GroupService groupService;

    @Mock
    private AuthorizeService authorizeService;

    @Mock
    private ProvenanceService provenanceService;



    @InjectMocks
    private ResourcePolicyServiceImpl resourcePolicyServiceImpl;

    private Context context;
    private ResourcePolicy resourcePolicy;
    private EPerson eperson;
    private Group group;
    private Item item;
    private Bitstream bitstream;

    @Before
    public void setUp() throws Exception {
        context = new Context();

        // Create mock objects
        resourcePolicy = mock(ResourcePolicy.class);
        eperson = mock(EPerson.class);
        group = mock(Group.class);
        item = mock(Item.class);
        bitstream = mock(Bitstream.class);

        // Setup basic mock behavior
        when(item.getID()).thenReturn(UUID.randomUUID());
        when(bitstream.getID()).thenReturn(UUID.randomUUID());
        when(eperson.getEmail()).thenReturn("test@example.com");
        when(group.getName()).thenReturn("TestGroup");
    }

    @Test
    public void testCreateResourcePolicyWithItem() throws SQLException, AuthorizeException {
        // Arrange
        when(resourcePolicy.getEPerson()).thenReturn(eperson);
        when(resourcePolicy.getGroup()).thenReturn(null);
        when(resourcePolicy.getdSpaceObject()).thenReturn(item);
        when(resourcePolicyDAO.create(any(Context.class), any(ResourcePolicy.class))).thenReturn(resourcePolicy);

        // Act
        resourcePolicyServiceImpl.create(context, eperson, null);

        // Assert
        verify(provenanceService, times(1)).createResourcePolicy(context, resourcePolicy);
        verify(resourcePolicyDAO, times(1)).create(eq(context), any(ResourcePolicy.class));
    }

    @Test
    public void testCreateResourcePolicyWithBitstream() throws SQLException, AuthorizeException {
        // Arrange
        when(resourcePolicy.getEPerson()).thenReturn(null);
        when(resourcePolicy.getGroup()).thenReturn(group);
        when(resourcePolicy.getdSpaceObject()).thenReturn(bitstream);
        when(resourcePolicyDAO.create(any(Context.class), any(ResourcePolicy.class))).thenReturn(resourcePolicy);

        // Act
        resourcePolicyServiceImpl.create(context, null, group);

        // Assert
        verify(provenanceService, times(1)).createResourcePolicy(context, resourcePolicy);
        verify(resourcePolicyDAO, times(1)).create(eq(context), any(ResourcePolicy.class));
    }

    @Test
    public void testDeleteResourcePolicyWithProvenance() throws SQLException, AuthorizeException {
        // Arrange
        when(resourcePolicy.getdSpaceObject()).thenReturn(item);

        // Act
        resourcePolicyServiceImpl.delete(context, resourcePolicy);

        // Assert
        verify(provenanceService, times(1)).deleteResourcePolicy(context, resourcePolicy);
        verify(resourcePolicyDAO, times(1)).delete(context, resourcePolicy);
    }

    @Test
    public void testUpdateResourcePoliciesWithProvenance() throws SQLException, AuthorizeException {
        // Arrange
        ResourcePolicy policy1 = mock(ResourcePolicy.class);
        ResourcePolicy policy2 = mock(ResourcePolicy.class);
        when(policy1.getdSpaceObject()).thenReturn(item);
        when(policy2.getdSpaceObject()).thenReturn(bitstream);

        // Act
        resourcePolicyServiceImpl.update(context, Arrays.asList(policy1, policy2));

        // Assert
        verify(provenanceService, times(1)).updateResourcePolicy(context, policy1);
        verify(provenanceService, times(1)).updateResourcePolicy(context, policy2);
        verify(resourcePolicyDAO, times(1)).save(context, policy1);
        verify(resourcePolicyDAO, times(1)).save(context, policy2);
    }

    @Test
    public void testUpdateSingleResourcePolicyWithProvenance() throws SQLException, AuthorizeException {
        // Arrange
        when(resourcePolicy.getdSpaceObject()).thenReturn(item);

        // Act
        resourcePolicyServiceImpl.update(context, resourcePolicy);

        // Assert
        verify(provenanceService, times(1)).updateResourcePolicy(context, resourcePolicy);
        verify(resourcePolicyDAO, times(1)).save(context, resourcePolicy);
    }

    @Test
    public void testCreateResourcePolicyWithoutDSpaceObject() throws SQLException, AuthorizeException {
        // Arrange - ResourcePolicy without attached DSpace object
        when(resourcePolicy.getEPerson()).thenReturn(eperson);
        when(resourcePolicy.getGroup()).thenReturn(null);
        when(resourcePolicy.getdSpaceObject()).thenReturn(null); // No DSpace object
        when(resourcePolicyDAO.create(any(Context.class), any(ResourcePolicy.class))).thenReturn(resourcePolicy);

        // Act
        resourcePolicyServiceImpl.create(context, eperson, null);

        // Assert - Provenance should not be called for policies without DSpace objects
        verify(provenanceService, times(0)).createResourcePolicy(any(), any());
        verify(resourcePolicyDAO, times(1)).create(eq(context), any(ResourcePolicy.class));
    }

    @Test
    public void testUpdateEmptyResourcePoliciesList() throws SQLException, AuthorizeException {
        // Act
        resourcePolicyServiceImpl.update(context, Collections.emptyList());

        // Assert - No provenance should be added for empty list
        verify(provenanceService, times(0)).updateResourcePolicy(any(), any());
        verify(resourcePolicyDAO, times(0)).save(any(), any());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateResourcePolicyWithoutEPersonOrGroup() throws SQLException, AuthorizeException {
        // Act & Assert - Should throw IllegalArgumentException when both EPerson and Group are null
        resourcePolicyServiceImpl.create(context, null, null);
    }
}