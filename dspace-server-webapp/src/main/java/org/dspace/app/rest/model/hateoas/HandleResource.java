package org.dspace.app.rest.model.hateoas;

import org.dspace.app.rest.model.HandleRest;
import org.dspace.app.rest.model.hateoas.annotations.RelNameDSpaceResource;
import org.dspace.app.rest.utils.Utils;


@RelNameDSpaceResource(HandleRest.NAME)
public class HandleResource extends DSpaceResource<HandleRest> {
    public HandleResource(HandleRest ms, Utils utils) {
        super(ms, utils);
    }
}
