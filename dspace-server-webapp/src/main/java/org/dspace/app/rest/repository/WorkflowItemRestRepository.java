/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.repository;

import static org.dspace.xmlworkflow.state.actions.processingaction.ProcessingAction.SUBMIT_EDIT_METADATA;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import javax.servlet.http.HttpServletRequest;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dspace.app.rest.Parameter;
import org.dspace.app.rest.SearchRestMethod;
import org.dspace.app.rest.exception.DSpaceBadRequestException;
import org.dspace.app.rest.exception.RESTAuthorizationException;
import org.dspace.app.rest.exception.UnprocessableEntityException;
import org.dspace.app.rest.model.ErrorRest;
import org.dspace.app.rest.model.WorkflowItemRest;
import org.dspace.app.rest.model.patch.Operation;
import org.dspace.app.rest.model.patch.Patch;
import org.dspace.app.rest.submit.SubmissionService;
import org.dspace.app.rest.utils.SolrOAIReindexer;
import org.dspace.app.util.SubmissionConfigReaderException;
import org.dspace.authorize.AuthorizeException;
import org.dspace.authorize.ResourcePolicy;
import org.dspace.authorize.service.AuthorizeService;
import org.dspace.authorize.service.ResourcePolicyService;
import org.dspace.content.Collection;
import org.dspace.content.Item;
import org.dspace.content.service.BitstreamFormatService;
import org.dspace.content.service.BitstreamService;
import org.dspace.content.service.CollectionService;
import org.dspace.content.service.ItemService;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.EPersonServiceImpl;
import org.dspace.services.ConfigurationService;
import org.dspace.submit.factory.SubmissionServiceFactory;
import org.dspace.submit.service.SubmissionConfigService;
import org.dspace.workflow.WorkflowException;
import org.dspace.workflow.WorkflowService;
import org.dspace.xmlworkflow.WorkflowConfigurationException;
import org.dspace.xmlworkflow.factory.XmlWorkflowFactory;
import org.dspace.xmlworkflow.state.Step;
import org.dspace.xmlworkflow.state.Workflow;
import org.dspace.xmlworkflow.state.actions.WorkflowActionConfig;
import org.dspace.xmlworkflow.storedcomponents.ClaimedTask;
import org.dspace.xmlworkflow.storedcomponents.XmlWorkflowItem;
import org.dspace.xmlworkflow.storedcomponents.service.ClaimedTaskService;
import org.dspace.xmlworkflow.storedcomponents.service.XmlWorkflowItemService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.rest.webmvc.ResourceNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * This is the repository responsible to manage WorkflowItem Rest object
 *
 * @author Andrea Bollini (andrea.bollini at 4science.it)
 */

@Component(WorkflowItemRest.CATEGORY + "." + WorkflowItemRest.NAME)
public class WorkflowItemRestRepository extends DSpaceRestRepository<WorkflowItemRest, Integer> {

    public static final String OPERATION_PATH_SECTIONS = "sections";

    private static final Logger log = LogManager.getLogger();

    @Autowired
    XmlWorkflowItemService wis;

    @Autowired
    ItemService itemService;

    @Autowired
    BitstreamService bitstreamService;

    @Autowired
    BitstreamFormatService bitstreamFormatService;

    @Autowired
    ConfigurationService configurationService;

    @Autowired
    SubmissionService submissionService;

    @Autowired
    EPersonServiceImpl epersonService;

    @Autowired
    WorkflowService<XmlWorkflowItem> wfs;

    @Autowired
    AuthorizeService authorizeService;

    @Autowired
    ClaimedTaskService claimedTaskService;

    @Autowired
    protected XmlWorkflowItemService xmlWorkflowItemService;

    @Autowired
    protected XmlWorkflowFactory workflowFactory;

    @Autowired
    private SolrOAIReindexer solrOAIReindexer;

    @Autowired
    private ResourcePolicyService resourcePolicyService;

    @Autowired
    private CollectionService collectionService;

