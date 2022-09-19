package org.dspace.app.rest.converter;

import org.dspace.app.rest.model.ClarinLicenseLabelRest;
import org.dspace.app.rest.model.ClarinLicenseRest;
import org.dspace.app.rest.projection.Projection;
import org.dspace.content.clarin.ClarinLicense;
import org.dspace.content.clarin.ClarinLicenseLabel;
import org.dspace.content.service.clarin.ClarinLicenseResourceMappingService;
import org.dspace.xoai.services.api.context.ContextService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

@Component
public class ClarinLicenseConverter implements DSpaceConverter<ClarinLicense, ClarinLicenseRest> {
    @Autowired
    private ConverterService converter;

    @Inject
    private ContextService contextService;

    @Autowired
    private ClarinLicenseResourceMappingService clarinLicenseResourceMappingService;

    @Override
    public ClarinLicenseRest convert(ClarinLicense modelObject, Projection projection) {
        ClarinLicenseRest license = new ClarinLicenseRest();
        license.setProjection(projection);
        license.setId(modelObject.getId());
        license.setName(modelObject.getName());
        license.setConfirmation(modelObject.getConfirmation());
        license.setDefinition(modelObject.getDefinition());
        license.setRequiredInfo(modelObject.getRequiredInfo());
        setExtendedClarinLicenseLabels(license, modelObject.getLicenseLabels(), projection);
        setClarinLicenseLabel(license, modelObject.getLicenseLabels(), projection);
//        license.setExtendedClarinLicenseLabels(modelObject.getLicenseLabels(), projection);
//        license.setClarinLicenseLabel(modelObject.getLicenseLabels(), projection);
        // TODO find out which bitstreams are using this license
        try {
            license.setBitstreams(clarinLicenseResourceMappingService.findAllByLicenseId(contextService.getContext(), modelObject.getID()).size());
        } catch (Exception e) {
            throw new RuntimeException("Message");
        }

        return license;
    }

    @Override
    public Class<ClarinLicense> getModelClass() {
        return ClarinLicense.class;
    }

    /**
     * Add ExtendedClarinLicenseLabel list to the map
     */
    public void setExtendedClarinLicenseLabels(ClarinLicenseRest licenseRest, List<ClarinLicenseLabel> cLicenseLabels,
                                               Projection projection) {
        DSpaceConverter<ClarinLicenseLabel, ClarinLicenseLabelRest> clarinLicenseLabelConverter =
                converter.getConverter(ClarinLicenseLabel.class);

        List<ClarinLicenseLabelRest> clarinLicenseLabelRestList = new ArrayList<>();
        for (ClarinLicenseLabel clarinLicenseLabel : cLicenseLabels) {
            if (!clarinLicenseLabel.isExtended()) {
                continue;
            }
//            ClarinLicenseLabelRest clarinLicenseLabelRest = new ClarinLicenseLabelRest();
//            clarinLicenseLabelRest.setId(clarinLicenseLabel.getId());
//            clarinLicenseLabelRest.setLabel(clarinLicenseLabel.getLabel());
//            clarinLicenseLabelRest.setExtended(clarinLicenseLabel.isExtended());
//            clarinLicenseLabelRest.setTitle(clarinLicenseLabel.getTitle());
//            clarinLicenseLabelRest.setIcon(clarinLicenseLabel.getIcon());
            clarinLicenseLabelRestList.add(clarinLicenseLabelConverter.convert(clarinLicenseLabel, projection));
        }
        licenseRest.getExtendedLicenseLabelsMap()
                .put(ClarinLicenseLabelRest.EXTENDED_LABEL_NAME_PRETTY, clarinLicenseLabelRestList);
    }

    public void setClarinLicenseLabel(ClarinLicenseRest licenseRest, List<ClarinLicenseLabel> cLicenseLabels,
                                      Projection projection) {
        DSpaceConverter<ClarinLicenseLabel, ClarinLicenseLabelRest> clarinLicenseLabelConverter =
                converter.getConverter(ClarinLicenseLabel.class);
        for (ClarinLicenseLabel clarinLicenseLabel : cLicenseLabels) {
            if (clarinLicenseLabel.isExtended()) {
                continue;
            }

            licenseRest.setClarinLicenseLabel(clarinLicenseLabelConverter.convert(clarinLicenseLabel, projection));
            // there is only one non-extended license label
            return;
        }
    }
}
