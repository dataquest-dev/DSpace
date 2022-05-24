package org.dspace.app.rest.model.hateoas;

import org.dspace.app.rest.model.MetadataValueWrapperRest;
import org.dspace.app.rest.model.hateoas.annotations.RelNameDSpaceResource;
import org.dspace.app.rest.utils.Utils;

/**
 * MetadataField Rest HAL Resource. The HAL Resource wraps the REST Resource
 * adding support for the links and embedded resources
 *
 * @author Andrea Bollini (andrea.bollini at 4science.it)
 */
@RelNameDSpaceResource(MetadataValueWrapperRest.NAME)
public class MetadataValueWrapperResource extends DSpaceResource<MetadataValueWrapperRest> {
    public MetadataValueWrapperResource(MetadataValueWrapperRest ms, Utils utils) {
        super(ms, utils);
    }
}
