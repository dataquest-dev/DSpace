package org.dspace.app.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hp.hpl.jena.rdf.model.Model;
import com.lyncode.xoai.dataprovider.OAIDataProvider;
import com.lyncode.xoai.dataprovider.OAIRequestParameters;
import com.lyncode.xoai.dataprovider.core.XOAIManager;
import com.lyncode.xoai.dataprovider.exceptions.InvalidContextException;
import com.lyncode.xoai.dataprovider.exceptions.OAIException;
import com.lyncode.xoai.dataprovider.exceptions.WritingXmlException;
import com.lyncode.xoai.dataprovider.xml.XmlOutputContext;
import com.lyncode.xoai.dataprovider.xml.oaipmh.MetadataType;
import com.lyncode.xoai.dataprovider.xml.oaipmh.OAIPMH;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.dspace.app.rest.converter.ConverterService;
import org.dspace.app.rest.model.ClarinFeaturedServiceRest;
import org.dspace.app.rest.utils.ContextUtil;
import org.dspace.app.rest.utils.Utils;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Item;
import org.dspace.content.MetadataValue;
import org.dspace.content.clarin.ClarinFeaturedService;
import org.dspace.content.clarin.ClarinFeaturedServiceLink;
import org.dspace.content.service.ItemService;
import org.dspace.core.Context;
import org.dspace.services.ConfigurationService;
import org.dspace.xoai.services.api.config.XOAIManagerResolver;
import org.dspace.xoai.services.api.config.XOAIManagerResolverException;
import org.dspace.xoai.services.api.context.ContextService;
import org.dspace.xoai.services.api.context.ContextServiceException;
import org.dspace.xoai.services.api.xoai.IdentifyResolver;
import org.dspace.xoai.services.api.xoai.ItemRepositoryResolver;
import org.dspace.xoai.services.api.xoai.SetRepositoryResolver;
import org.dspace.xoai.services.impl.xoai.DSpaceResumptionTokenFormatter;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.NotFoundException;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static java.util.Arrays.asList;
import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_OK;

@RestController
@RequestMapping("/api/core/refbox")
public class ClarinRefBoxController {
    private final Logger log = org.apache.logging.log4j.LogManager.getLogger(ClarinRefBoxController.class);

    @Autowired
    ConfigurationService configurationService;

    @Autowired
    ItemService itemService;

    @Autowired
    private ConverterService converterService;

    @Autowired
    protected Utils utils;

    @Autowired
    private ContextService contextService;

    @Autowired
    private XOAIManagerResolver xoaiManagerResolver;

    @Autowired
    private IdentifyResolver identifyResolver;

    @Autowired
    private SetRepositoryResolver setRepositoryResolver;

    @Autowired
    private ItemRepositoryResolver itemRepositoryResolver;

    private final DSpaceResumptionTokenFormatter resumptionTokenFormat = new DSpaceResumptionTokenFormatter();

    @RequestMapping(method = RequestMethod.GET, value = "/services")
    public Page<ClarinFeaturedServiceRest> getServices(@RequestParam(name = "id") UUID id,
                                                       HttpServletResponse response,
                                                       HttpServletRequest request, Pageable pageable)
            throws SQLException, AuthorizeException, IOException, IOException {
        // Get context
        Context context = ContextUtil.obtainCurrentRequestContext();
        if (Objects.isNull(context)) {
            throw new RuntimeException("Cannot obtain the context from the request.");
        }

        // Get item
        Item item = itemService.find(context, id);
        if (Objects.isNull(item)) {
            throw new NotFoundException("Cannot find the item with the uuid: " + id);
        }

        List<ClarinFeaturedService> featuredServiceList = new ArrayList<>();

        // Get services from configuration
        List<String> featuredServiceNames = Arrays.asList(configurationService.getArrayProperty("featured.services"));
        for (String featuredServiceName : featuredServiceNames) {
            // Get fullname, url and description of the featured service from the cfg
            String fullName = configurationService.getProperty("featured.service." + featuredServiceName + ".fullname");
            String url = configurationService.getProperty("featured.service." + featuredServiceName + ".url");
            String description = configurationService.getProperty("featured.service." + featuredServiceName + ".description");

            if (StringUtils.isBlank(url)) {
                throw new RuntimeException("The configuration property: `featured.service." + featuredServiceName +
                        ".url cannot be empty!");
            }

            // Check if the item has metadata for this featured service, if it doesn't have - do NOT return the
            // featured service.
            List<MetadataValue> itemMetadata = itemService.getMetadata(item, "local", "featuredService",
                    featuredServiceName, Item.ANY, false);
            if (CollectionUtils.isEmpty(itemMetadata)) {
                continue;
            }

            // Add the fullname, url, description, links to the REST object
            ClarinFeaturedService clarinFeaturedService = new ClarinFeaturedService();
            clarinFeaturedService.setName(fullName);
            clarinFeaturedService.setUrl(url);
            clarinFeaturedService.setDescription(description);
            clarinFeaturedService.setFeaturedServiceLinks(mapFeaturedServiceLinks(itemMetadata));

            featuredServiceList.add(clarinFeaturedService);
        }

        return converterService.toRestPage(featuredServiceList, pageable, utils.obtainProjection());
    }

