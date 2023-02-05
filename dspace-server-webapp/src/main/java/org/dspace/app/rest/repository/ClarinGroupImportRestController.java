package org.dspace.app.rest.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.Logger;
import org.dspace.app.rest.converter.ConverterService;
import org.dspace.app.rest.converter.MetadataConverter;
import org.dspace.app.rest.exception.UnprocessableEntityException;
import org.dspace.app.rest.model.BitstreamRest;
import org.dspace.app.rest.model.GroupRest;
import org.dspace.app.rest.utils.ContextUtil;
import org.dspace.app.rest.utils.Utils;
import org.dspace.authorize.AuthorizeException;
import org.dspace.core.Context;
import org.dspace.eperson.Group;
import org.dspace.eperson.service.GroupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

@RestController
@RequestMapping("/api/groups/import")
public class ClarinGroupImportRestController {
    private static final Logger log = org.apache.logging.log4j.LogManager
            .getLogger(ClarinGroupImportRestController.class);

    @Autowired
    private GroupService groupService;
    @Autowired
    MetadataConverter metadataConverter;
    @Autowired
    protected ConverterService converter;
    @Autowired
    protected Utils utils;

    @RequestMapping(method = RequestMethod.POST)
    @PreAuthorize("hasAuthority('ADMIN')")
    public GroupRest importGroup(HttpServletRequest request, HttpServletResponse response)
            throws SQLException, AuthorizeException {
        Context context = ContextUtil.obtainContext(request);
        ObjectMapper mapper = new ObjectMapper();
        GroupRest groupRest;

        try {
            groupRest = mapper.readValue(request.getInputStream(), GroupRest.class);
        } catch (IOException excIO) {
            throw new UnprocessableEntityException("error parsing the body ..." + excIO.getMessage());
        }

        Group group;
        try {
            group = groupService.create(context);
            groupService.setName(group, groupRest.getName());
            groupService.update(context, group);
            metadataConverter.setMetadata(context, group, groupRest.getMetadata());
        } catch (SQLException excSQL) {
            throw new RuntimeException(excSQL.getMessage(), excSQL);
        }

        context.commit();
        return converter.toRest(group, utils.obtainProjection());
    }
}
