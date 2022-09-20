package org.dspace.builder;

import org.dspace.authorize.AuthorizeException;
import org.dspace.content.clarin.ClarinLicenseResourceMapping;
import org.dspace.content.factory.ClarinServiceFactory;
import org.dspace.content.service.clarin.ClarinLicenseResourceMappingService;
import org.dspace.core.Context;

import java.sql.SQLException;

public class ClarinLicenseResourceMappingBuilder extends AbstractBuilder<ClarinLicenseResourceMapping, ClarinLicenseResourceMappingService> {

    private ClarinLicenseResourceMappingService clarinLicenseResourceMappingService = ClarinServiceFactory.getInstance().getClarinResourceMappingService();

    private ClarinLicenseResourceMapping clarinLicenseResourceMapping;

    protected ClarinLicenseResourceMappingBuilder(Context context) {
        super(context);
    }

    public static ClarinLicenseResourceMappingBuilder createClarinLicenseResourceMapping(final Context context) {
        ClarinLicenseResourceMappingBuilder builder = new ClarinLicenseResourceMappingBuilder(context);
        return builder.create(context);
    }

    private ClarinLicenseResourceMappingBuilder create(final Context context) {
        this.context = context;
        try {
            clarinLicenseResourceMapping = clarinLicenseResourceMappingService.create(context);
        } catch (Exception e) {
            return handleException(e);
        }
        return this;
    }

    @Override
    public void cleanup() throws Exception {
        try (Context c = new Context()) {
            c.turnOffAuthorisationSystem();
            // Ensure object and any related objects are reloaded before checking to see what needs cleanup
            clarinLicenseResourceMapping = c.reloadEntity(clarinLicenseResourceMapping);
            delete(c, clarinLicenseResourceMapping);
            c.complete();
            indexingService.commit();
        }
    }

    @Override
    public ClarinLicenseResourceMapping build() throws SQLException, AuthorizeException {
        try {
            context.dispatchEvents();
            indexingService.commit();
            return clarinLicenseResourceMapping;
        } catch (Exception e) {
            return handleException(e);
        }
    }

    @Override
    public void delete(Context c, ClarinLicenseResourceMapping dso) throws Exception {
        if (dso != null) {
            getService().delete(c, dso);
        }
    }

    @Override
    protected ClarinLicenseResourceMappingService getService() {
        return clarinLicenseResourceMappingService;
    }
}
