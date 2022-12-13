package org.dspace.app.rest.model.hateoas;

import org.dspace.app.rest.model.ClarinLicenseRest;
import org.dspace.app.rest.model.ClarinVerificationTokenRest;
import org.dspace.app.rest.model.hateoas.annotations.RelNameDSpaceResource;
import org.dspace.app.rest.utils.Utils;

@RelNameDSpaceResource(ClarinVerificationTokenRest.NAME)
public class ClarinVerificationTokenResource extends DSpaceResource<ClarinVerificationTokenRest> {
    public ClarinVerificationTokenResource(ClarinVerificationTokenRest data, Utils utils) {
        super(data, utils);
    }
}
