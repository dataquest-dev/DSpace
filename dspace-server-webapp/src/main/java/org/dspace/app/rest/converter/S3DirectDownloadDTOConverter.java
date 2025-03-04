package org.dspace.app.rest.converter;

import org.dspace.app.rest.model.S3DirectDownloadDTO;
import org.dspace.app.rest.model.S3DirectDownloadDTORest;
import org.dspace.app.rest.projection.Projection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
public class S3DirectDownloadDTOConverter implements DSpaceConverter<S3DirectDownloadDTO,
        S3DirectDownloadDTORest> {

    // Must be loaded @Lazy, as ConverterService autowires all DSpaceConverter components
    @Lazy
    @Autowired
    private ConverterService converter;

    @Override
    public S3DirectDownloadDTORest convert(S3DirectDownloadDTO modelObject, Projection projection) {
        S3DirectDownloadDTORest s3DirectDownloadDTORest = new S3DirectDownloadDTORest();
        s3DirectDownloadDTORest.setBitstreamName(modelObject.getBitstreamName());
        s3DirectDownloadDTORest.setUrl(modelObject.getUrl());
        s3DirectDownloadDTORest.setProjection(projection);
        return s3DirectDownloadDTORest;
    }

    @Override
    public Class<S3DirectDownloadDTO> getModelClass() {
        return S3DirectDownloadDTO.class;
    }
}
