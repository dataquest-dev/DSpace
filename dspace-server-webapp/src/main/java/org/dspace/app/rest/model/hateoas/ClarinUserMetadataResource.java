package org.dspace.app.rest.model.hateoas;

import org.dspace.app.rest.model.ClarinLicenseResourceUserAllowanceRest;
import org.dspace.app.rest.model.ClarinLicenseRest;
import org.dspace.app.rest.model.ClarinUserMetadataRest;
import org.dspace.app.rest.model.hateoas.annotations.RelNameDSpaceResource;
import org.dspace.app.rest.utils.Utils;
import org.dspace.content.clarin.ClarinUserMetadata;

@RelNameDSpaceResource(ClarinUserMetadataRest.NAME)
public class ClarinUserMetadataResource  extends DSpaceResource<ClarinUserMetadataRest> {
    public ClarinUserMetadataResource(ClarinUserMetadataRest data, Utils utils) {
        super(data, utils);
    }
}
