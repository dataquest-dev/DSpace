package org.dspace.app.rest.model.hateoas;

import org.dspace.app.rest.model.MetadataValueWithFieldRest;
import org.dspace.app.rest.model.hateoas.annotations.RelNameDSpaceResource;
import org.dspace.app.rest.utils.Utils;

/**
 * MetadataValueWithField Rest HAL Resource. The HAL Resource wraps the REST Resource
 * adding support for the links and embedded resources
 *
 * @author Andrea Bollini (andrea.bollini at 4science.it)
 */
@RelNameDSpaceResource(MetadataValueWithFieldRest.NAME)
public class MetadataValueWithFieldResource extends DSpaceResource<MetadataValueWithFieldRest> {
    public MetadataValueWithFieldResource(MetadataValueWithFieldRest ms, Utils utils) {
        super(ms, utils);
    }
}

