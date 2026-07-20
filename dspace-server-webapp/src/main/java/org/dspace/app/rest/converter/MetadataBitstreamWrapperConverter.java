/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.converter;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dspace.access.status.DefaultAccessStatusHelper;
import org.dspace.access.status.service.AccessStatusService;
import org.dspace.app.rest.model.MetadataBitstreamWrapperRest;
import org.dspace.app.rest.model.wrapper.MetadataBitstreamWrapper;
import org.dspace.app.rest.projection.Projection;
import org.dspace.app.rest.utils.ContextUtil;
import org.dspace.core.Context;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * This is the converter from/to the MetadataBitstreamWrapper in the DSpace API data model and the
 * REST data model
 *
 * @author longtv
 */
@Component
public class MetadataBitstreamWrapperConverter implements DSpaceConverter<MetadataBitstreamWrapper,
        MetadataBitstreamWrapperRest> {

    private static final Logger log = LogManager.getLogger(MetadataBitstreamWrapperConverter.class);

    @Lazy
    @Autowired
    private ConverterService converter;


    @Autowired
    private BitstreamConverter bitstreamConverter;

    @Autowired
    private AccessStatusService accessStatusService;

    @Override
    public MetadataBitstreamWrapperRest convert(MetadataBitstreamWrapper modelObject, Projection projection) {
        MetadataBitstreamWrapperRest bitstreamWrapperRest = new MetadataBitstreamWrapperRest();
        bitstreamWrapperRest.setProjection(projection);
        bitstreamWrapperRest.setName(modelObject.getBitstream().getName());
        bitstreamWrapperRest.setId(modelObject.getBitstream().getID().toString());
        bitstreamWrapperRest.setDescription(modelObject.getDescription());
        bitstreamWrapperRest.setChecksum(modelObject.getBitstream().getChecksum());
        bitstreamWrapperRest.setFileSize(modelObject.getBitstream().getSizeBytes());
        bitstreamWrapperRest.setFileInfo(modelObject.getFileInfo());
        bitstreamWrapperRest.setHref(modelObject.getHref());
        bitstreamWrapperRest.setFormat(modelObject.getFormat());
        bitstreamWrapperRest.setCanPreview(modelObject.isCanPreview());
        setAccessStatus(bitstreamWrapperRest, modelObject);
        return bitstreamWrapperRest;
    }

    /**
     * Populate the access status/embargo date for this bitstream. Failures here must never break
     * the surrounding file listing - same fail-closed intent as the standalone
     * BitstreamAccessStatusLinkRepository, just inline since this endpoint returns a flat DTO
     * rather than a HAL link.
     */
    private void setAccessStatus(MetadataBitstreamWrapperRest bitstreamWrapperRest,
                                  MetadataBitstreamWrapper modelObject) {
        try {
            Context context = ContextUtil.obtainCurrentRequestContext();
            String status = accessStatusService.getAccessStatus(context, modelObject.getBitstream());
            bitstreamWrapperRest.setStatus(status);
            if (DefaultAccessStatusHelper.EMBARGO.equals(status)) {
                bitstreamWrapperRest.setEmbargoDate(
                    accessStatusService.getEmbargoFromBitstream(context, modelObject.getBitstream()));
            }
        } catch (Exception e) {
            log.warn("Unable to compute access status for bitstream {}",
                modelObject.getBitstream().getID(), e);
        }
    }

    @Override
    public Class<MetadataBitstreamWrapper> getModelClass() {
        return MetadataBitstreamWrapper.class;
    }
}
