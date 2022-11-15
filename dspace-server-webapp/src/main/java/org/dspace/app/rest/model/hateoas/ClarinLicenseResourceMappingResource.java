package org.dspace.app.rest.model.hateoas;

import org.dspace.app.rest.model.ClarinLicenseResourceMappingRest;
import org.dspace.app.rest.model.ClarinLicenseResourceUserAllowanceRest;
import org.dspace.app.rest.model.hateoas.annotations.RelNameDSpaceResource;
import org.dspace.app.rest.utils.Utils;

@RelNameDSpaceResource(ClarinLicenseResourceMappingRest.NAME)
public class ClarinLicenseResourceMappingResource extends DSpaceResource<ClarinLicenseResourceMappingRest> {
    public ClarinLicenseResourceMappingResource(ClarinLicenseResourceMappingRest data, Utils utils) {
        super(data, utils);
    }
}
