package org.dspace.app.rest.model.hateoas;

import org.dspace.app.rest.model.AuthrnRest;
import org.dspace.app.rest.model.BitstreamChecksumRest;
import org.dspace.app.rest.model.BitstreamFormatRest;
import org.dspace.app.rest.model.hateoas.annotations.RelNameDSpaceResource;
import org.dspace.app.rest.utils.Utils;

@RelNameDSpaceResource(BitstreamChecksumRest.NAME)
public class BitstreamChecksumResource extends DSpaceResource<BitstreamChecksumRest> {

    public BitstreamChecksumResource(BitstreamChecksumRest data, Utils utils) {
        super(data, utils);
    }
}
