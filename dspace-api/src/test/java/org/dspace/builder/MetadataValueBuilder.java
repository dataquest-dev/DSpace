/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.builder;

import java.io.IOException;
import java.sql.SQLException;

import org.apache.logging.log4j.Logger;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.DSpaceObject;
import org.dspace.content.MetadataField;
import org.dspace.content.MetadataValue;
import org.dspace.content.service.MetadataValueService;
import org.dspace.core.Context;
import org.dspace.discovery.SearchServiceException;

public class MetadataValueBuilder extends AbstractBuilder<MetadataValue, MetadataValueService> {

    /* Log4j logger*/
    private static final Logger log = org.apache.logging.log4j.LogManager.getLogger(MetadataValueBuilder.class);

    private MetadataValue metadataValue;

    protected MetadataValueBuilder(Context context) {
        super(context);
    }

    @Override
    public void cleanup() throws Exception {
        try (Context c = new Context()) {
            c.turnOffAuthorisationSystem();
            // Ensure object and any related objects are reloaded before checking to see what needs cleanup
            metadataValue = c.reloadEntity(metadataValue);
            if (metadataValue != null) {
                delete(c, metadataValue);
            }
            c.complete();
            indexingService.commit();
        }
    }

    @Override
    public MetadataValue build() throws SQLException, AuthorizeException {
        try {
            metadataValueService.update(context, metadataValue);
            context.dispatchEvents();

            indexingService.commit();
        } catch (SearchServiceException | SQLException e) {
            log.error("Failed to complete MetadataValue", e);
        }
        return metadataValue;
    }

    @Override
    public void delete(Context c, MetadataValue dso) throws Exception {
        if (dso != null) {
            getService().delete(c, dso);
        }
    }

    @Override
    protected MetadataValueService getService() {
        return metadataValueService;
    }

    public static MetadataValueBuilder createMetadataValue(Context context, DSpaceObject dso, String element,
                                                           String qualifier, String scopeNote)
            throws SQLException, AuthorizeException {
        MetadataField metadataField = MetadataFieldBuilder.createMetadataField(context, element, qualifier, scopeNote)
                .build();
        MetadataValueBuilder metadataValueBuilder = new MetadataValueBuilder(context);
        return metadataValueBuilder.create(context, dso, metadataField);
    }

    public static MetadataValueBuilder createMetadataValue(Context context, DSpaceObject dso,
                                                           MetadataField metadataField)
            throws SQLException, AuthorizeException {
        MetadataValueBuilder metadataValueBuilder = new MetadataValueBuilder(context);
        return metadataValueBuilder.create(context, dso, metadataField);
    }

    public void delete(MetadataValue dso) throws Exception {
        try (Context c = new Context()) {
            c.turnOffAuthorisationSystem();
            MetadataValue attachedDso = c.reloadEntity(dso);
            if (attachedDso != null) {
                getService().delete(c, attachedDso);
            }
            c.complete();
        }

        indexingService.commit();
    }

    /**
     * Delete the Test MetadataField referred to by the given ID
     * @param id Integer of Test MetadataField to delete
     * @throws SQLException
     * @throws IOException
     */
    public static void deleteMetadataValue(Integer id) throws SQLException, IOException {
        try (Context c = new Context()) {
            c.turnOffAuthorisationSystem();
            MetadataValue metadataValue = metadataValueService.find(c, id);
            if (metadataValue != null) {
                metadataValueService.delete(c, metadataValue);
            }
            c.complete();
        }
    }

    private MetadataValueBuilder create(Context context, DSpaceObject dso, MetadataField metadataField)
            throws SQLException {
        this.context = context;
        metadataValue = metadataValueService.create(context, dso, metadataField);
        return this;
    }
}
