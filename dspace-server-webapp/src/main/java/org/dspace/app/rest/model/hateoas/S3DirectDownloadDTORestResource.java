package org.dspace.app.rest.model.hateoas;

import org.dspace.app.rest.model.S3DirectDownloadDTORest;
import org.dspace.app.rest.model.hateoas.annotations.RelNameDSpaceResource;
import org.dspace.app.rest.utils.Utils;

@RelNameDSpaceResource(S3DirectDownloadDTORest.NAME)
public class S3DirectDownloadDTORestResource extends DSpaceResource<S3DirectDownloadDTORest> {

    public S3DirectDownloadDTORestResource(S3DirectDownloadDTORest data, Utils utils) {
        super(data, utils);
    }
}
