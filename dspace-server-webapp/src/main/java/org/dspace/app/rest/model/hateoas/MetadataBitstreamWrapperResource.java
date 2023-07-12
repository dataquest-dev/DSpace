package org.dspace.app.rest.model.hateoas;

import org.dspace.app.rest.model.MetadataBitstreamWrapperRest;
import org.dspace.app.rest.model.hateoas.annotations.RelNameDSpaceResource;
import org.dspace.app.rest.utils.Utils;

@RelNameDSpaceResource(MetadataBitstreamWrapperRest.NAME)
public class MetadataBitstreamWrapperResource extends DSpaceResource<MetadataBitstreamWrapperRest>{
    public MetadataBitstreamWrapperResource(MetadataBitstreamWrapperRest data, Utils utils) {
        super(data, utils);
    }
}
