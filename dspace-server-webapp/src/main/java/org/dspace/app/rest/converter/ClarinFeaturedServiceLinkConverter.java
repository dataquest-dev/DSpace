package org.dspace.app.rest.converter;

import org.dspace.app.rest.model.ClarinFeaturedServiceLinkRest;
import org.dspace.app.rest.model.ClarinFeaturedServiceRest;
import org.dspace.app.rest.projection.Projection;
import org.dspace.content.clarin.ClarinFeaturedService;
import org.dspace.content.clarin.ClarinFeaturedServiceLink;
import org.springframework.stereotype.Component;

@Component
public class ClarinFeaturedServiceLinkConverter implements DSpaceConverter<ClarinFeaturedServiceLink,
        ClarinFeaturedServiceLinkRest> {

    @Override
    public ClarinFeaturedServiceLinkRest convert(ClarinFeaturedServiceLink modelObject, Projection projection) {
        ClarinFeaturedServiceLinkRest clarinFeaturedServiceLinkRest = new ClarinFeaturedServiceLinkRest();
        clarinFeaturedServiceLinkRest.setProjection(projection);
        clarinFeaturedServiceLinkRest.setKey(modelObject.getKey());
        clarinFeaturedServiceLinkRest.setValue(modelObject.getValue());
        return clarinFeaturedServiceLinkRest;
    }

    @Override
    public Class<ClarinFeaturedServiceLink> getModelClass() {
        return ClarinFeaturedServiceLink.class;
    }
}
