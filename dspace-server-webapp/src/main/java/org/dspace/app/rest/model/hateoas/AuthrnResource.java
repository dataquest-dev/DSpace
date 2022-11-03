package org.dspace.app.rest.model.hateoas;

import org.dspace.app.rest.model.AuthrnRest;
import org.dspace.app.rest.model.hateoas.annotations.RelNameDSpaceResource;
import org.dspace.app.rest.utils.Utils;

@RelNameDSpaceResource(AuthrnRest.NAME)
public class AuthrnResource extends DSpaceResource<AuthrnRest> {
    public AuthrnResource(AuthrnRest data, Utils utils) {
        super(data, utils);
    }
}
