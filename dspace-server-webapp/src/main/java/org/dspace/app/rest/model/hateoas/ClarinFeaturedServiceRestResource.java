package org.dspace.app.rest.model.hateoas;

import org.dspace.app.rest.model.ClarinFeaturedServiceLinkRest;
import org.dspace.app.rest.model.ClarinFeaturedServiceRest;
import org.dspace.app.rest.model.GroupRest;
import org.dspace.app.rest.model.hateoas.annotations.RelNameDSpaceResource;
import org.dspace.app.rest.utils.Utils;

@RelNameDSpaceResource(ClarinFeaturedServiceRest.NAME)
public class ClarinFeaturedServiceRestResource extends DSpaceResource<ClarinFeaturedServiceRest> {
    public ClarinFeaturedServiceRestResource(ClarinFeaturedServiceRest data, Utils utils) {
        super(data, utils);
    }
}
