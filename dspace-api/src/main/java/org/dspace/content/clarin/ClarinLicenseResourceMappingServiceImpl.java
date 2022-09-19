package org.dspace.content.clarin;

import org.apache.commons.lang.NullArgumentException;
import org.dspace.authorize.AuthorizeException;
import org.dspace.authorize.service.AuthorizeService;
import org.dspace.content.dao.clarin.ClarinLicenseResourceMappingDAO;
import org.dspace.content.service.clarin.ClarinLicenseResourceMappingService;
import org.dspace.core.Context;
import org.dspace.core.LogHelper;
import org.hibernate.ObjectNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class ClarinLicenseResourceMappingServiceImpl implements ClarinLicenseResourceMappingService {

    private static final Logger log = LoggerFactory.getLogger(ClarinLicenseServiceImpl.class);

    @Autowired
    ClarinLicenseResourceMappingDAO clarinLicenseResourceMappingDAO;

    @Autowired
    AuthorizeService authorizeService;

    @Override
    public ClarinLicenseResourceMapping create(Context context) throws SQLException, AuthorizeException {
        if (!authorizeService.isAdmin(context)) {
            throw new AuthorizeException(
                    "You must be an admin to create an Clarin License");
        }

        // Create a table row
        ClarinLicenseResourceMapping clarinLicenseResourceMapping = clarinLicenseResourceMappingDAO.create(context, new ClarinLicenseResourceMapping());

        log.info(LogHelper.getHeader(context, "create_clarin_license_resource_mapping", "clarin_license_resource_mapping_id="
                + clarinLicenseResourceMapping.getID()));

        return clarinLicenseResourceMapping;
    }

    @Override
    public ClarinLicenseResourceMapping create(Context context, ClarinLicenseResourceMapping clarinLicenseResourceMapping) throws SQLException, AuthorizeException {
        if (!authorizeService.isAdmin(context)) {
            throw new AuthorizeException(
                    "You must be an admin to create an Clarin License Resource Mapping");
        }
        return clarinLicenseResourceMappingDAO.create(context, clarinLicenseResourceMapping);
    }

    @Override
    public ClarinLicenseResourceMapping create(Context context, Integer licenseId, UUID bitstreamUuid) throws SQLException, AuthorizeException {
        if (!authorizeService.isAdmin(context)) {
            throw new AuthorizeException(
                    "You must be an admin to create an Clarin License Resource Mapping");
        }

        ClarinLicenseResourceMapping clarinLicenseResourceMapping = new ClarinLicenseResourceMapping();
        clarinLicenseResourceMapping.setLicenseId(licenseId);
        clarinLicenseResourceMapping.setBitstreamId(bitstreamUuid);

        return clarinLicenseResourceMappingDAO.create(context, clarinLicenseResourceMapping);
    }

    @Override
    public ClarinLicenseResourceMapping find(Context context, int valueId) throws SQLException {
        return clarinLicenseResourceMappingDAO.findByID(context, ClarinLicenseResourceMapping.class, valueId);
    }

    @Override
    public List<ClarinLicenseResourceMapping> findAllByLicenseId(Context context, Integer licenseId) throws SQLException {
        List<ClarinLicenseResourceMapping> mappings = clarinLicenseResourceMappingDAO.findAll(context, ClarinLicenseResourceMapping.class);
        List<ClarinLicenseResourceMapping> mappingsByLicenseId = new ArrayList<>();
        for (ClarinLicenseResourceMapping mapping: mappings) {
            if (mapping.getLicenseId() == licenseId) {
                mappingsByLicenseId.add(mapping);
            }
        }
        return mappingsByLicenseId;
    }

    @Override
    public void update(Context context, ClarinLicenseResourceMapping newClarinLicenseResourceMapping) throws SQLException {
        if (Objects.isNull(newClarinLicenseResourceMapping)) {
            throw new NullArgumentException("Cannot update clarin license resource mapping because the new clarin license resource mapping is null");
        }

        ClarinLicenseResourceMapping foundClarinLicenseResourceMapping = find(context, newClarinLicenseResourceMapping.getID());
        if (Objects.isNull(foundClarinLicenseResourceMapping)) {
            throw new ObjectNotFoundException(newClarinLicenseResourceMapping.getID(), "Cannot update the license resource mapping because" +
                    " the clarin license resource mapping wasn't found " +
                    "in the database.");
        }

        clarinLicenseResourceMappingDAO.save(context, newClarinLicenseResourceMapping);
    }

    @Override
    public void delete(Context context, ClarinLicenseResourceMapping clarinLicenseResourceMapping) throws SQLException {
        clarinLicenseResourceMappingDAO.delete(context, clarinLicenseResourceMapping);
    }
}
