package org.dspace.app.rest.model.hateoas;

import org.dspace.app.rest.model.ClarinFeaturedServiceLinkRest;
import org.dspace.app.rest.model.ClarinLicenseRest;
import org.dspace.app.rest.model.GroupRest;
import org.dspace.app.rest.model.hateoas.annotations.RelNameDSpaceResource;
import org.dspace.app.rest.utils.Utils;

@RelNameDSpaceResource(ClarinFeaturedServiceLinkRest.NAME)
public class ClarinFeaturedServiceRestLinkResource extends DSpaceResource<ClarinFeaturedServiceLinkRest> {
    public ClarinFeaturedServiceRestLinkResource(ClarinFeaturedServiceLinkRest data, Utils utils) {
        super(data, utils);
    }
}
