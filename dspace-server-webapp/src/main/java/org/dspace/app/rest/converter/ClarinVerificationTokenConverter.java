package org.dspace.app.rest.converter;

import org.dspace.app.rest.model.ClarinLicenseRest;
import org.dspace.app.rest.model.ClarinVerificationTokenRest;
import org.dspace.app.rest.projection.Projection;
import org.dspace.content.clarin.ClarinLicense;
import org.dspace.content.clarin.ClarinVerificationToken;
import org.springframework.stereotype.Component;

@Component
public class ClarinVerificationTokenConverter implements DSpaceConverter<ClarinVerificationToken,
        ClarinVerificationTokenRest> {

    @Override
    public ClarinVerificationTokenRest convert(ClarinVerificationToken modelObject, Projection projection) {
        ClarinVerificationTokenRest clarinVerificationTokenRest = new ClarinVerificationTokenRest();
        clarinVerificationTokenRest.setId(modelObject.getID());
        clarinVerificationTokenRest.setToken(modelObject.getToken());
        clarinVerificationTokenRest.setShibHeaders(modelObject.getShibHeaders());
        clarinVerificationTokenRest.setEmail(modelObject.getEmail());
        clarinVerificationTokenRest.setePersonNetID(modelObject.getePersonNetID());
        clarinVerificationTokenRest.setProjection(projection);
        return clarinVerificationTokenRest;
    }

    @Override
    public Class<ClarinVerificationToken> getModelClass() {
        return ClarinVerificationToken.class;
    }
}
