package org.dspace.app.rest;

import org.atteo.evo.inflector.English;
import org.dspace.app.rest.converter.ConverterService;
import org.dspace.app.rest.model.DSpaceObjectRest;
import org.dspace.app.rest.model.TemplateItemRest;
import org.dspace.app.rest.model.WorkflowDefinitionRest;
import org.dspace.app.rest.model.WorkflowStepRest;
import org.dspace.app.rest.model.hateoas.TemplateItemResource;
import org.dspace.app.rest.projection.Projection;
import org.dspace.app.rest.repository.ItemTemplateItemOfLinkRepository;
import org.dspace.app.rest.repository.TemplateItemRestRepository;
import org.dspace.app.rest.utils.ContextUtil;
import org.dspace.app.rest.utils.Utils;
import org.dspace.content.DSpaceObject;
import org.dspace.content.service.ItemService;
import org.dspace.core.Context;
import org.dspace.identifier.IdentifierNotFoundException;
import org.dspace.identifier.IdentifierNotResolvableException;
import org.dspace.identifier.factory.IdentifierServiceFactory;
import org.dspace.identifier.service.IdentifierService;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.rest.webmvc.ResourceNotFoundException;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.TemplateVariable;
import org.springframework.hateoas.TemplateVariables;
import org.springframework.hateoas.UriTemplate;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.dspace.app.rest.IdentifierRestController.PARAM;
import static org.dspace.app.rest.utils.RegexUtils.REGEX_REQUESTMAPPING_IDENTIFIER_AS_UUID;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;

@RestController
@RequestMapping("/api/core/suggestions")
public class SuggestionRestController implements InitializingBean {

    @Autowired
    private Utils utils;

    @Autowired
    private ItemService itemService;

    @Autowired
    private TemplateItemRestRepository templateItemRestRepository;

    @Autowired
    private ConverterService converter;

    @Autowired
    private ItemTemplateItemOfLinkRepository itemTemplateItemOfLinkRepository;

    @Autowired
    private DiscoverableEndpointsService discoverableEndpointsService;

    @RequestMapping(method = RequestMethod.GET)
    public void getSuggestions(HttpServletRequest request,
                                        HttpServletResponse response) {

        DSpaceObject dso = null;
        Context context = ContextUtil.obtainContext(request);
        IdentifierService identifierService = IdentifierServiceFactory
                .getInstance().getIdentifierService();
        try {
            dso = identifierService.resolve(context, "123456");
            if (dso != null) {
                DSpaceObjectRest dsor = converter.toRest(dso, utils.obtainProjection());
                URI link = linkTo(dsor.getController(), dsor.getCategory(),
                        English.plural(dsor.getType()))
                        .slash(dsor.getId()).toUri();
                response.setStatus(HttpServletResponse.SC_FOUND);
                response.sendRedirect(link.toString());
            } else {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            }
        } catch (IdentifierNotFoundException e) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        } catch (IdentifierNotResolvableException e) {
            response.setStatus(HttpServletResponse.SC_NOT_IMPLEMENTED);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            context.abort();
        }
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        discoverableEndpointsService
                .register(this,
                        Arrays.asList(
                                new Link(
                                        new UriTemplate("/api/" + "suggestions"),
                                        "suggestions")));
    }
}