    private SubmissionConfigService submissionConfigService;

    public WorkflowItemRestRepository() throws SubmissionConfigReaderException {
        submissionConfigService = SubmissionServiceFactory.getInstance().getSubmissionConfigService();
    }

    @Override
    @PreAuthorize("hasPermission(#id, 'WORKFLOWITEM', 'READ')")
    public WorkflowItemRest findOne(Context context, Integer id) {
        XmlWorkflowItem witem = null;
        try {
            witem = wis.find(context, id);
        } catch (SQLException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
        if (witem == null) {
            return null;
        }
        return converter.toRest(witem, utils.obtainProjection());
    }

    @Override
    @PreAuthorize("hasAuthority('ADMIN')")
    public Page<WorkflowItemRest> findAll(Context context, Pageable pageable) {
        try {
            long total = wis.countAll(context);
            List<XmlWorkflowItem> witems = wis.findAll(context, pageable.getPageNumber(), pageable.getPageSize());
            return converter.toRestPage(witems, pageable, total, utils.obtainProjection());
        } catch (SQLException e) {
            throw new RuntimeException("SQLException in " + this.getClass() + "#findAll trying to retrieve all " +
                "workflowitems from db.", e);
        }
    }

    @SearchRestMethod(name = "findBySubmitter")
    @PreAuthorize("hasAuthority('ADMIN')")
    public Page<WorkflowItemRest> findBySubmitter(@Parameter(value = "uuid") UUID submitterID, Pageable pageable) {
        try {
            Context context = obtainContext();
            EPerson ep = epersonService.find(context, submitterID);
            long total = wis.countBySubmitter(context, ep);
            List<XmlWorkflowItem> witems = wis.findBySubmitter(context, ep, pageable.getPageNumber(),
                    pageable.getPageSize());
            return converter.toRestPage(witems, pageable, total, utils.obtainProjection());
        } catch (SQLException e) {
            throw new RuntimeException("SQLException in " + this.getClass() + "#findBySubmitter trying to retrieve " +
                "eperson or their workflowitems from db.", e);
        }
    }

    @Override
    protected WorkflowItemRest createAndReturn(Context context, List<String> stringList) {
        XmlWorkflowItem source;
        if (stringList == null || stringList.isEmpty() || stringList.size() > 1) {
            throw new UnprocessableEntityException("The given URI list could not be properly parsed to one result");
        }
        try {
            source = submissionService.createWorkflowItem(context, stringList.get(0));
            if (isCollectionAllowedForSubmitterEditing(source.getItem().getOwningCollection())) {
                if (isValidSubmitter(context, source.getItem())) {
                    createResourcePolicy(context, source.getItem(), Constants.WRITE);
                }
            }
        } catch (AuthorizeException e) {
            throw new RESTAuthorizationException(e);
        } catch (WorkflowException e) {
            throw new UnprocessableEntityException(
                    "Invalid workflow action: " + e.getMessage(), e);
        } catch (SQLException e) {
            throw new RuntimeException("SQLException in " + this.getClass() + "#findBySubmitter trying to create " +
                "a workflow and adding it to db.", e);
        }
        solrOAIReindexer.reindexItem(source.getItem());
        //if the item go directly in published status we have to manage a status code 204 with no content
        if (source.getItem().isArchived()) {
            return null;
        }
        return converter.toRest(source, utils.obtainProjection());
    }

    /**
     * Checks if the provided collection is allowed for submitter metadata editing.
     *
     * This method retrieves a list of allowed collection names and IDs from the system configuration,
     * and checks if the given collection's name or ID matches any of the allowed values.
     *
     * @param collection The collection to be checked.
     * @return True if the collection's name or ID is in the allowed list for submitter editing, false otherwise.
     * @throws SQLException If there is an issue retrieving the configuration or querying the database.
     */
    private boolean isCollectionAllowedForSubmitterEditing(Collection collection) throws SQLException {
        if (Objects.isNull(collection)) {
            return false;
        }
        // Retrieve the allowed collections for submitter edition as an array
        String[] editableCollections = configurationService.getArrayProperty("lr.allow.edit.metadata");

        if (Objects.isNull(editableCollections) || editableCollections.length == 0) {
            return false;
        }

        Set<String> allowedNamesOrIds = new HashSet<>(Arrays.asList(editableCollections));

        // Check if the provided collection's name or ID is in the allowed set
        return allowedNamesOrIds.contains(collection.getName()) ||
                allowedNamesOrIds.contains(collection.getID().toString());
    }

    /**
     * Checks if the current user is a valid submitter for the given item.
     *
     * @param context The current DSpace context.
     * @param item The item for which the submitter validation is being checked.
     * @return True if the current user belongs to the valid submitter group
     *          for the item's owning collection, false otherwise.
     */
    private boolean isValidSubmitter(Context context, Item item) {
        if (Objects.isNull(item)) {
            return false;
        }
        return context.getCurrentUser().getGroups().stream()
                .anyMatch(g -> {
                    if (item.getOwningCollection() != null) {
                        String groupName = g.getName();
                        String expectedGroupName = "COLLECTION_" +
                                item.getOwningCollection().getID() + "_SUBMIT";
                        return groupName.equals(expectedGroupName);
                    }
                    return false;
                });
    }

    /**
     * Creates a resource policy for an item, granting the specified action to the current user.
     *
     * @param context The current DSpace context.
     * @param item The item for which the resource policy is being created.
     * @param action The action to be assigned to the resource policy (e.g., write, read).
     * @throws SQLException If there is an issue interacting with the database.
     * @throws AuthorizeException If the current user does not have sufficient authorization
     *                            to create the resource policy.
     */
    private void createResourcePolicy(Context context, Item item, int action) throws SQLException, AuthorizeException {
        context.turnOffAuthorisationSystem();
        ResourcePolicy resPol = resourcePolicyService.create(context);
        resPol.setAction(action);
        resPol.setdSpaceObject(item);
        resPol.setEPerson(context.getCurrentUser());
        context.restoreAuthSystemState();
    }

    @Override
    public Class<WorkflowItemRest> getDomainClass() {
        return WorkflowItemRest.class;
    }

    @Override
    public WorkflowItemRest upload(HttpServletRequest request, String apiCategory, String model, Integer id,
                                   MultipartFile file) throws SQLException {

        Context context = obtainContext();
        WorkflowItemRest wsi = findOne(context, id);
        XmlWorkflowItem source = wis.find(context, id);

        this.checkIfEditMetadataAllowedInCurrentStep(context, source);
        List<ErrorRest> errors = submissionService.uploadFileToInprogressSubmission(context, request, wsi, source,
                file);
        wsi = converter.toRest(source, utils.obtainProjection());

        if (!errors.isEmpty()) {
            wsi.getErrors().addAll(errors);
        }

        context.commit();
        return wsi;
    }

    @Override
    public void patch(Context context, HttpServletRequest request, String apiCategory, String model, Integer id,
                      Patch patch) throws SQLException, AuthorizeException {
        List<Operation> operations = patch.getOperations();
        WorkflowItemRest wsi = findOne(context, id);
        XmlWorkflowItem source = wis.find(context, id);

        this.checkIfEditMetadataAllowedInCurrentStep(context, source);

        for (Operation op : operations) {
            //the value in the position 0 is a null value
            String[] path = op.getPath().substring(1).split("/", 3);
            if (OPERATION_PATH_SECTIONS.equals(path[0])) {
                String section = path[1];
                submissionService.evaluatePatchToInprogressSubmission(context, request, source, wsi, section, op);
            } else {
                throw new DSpaceBadRequestException(
                    "Patch path operation need to starts with '" + OPERATION_PATH_SECTIONS + "'");
            }
        }
        wis.update(context, source);
    }

    @Override
    /**
     * This method provides support for the administrative abort workflow functionality. The abort functionality will
     * move the workflowitem back to the submitter workspace regardless to how the workflow is designed
     */
    protected void delete(Context context, Integer id) {
        XmlWorkflowItem witem = null;
        try {
            witem = wis.find(context, id);
            if (witem == null) {
                throw new ResourceNotFoundException("WorkflowItem ID " + id + " not found");
            }
            wfs.abort(context, witem, context.getCurrentUser());
        } catch (AuthorizeException e) {
            throw new RESTAuthorizationException(e);
        } catch (SQLException e) {
            throw new RuntimeException("SQLException in " + this.getClass() + "#delete trying to retrieve or delete a" +
                " workflowitem from db.", e);
        } catch (IOException e) {
            throw new RuntimeException("IOException in " + this.getClass() + "#delete trying to delete a workflowitem" +
                " from db (abort).", e);
        }
    }

    /**
     * Checks if @link{SUBMIT_EDIT_METADATA} is a valid option in the workflow step this task is currently at.
     * Patching and uploading is only allowed if this is the case.
     * @param context               Context
     * @param xmlWorkflowItem       WorkflowItem of the task
     */
    private void checkIfEditMetadataAllowedInCurrentStep(Context context, XmlWorkflowItem xmlWorkflowItem) {
        try {
            ClaimedTask claimedTask = claimedTaskService.findByWorkflowIdAndEPerson(context, xmlWorkflowItem,
                context.getCurrentUser());
            if (claimedTask == null) {
                throw new UnprocessableEntityException("WorkflowItem with id " + xmlWorkflowItem.getID()
                    + " has not been claimed yet.");
            }
            Workflow workflow = workflowFactory.getWorkflow(claimedTask.getWorkflowItem().getCollection());
            Step step = workflow.getStep(claimedTask.getStepID());
            WorkflowActionConfig currentActionConfig = step.getActionConfig(claimedTask.getActionID());
            if (!currentActionConfig.getProcessingAction().getOptions().contains(SUBMIT_EDIT_METADATA)) {
                throw new UnprocessableEntityException(SUBMIT_EDIT_METADATA + " is not a valid option on this " +
                    "action (" + currentActionConfig.getProcessingAction().getClass() + ").");
            }
        } catch (SQLException e) {
            throw new RuntimeException("SQLException in " + this.getClass()
                + "#checkIfEditMetadataAllowedInCurrentStep trying to retrieve workflowitem from db by eperson.", e);
        } catch (WorkflowConfigurationException e) {
            throw new RuntimeException("WorkflowConfigurationException in " + this.getClass()
                + "#checkIfEditMetadataAllowedInCurrentStep trying to retrieve workflow configuration from config", e);
        }
    }

    /**
     * This is a search method that will return the WorkflowItemRest object found through the UUID of an item. It'll
     * find the Item through the given UUID and try to resolve the WorkflowItem relevant for that item and return it.
     * It'll return a 401/403 if the current user isn't allowed to view the WorkflowItem.
     * It'll return a 204 if nothing was found
     * @param itemUuid  The UUID for the Item to be used
     * @param pageable  The pageable if present
     * @return          The resulting WorkflowItemRest object
     */
    @SearchRestMethod(name = "item")
    public WorkflowItemRest findByItemUuid(@Parameter(value = "uuid", required = true) UUID itemUuid,
                                           Pageable pageable) {
        try {
            Context context = obtainContext();
            Item item = itemService.find(context, itemUuid);
            XmlWorkflowItem xmlWorkflowItem = wis.findByItem(context, item);
            if (xmlWorkflowItem == null) {
                return null;
            }
            if (!authorizeService.authorizeActionBoolean(context, xmlWorkflowItem.getItem(), Constants.READ)) {
                throw new AccessDeniedException("The current user does not have rights to view the WorkflowItem");
            }
            return converter.toRest(xmlWorkflowItem, utils.obtainProjection());
        } catch (SQLException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

}
