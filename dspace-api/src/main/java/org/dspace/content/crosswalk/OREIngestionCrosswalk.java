/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.content.crosswalk;

import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.text.NumberFormat;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.logging.log4j.Logger;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Bitstream;
import org.dspace.content.BitstreamFormat;
import org.dspace.content.Bundle;
import org.dspace.content.DSpaceObject;
import org.dspace.content.Item;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.BitstreamFormatService;
import org.dspace.content.service.BitstreamService;
import org.dspace.content.service.BundleService;
import org.dspace.content.service.ItemService;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.harvest.ore.HarvestPolicyAware;
import org.dspace.harvest.ore.OreEgressPolicy;
import org.dspace.harvest.ore.OreResourceRejectedException;
import org.dspace.harvest.ore.OreUrlValidator;
import org.dspace.harvest.ore.RejectionReason;
import org.dspace.harvest.ore.SafeResourceFetcher;
import org.dspace.services.factory.DSpaceServicesFactory;
import org.jdom2.Attribute;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.jdom2.filter.Filters;
import org.jdom2.xpath.XPathExpression;
import org.jdom2.xpath.XPathFactory;

/**
 * ORE ingestion crosswalk
 * <p>
 * Processes an Atom-encoded ORE resource map and attempts to interpret it as a DSpace item.
 *
 * @author Alexey Maslov
 */
