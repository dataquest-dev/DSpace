/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */


package org.dspace.app.rest;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;



@RestController
@RequestMapping("/cmdi")
public class CMDIRestController {

    @RequestMapping("/superjozef")
    public String superJozef() {
        return "{ Jozef je super }";
    }

    @RequestMapping("/test")
    public String test() {
        return "nečistý test ";
    }

    @RequestMapping("/metadata/{id}")
    public String metadata(@PathVariable String id) {
        return "Requesting metadata for id " + id;
    }

    @RequestMapping("/testID/{id}")
    public String testId(@PathVariable String id) {
        return "Requesting metadata for id " + id;
    }

    @RequestMapping("/pls")
    public String pls() {
        return "pls wrk ";
    }


    /*
     * This is not an oai endpoint. It's here only to expose the metadata
     */
//    @RequestMapping("/cite")
//    public String contextAction (Model model, HttpServletRequest request, HttpServletResponse response)
//            throws IOException, ServletException {
//        Context context = null;
//        try {
//            request.setCharacterEncoding("UTF-8");
//            context = contextService.getContext();
//
//            XOAIManager manager = xoaiManagerResolver.getManager();
//
//            OAIDataProvider dataProvider = new OAIDataProvider(manager, "request",
//                    identifyResolver.getIdentify(),
//                    setRepositoryResolver.getSetRepository(),
//                    itemRepositoryResolver.getItemRepository(),
//                    resumptionTokenFormat);
//
//            OutputStream out = response.getOutputStream();
//
//            // adding some defaults for /cite requests this will make the URL simple
//            // only handle and metadataPrefix will be required
//            Map<String, List<String>> parameterMap = buildParametersMap(request);
//            if(!parameterMap.containsKey("verb")) {
//                parameterMap.put("verb", asList("GetRecord"));
//            }
//            if(!parameterMap.containsKey("metadataPrefix")) {
//                parameterMap.put("metadataPrefix", asList("cmdi"));
//            } else {
//                List<String> mp = parameterMap.get("metadataPrefix");
//                List<String> lcMP = new ArrayList<String>();
//                for(String m : mp) {
//                    lcMP.add(m.toLowerCase());
//                }
//                parameterMap.put("metadataPrefix", lcMP);
//            }
//            if(!parameterMap.containsKey("identifier")) {
//                parameterMap.put("identifier", asList("oai:" + request.getServerName() + ":" + request
//                    .getParameter("handle")));
//                parameterMap.remove("handle");
//            }
//            /////////////////////////////////////////////////////////////////////////
//
//            OAIRequestParameters parameters = new OAIRequestParameters(parameterMap);
//
//            response.setContentType("application/xml");
//
//            OAIPMH oaipmh = dataProvider.handle(parameters);
//
//            XmlOutputContext xmlOutContext = XmlOutputContext.emptyContext(out);
//            xmlOutContext.getWriter().writeStartDocument();
//
//            //Try to obtain just the metadata, if that fails return "normal" response
//            try{
//                oaipmh.getInfo().getGetRecord().getRecord().getMetadata().write(xmlOutContext);
//            }catch(Exception e){
//                oaipmh.write(xmlOutContext);
//            }
//
//            xmlOutContext.getWriter().writeEndDocument();
//            xmlOutContext.getWriter().flush();
//            xmlOutContext.getWriter().close();
//
//            out.flush();
//            out.close();
//        } catch (InvalidContextException e) {
//            log.debug(e.getMessage(), e);
//            return indexAction(response, model);
//        } catch (ContextServiceException | WritingXmlException | XMLStreamException e) {
//            log.error(e.getMessage(), e);
//            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
//                    "Unexpected error while writing the output. For more information visit the log files.");
//        } catch (XOAIManagerResolverException e) {
//      throw new ServletException("OAI 2.0 wasn't correctly initialized, please check the log for previous errors", e);
//        } catch (OAIException e) {
//            log.error(e.getMessage(), e);
//            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
//                    "Unexpected error. For more information visit the log files.");
//        } finally {
//            closeContext(context);
//        }
//
//        return null; // response without content
//    }

}
