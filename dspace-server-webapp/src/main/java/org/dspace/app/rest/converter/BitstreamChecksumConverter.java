package org.dspace.app.rest.converter;

import org.dspace.app.rest.authorization.Authorization;
import org.dspace.app.rest.model.AuthorizationRest;
import org.dspace.app.rest.model.BitstreamChecksum;
import org.dspace.app.rest.model.BitstreamChecksumRest;
import org.dspace.app.rest.projection.Projection;
import org.springframework.stereotype.Component;

@Component
public class BitstreamChecksumConverter implements DSpaceConverter<BitstreamChecksum, BitstreamChecksumRest> {
    @Override
    public BitstreamChecksumRest convert(BitstreamChecksum modelObject, Projection projection) {
        BitstreamChecksumRest bitstreamChecksumRest = new BitstreamChecksumRest();
        bitstreamChecksumRest.setActiveStore(modelObject.getActiveStore());
        bitstreamChecksumRest.setDatabaseChecksum(modelObject.getDatabaseChecksum());
        bitstreamChecksumRest.setSynchronizedStore(modelObject.getSynchronizedStore());
        return bitstreamChecksumRest;
    }

    @Override
    public Class<BitstreamChecksum> getModelClass() {
        return BitstreamChecksum.class;
    }
}