public class OREIngestionCrosswalk
    implements IngestionCrosswalk, HarvestPolicyAware {
    /**
     * log4j category
     */
    private static final Logger log = org.apache.logging.log4j.LogManager.getLogger();

    /* Namespaces */
    public static final Namespace ATOM_NS =
        Namespace.getNamespace("atom", "http://www.w3.org/2005/Atom");
    private static final Namespace ORE_ATOM =
        Namespace.getNamespace("oreatom", "http://www.openarchives.org/ore/atom/");
    private static final Namespace ORE_NS =
        Namespace.getNamespace("ore", "http://www.openarchives.org/ore/terms/");
    private static final Namespace RDF_NS =
        Namespace.getNamespace("rdf", "http://www.w3.org/1999/02/22-rdf-syntax-ns#");
    private static final Namespace DCTERMS_NS =
        Namespace.getNamespace("dcterms", "http://purl.org/dc/terms/");
    private static final Namespace DS_NS =
        Namespace.getNamespace("ds", "http://www.dspace.org/objectModel/");


    protected BitstreamService bitstreamService = ContentServiceFactory.getInstance().getBitstreamService();
    protected BitstreamFormatService bitstreamFormatService = ContentServiceFactory.getInstance()
                                                                                   .getBitstreamFormatService();
    protected BundleService bundleService = ContentServiceFactory.getInstance().getBundleService();
    protected ItemService itemService = ContentServiceFactory.getInstance().getItemService();

    private final SafeResourceFetcher resourceFetcher = new SafeResourceFetcher();
    private OreEgressPolicy oreEgressPolicy;

    @Override
    public void setOreEgressPolicy(OreEgressPolicy policy) {
        this.oreEgressPolicy = policy;
    }

    /**
     * The packagers and the XSLT CLI reach this crosswalk with no harvest context, so they get the
     * fail-closed policy rather than no policy at all.
     */
    private OreEgressPolicy egressPolicy() {
        if (oreEgressPolicy == null) {
            oreEgressPolicy = OreEgressPolicy
                .strictest(DSpaceServicesFactory.getInstance().getConfigurationService());
        }
        return oreEgressPolicy;
    }

    @Override
    public void ingest(Context context, DSpaceObject dso, List<Element> metadata, boolean createMissingMetadataFields)
        throws CrosswalkException, IOException, SQLException, AuthorizeException {

        // If this list contains only the root already, just pass it on
        if (metadata.size() == 1) {
            ingest(context, dso, metadata.get(0), createMissingMetadataFields);
        } else {
            // Otherwise, wrap them up
            Element wrapper = new Element("wrap", metadata.get(0).getNamespace());
            wrapper.addContent(metadata);

            ingest(context, dso, wrapper, createMissingMetadataFields);
        }
    }


    @Override
    public void ingest(Context context, DSpaceObject dso, Element root, boolean createMissingMetadataFields)
        throws CrosswalkException, IOException, SQLException, AuthorizeException {

        Instant timeStart = Instant.now();

        if (dso.getType() != Constants.ITEM) {
            throw new CrosswalkObjectNotSupported("OREIngestionCrosswalk can only crosswalk an Item.");
        }
        Item item = (Item) dso;

        if (root == null) {
            System.err.println("The element received by ingest was null");
            return;
        }

        Document doc = new Document();
        doc.addContent(root.detach());

        List<Element> aggregatedResources;
        String entryId;
        XPathExpression<Element> xpathLinks =
            XPathFactory.instance()
                        .compile("/atom:entry/atom:link[@rel=\"" + ORE_NS.getURI() + "aggregates" + "\"]",
                                 Filters.element(), null, ATOM_NS);
        aggregatedResources = xpathLinks.evaluate(doc);

        XPathExpression<Attribute> xpathAltHref =
            XPathFactory.instance()
                        .compile("/atom:entry/atom:link[@rel='alternate']/@href",
                                 Filters.attribute(), null, ATOM_NS);
        Attribute entryIdAttribute = xpathAltHref.evaluateFirst(doc);
        entryId = entryIdAttribute == null ? null : entryIdAttribute.getValue();

        // Next for each resource, create a bitstream
        NumberFormat nf = NumberFormat.getInstance();
        nf.setGroupingUsed(false);
        nf.setMinimumIntegerDigits(4);

        for (Element resource : aggregatedResources) {
            String href = resource.getAttributeValue("href");
            log.debug("ORE processing: " + href);

            String bundleName;
            Element desc = null;
            XPathExpression<Element> xpathDesc =
                XPathFactory.instance()
                    .compile("/atom:entry/oreatom:triples/rdf:Description[@rdf:about=\"" +
                                 this.encodeForURL(href) + "\"][1]",
                             Filters.element(), null, ATOM_NS, ORE_ATOM, RDF_NS);
            desc = xpathDesc.evaluateFirst(doc);

            // the harvested document need not carry an <rdf:type resource="..."/>, so neither may be dereferenced
            Element descType = desc == null ? null : desc.getChild("type", RDF_NS);
            String descTypeResource = descType == null ? null : descType.getAttributeValue("resource", RDF_NS);

            if ((DS_NS.getURI() + "DSpaceBitstream").equals(descTypeResource)) {
                bundleName = desc.getChildText("description", DCTERMS_NS);
                log.debug("Setting bundle name to: " + bundleName);
            } else {
                log.info("Could not obtain bundle name; using 'ORIGINAL'");
                bundleName = "ORIGINAL";
            }

            // Bundle names are not unique, so we just pick the first one if there's more than one.
            List<Bundle> targetBundles = itemService.getBundles(item, bundleName);
            Bundle targetBundle;

            // if null, create the new bundle and add it in
            if (targetBundles.size() == 0) {
                targetBundle = bundleService.create(context, item, bundleName);
                itemService.addBundle(context, item, targetBundle);
            } else {
                targetBundle = targetBundles.get(0);
            }

            InputStream in = null;
            if (href != null) {
                try {
                    // Make sure the url string escapes all the oddball characters
                    String processedURL = encodeForURL(href);
                    // The trust anchor of the egress policy is the collection's oai_source, NOT entryId:
                    // entryId is read from this remote document and is therefore attacker-controlled.
                    in = resourceFetcher.fetch(OreUrlValidator.parse(processedURL), egressPolicy());
                } catch (IOException ioe) {
                    // a transport failure has to drop this record only; escaping here would stop the whole
                    // collection, and after removeAllBundles it would commit the item without its files
                    throw transferFailed(href, ioe);
                }
            } else {
                throw new CrosswalkException("Entry did not contain link to resource: " + entryId);
            }

            // ingest and update
            if (in != null) {
                Bitstream newBitstream;
                try {
                    newBitstream = bitstreamService.create(context, targetBundle, in);
                } catch (IOException ioe) {
                    // the body is only read here, and the bitstore wraps everything it catches in a plain
                    // IOException, so both the size cap and a mid-transfer failure surface at this point
                    throw transferFailed(href, ioe);
                }

                String bsName = resource.getAttributeValue("title");
                newBitstream.setName(context, bsName);

                // Identify the format
                String mimeString = resource.getAttributeValue("type");
                BitstreamFormat bsFormat = bitstreamFormatService.findByMIMEType(context, mimeString);
                if (bsFormat == null) {
                    bsFormat = bitstreamFormatService.guessFormat(context, newBitstream);
                }
                newBitstream.setFormat(context, bsFormat);
                bitstreamService.update(context, newBitstream);

                bundleService.addBitstream(context, targetBundle, newBitstream);
                bundleService.update(context, targetBundle);
            } else {
                throw new CrosswalkException("Could not retrieve bitstream: " + entryId);
            }

        }
        log.info(
            "OREIngest for Item " + item.getID() + " took: " +
                (Instant.now().toEpochMilli() - timeStart.toEpochMilli()) + "ms.");
    }


    /**
     * Turn an I/O failure while fetching or storing a file into a rejection of this one record.
     *
     * @param href  the resource that could not be transferred
     * @param cause the failure, kept so the real reason still reaches the log
     * @return the rejection to throw
     */
    private OreResourceRejectedException transferFailed(String href, IOException cause) {
        // the cap is enforced while the body streams, so it arrives wrapped rather than as itself
        boolean tooLarge =
            ExceptionUtils.indexOfType(cause, SafeResourceFetcher.ResponseTooLargeException.class) >= 0;
        return new OreResourceRejectedException(
            tooLarge ? RejectionReason.RESPONSE_TOO_LARGE : RejectionReason.FETCH_FAILED, href,
            tooLarge ? "size cap exceeded" : "transfer failed", cause);
    }

    /**
     * Helper method to escape all characters that are not part of the canon set
     *
     * @param sourceString source unescaped string
     */
    private String encodeForURL(String sourceString) {
        Character lowalpha[] = {'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i',
            'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r',
            's', 't', 'u', 'v', 'w', 'x', 'y', 'z'};
        Character upalpha[] = {'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I',
            'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R',
            'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z'};
        Character digit[] = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9'};
        Character mark[] = {'-', '_', '.', '!', '~', '*', '\'', '(', ')'};

        // reserved
        Character reserved[] = {';', '/', '?', ':', '@', '&', '=', '+', '$', ',', '%', '#'};

        Set<Character> URLcharsSet = new HashSet<Character>();
        URLcharsSet.addAll(Arrays.asList(lowalpha));
        URLcharsSet.addAll(Arrays.asList(upalpha));
        URLcharsSet.addAll(Arrays.asList(digit));
        URLcharsSet.addAll(Arrays.asList(mark));
        URLcharsSet.addAll(Arrays.asList(reserved));

        StringBuilder processedString = new StringBuilder();
        for (int i = 0; i < sourceString.length(); i++) {
            char ch = sourceString.charAt(i);
            if (URLcharsSet.contains(ch)) {
                processedString.append(ch);
            } else {
                processedString.append("%").append(Integer.toHexString((int) ch));
            }
        }

        return processedString.toString();
    }

}
