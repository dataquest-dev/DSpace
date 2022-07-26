/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */

/* Created for LINDAT/CLARIAH-CZ (UFAL) */
package org.dspace.utils;

import java.io.InputStreamReader;
import java.io.Reader;
import java.util.List;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

//import net.sf.saxon.om.NodeInfo;
//import net.sf.saxon.s9api.ItemType;
//import net.sf.saxon.s9api.OccurrenceIndicator;
//import net.sf.saxon.s9api.Processor;
//import net.sf.saxon.s9api.QName;
//import net.sf.saxon.s9api.SaxonApiException;
//import net.sf.saxon.s9api.SequenceType;
//import net.sf.saxon.s9api.XdmAtomicValue;
//import net.sf.saxon.s9api.XdmNode;
//import net.sf.saxon.s9api.XdmValue;
import org.dspace.app.util.DCInput;
import org.dspace.content.Bitstream;
import org.dspace.content.DSpaceObject;
import org.dspace.content.Item;
import org.dspace.content.ItemServiceImpl;
import org.dspace.content.MetadataValue;
import org.dspace.content.service.BitstreamService;
import org.dspace.content.service.ItemService;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.xoai.app.BasicConfiguration;
import org.dspace.xoai.services.api.HandleResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;


@Component
public class SpecialItemService {

    @Autowired
    private static HandleResolver handleResolver;

    @Autowired
    private static ItemService itemService;

    @Autowired
    private static BitstreamService bitstreamService;


    /** log4j logger */
    private static org.apache.log4j.Logger log = org.apache.log4j.Logger
            .getLogger(SpecialItemService.class);

    public Node getUploadedMetadata(String handle) {
        Node ret = null;
        Context context = null;
        try {
            context = new Context();

            DSpaceObject dSpaceObject = handleResolver.resolve(handle);
            List<MetadataValue> metadataValues = itemService.getMetadataByMetadataString(((Item) dSpaceObject),
                    "local.hasMetadata");
            if (dSpaceObject != null && dSpaceObject.getType() == Constants.ITEM && hasOwnMetadata(metadataValues)) {

                Bitstream bitstream = itemService.getBundles(((Item) dSpaceObject), "METADATA").get(0)
                        .getBitstreams().get(0);
                context.turnOffAuthorisationSystem();
                Reader reader = new InputStreamReader(bitstreamService.retrieve(context, bitstream));
                context.restoreAuthSystemState();
                try {
                    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                    factory.setNamespaceAware(true);
                    DocumentBuilder builder = factory.newDocumentBuilder();
                    Document doc = builder.parse(new InputSource(reader));
                    ret = doc.getDocumentElement();
                } finally {
                    reader.close();
                }

            }
        } catch (Exception e) {
            log.error(e);
            try {
                ret = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
            } catch (ParserConfigurationException ex) {
                log.error(ex);
            }
        } finally {
            closeContext(context);
        }
        return ret;
    }

    public Node getFunding(String mdValue) {
        String ns = "http://www.clarin.eu/cmd/";
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder;
        try {
            builder = factory.newDocumentBuilder();
            Document doc = builder.newDocument();
            Element el = doc.createElementNS(ns, "funding");
            doc.appendChild(el);
            Element organization = doc.createElementNS(ns, "organization");
            Element projName = doc.createElementNS(ns, "projectName");
            Element code = doc.createElementNS(ns, "code");
            Element fundsType = doc.createElementNS(ns, "fundsType");

            String[] values = mdValue
                    .split(DCInput.ComplexDefinitions.getSeparator(), -1);

            // mind the order in input forms, org;code;projname;type
            Element[] elements = {organization, code, projName, fundsType};
            for (int i = 0; i < elements.length; i++) {
                elements[i].appendChild(doc.createTextNode(values[i]));
                el.appendChild(elements[i]);
            }
            return doc.getDocumentElement();
        } catch (ParserConfigurationException e) {
            return null;
        }
    }

    public Node getContact(String mdValue) {
        String ns = "http://www.clarin.eu/cmd/";
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder;
        try {
            builder = factory.newDocumentBuilder();
            Document doc = builder.newDocument();
            Element el = doc.createElementNS(ns, "contactPerson");
            doc.appendChild(el);
            Element first = doc.createElementNS(ns, "firstName");
            Element last = doc.createElementNS(ns, "lastName");
            Element email = doc.createElementNS(ns, "email");
            Element affil = doc.createElementNS(ns, "affiliation");

            String[] values = mdValue
                    .split(DCInput.ComplexDefinitions.getSeparator(), -1);

            Element[] elements = {first, last, email, affil};
            for (int i = 0; i < values.length; i++) {
                elements[i].appendChild(doc.createTextNode(values[i]));
                el.appendChild(elements[i]);
            }
            return doc.getDocumentElement();
        } catch (ParserConfigurationException e) {
            return null;
        }
    }

    public Node getSize(String mdValue) {
        String ns = "http://www.clarin.eu/cmd/";
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder;
        try {
            builder = factory.newDocumentBuilder();
            Document doc = builder.newDocument();
            Element el = doc.createElementNS(ns, "size");
            doc.appendChild(el);
            Element size = doc.createElementNS(ns, "size");
            Element unit = doc.createElementNS(ns, "unit");

            String[] values = mdValue
                    .split(DCInput.ComplexDefinitions.getSeparator(), -1);

            Element[] elements = {size, unit};
            for (int i = 0; i < values.length; i++) {
                elements[i].appendChild(doc.createTextNode(values[i]));
                el.appendChild(elements[i]);
            }
            return doc.getDocumentElement();
        } catch (ParserConfigurationException e) {
            return null;
        }
    }

    public Node getAuthor(String mdValue) {
        String ns = "http://www.clarin.eu/cmd/";
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder;
        try {
            builder = factory.newDocumentBuilder();
            Document doc = builder.newDocument();
            Element el = doc.createElementNS(ns, "author");
            doc.appendChild(el);
            Element last = doc.createElementNS(ns, "lastName");

            String[] values = mdValue
                    .split(",", 2);

            last.appendChild(doc.createTextNode(values[0]));
            el.appendChild(last);
            if (values.length > 1) {
                Element first = doc.createElementNS(ns, "firstName");
                first.appendChild(doc.createTextNode(values[1]));
                el.appendChild(first);
            }
            return doc.getDocumentElement();
        } catch (ParserConfigurationException e) {
            return null;
        }
    }

    public boolean hasOwnMetadata(List<MetadataValue> metadataValues) {
        if (metadataValues.size() == 1 && metadataValues.get(0).getValue().equalsIgnoreCase("true")) {
            return true;
        }
        return false;
    }

    private void closeContext(Context c) {
        if (c != null) {
            c.abort();
        }
    }
}