    private List<ClarinFeaturedServiceLink> mapFeaturedServiceLinks(List<MetadataValue> itemMetadata) {
        List<ClarinFeaturedServiceLink> featuredServiceLinkList = new ArrayList<>();

        for (MetadataValue mv : itemMetadata) {
            if (Objects.isNull(mv)) {
                log.error("The metadata value object is null!");
                continue;
            }

            // The featured service key and value are stored like `<KEY>|<VALUE>`, must split it by `|`
            String metadataValue = mv.getValue();
            if (StringUtils.isBlank(metadataValue)) {
                log.error("The value of the metadata value object is null!");
                continue;
            }

            List<String> keyAndValue = List.of(metadataValue.split("\\|"));
            if (keyAndValue.size() < 2) {
                log.error("Cannot properly split the key and value from the metadata value!");
                continue;
            }

            // Create REST object with key and value
            ClarinFeaturedServiceLink clarinFeaturedServiceLink = new ClarinFeaturedServiceLink();
            clarinFeaturedServiceLink.setKey(keyAndValue.get(0));
            clarinFeaturedServiceLink.setValue(keyAndValue.get(1));

            featuredServiceLinkList.add(clarinFeaturedServiceLink);
        }

        return featuredServiceLinkList;
    }

    @RequestMapping(method = RequestMethod.GET, value = "/citations")
    public ResponseEntity getCitationText(@RequestParam(name = "type") String type,
                                          @RequestParam(name = "handle") String handle,
                                          Model model,
                                          HttpServletResponse response,
                                          HttpServletRequest request) throws IOException, ServletException {
        Context context = null;
        OAIPMH oaipmh = null;
        // ClarinOutputStream writs bytes into String.
        ClarinOutputStream output = new ClarinOutputStream();
        try {
            request.setCharacterEncoding("UTF-8");
            context = contextService.getContext();

            XOAIManager manager = xoaiManagerResolver.getManager();

            OAIDataProvider dataProvider = new OAIDataProvider(manager, "request",
                    identifyResolver.getIdentify(),
                    setRepositoryResolver.getSetRepository(),
                    itemRepositoryResolver.getItemRepository(),
                    resumptionTokenFormat);

            // adding some defaults for /cite requests this will make the URL simple
            // only handle and metadataPrefix will be required
            Map<String, List<String>> parameterMap = buildParametersMap(request);
            if (parameterMap.containsKey("type")) {
                parameterMap.remove("type");
            }
            if (!parameterMap.containsKey("verb")) {
                parameterMap.put("verb", asList("GetRecord"));
            }
            if (!parameterMap.containsKey("metadataPrefix")) {
                parameterMap.put("metadataPrefix", asList(type));
            } else {
                List<String> mp = parameterMap.get("metadataPrefix");
                List<String> lcMP = new ArrayList<String>();
                for (String m : mp) {
                    lcMP.add(m.toLowerCase());
                }
                parameterMap.put("metadataPrefix", lcMP);
            }
            if (!parameterMap.containsKey("identifier")) {
                parameterMap.put("identifier", asList("oai:" + request.getServerName() + ":" + handle));
                parameterMap.remove("handle");
            }

            OAIRequestParameters parameters = new OAIRequestParameters(parameterMap);

            response.setContentType("application/xml");

            oaipmh = dataProvider.handle(parameters);

            XmlOutputContext xmlOutContext = XmlOutputContext.emptyContext(output);
            xmlOutContext.getWriter().writeStartDocument();

            //Try to obtain just the metadata, if that fails return "normal" response
            try {
                oaipmh.getInfo().getGetRecord().getRecord().getMetadata().write(xmlOutContext);
            } catch (Exception e) {
                oaipmh.write(xmlOutContext);
            }

            xmlOutContext.getWriter().writeEndDocument();


            xmlOutContext.getWriter().flush();
            xmlOutContext.getWriter().close();

            output.close();
        } catch (InvalidContextException e) {
            return ResponseEntity.ok(indexAction(response, model));
        } catch (ContextServiceException | WritingXmlException | XMLStreamException e) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Unexpected error while writing the output. For more information visit the log files.");
        } catch (XOAIManagerResolverException e) {
            throw new ServletException("OAI 2.0 wasn't correctly initialized," +
                    " please check the log for previous errors", e);
        } catch (OAIException e) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Unexpected error. For more information visit the log files.");
        } catch (IOException ioException) {
            ioException.printStackTrace();
        } finally {
            closeContext(context);
        }

        if (Objects.isNull(oaipmh)) {
            return new ResponseEntity<String>("Cannot get oaipmh data", HttpStatus.valueOf(HttpServletResponse.SC_NO_CONTENT));
        }

        ResponseEntity<String> ent = new ResponseEntity<>(output.toString(), HttpStatus.valueOf(SC_OK));
        return ent;
    }

    private void closeContext(Context context) {
        if (Objects.nonNull(context) && context.isValid()) {
            context.abort();
        }
    }

    private String indexAction(HttpServletResponse response, Model model) {

        return "index";
    }

    private Map<String, List<String>> buildParametersMap(HttpServletRequest request) {
        Map<String, List<String>> map = new HashMap<String, List<String>>();
        Enumeration<String> names = request.getParameterNames();
        while (names.hasMoreElements()) {
            String name = (String) names.nextElement();
            String[] values = request.getParameterValues(name);
            map.put(name, asList(values));
        }
        return map;
    }

}

class ClarinOutputStream extends OutputStream {
    private StringBuilder string = new StringBuilder();

    @Override
    public void write(int b) throws IOException {
        this.string.append((char) b );
    }

    @Override
    public String toString() {
        return this.string.toString();
    }
};