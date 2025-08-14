/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.xoai.services.impl.resources;

import javax.xml.transform.TransformerFactory;

import net.sf.saxon.jaxp.SaxonTransformerFactory;
import net.sf.saxon.s9api.DocumentBuilder;
import net.sf.saxon.s9api.Processor;

/**
 * Utility class to provide a shared Saxon processor instance to avoid
 * configuration incompatibility issues between different Saxon processors.
 *
 * @author Michaela Stefancova (dspace at dataquest.sk)
 */
public class SharedSaxonProcessor {

    private static SaxonTransformerFactory saxonTransformerFactory;
    private static Processor sharedProcessor;
    private static DocumentBuilder sharedDocumentBuilder;

    /**
     * Initialize the shared processor with the given transformer factory.
     * This should be called once during application startup.
     *
     * @param transformerFactory the Saxon transformer factory to use
     */
    public static void initialize(TransformerFactory transformerFactory) {
        if (saxonTransformerFactory == null) {
            if (!(transformerFactory instanceof SaxonTransformerFactory)) {
                throw new IllegalArgumentException("TransformerFactory must be an instance of SaxonTransformerFactory");
            }
            saxonTransformerFactory = (SaxonTransformerFactory) transformerFactory;
            sharedProcessor = saxonTransformerFactory.getProcessor();
            sharedDocumentBuilder = sharedProcessor.newDocumentBuilder();
        }
    }

    /**
     * Get the shared Saxon processor instance.
     * @return the shared processor
     */
    public static  Processor getProcessor() {
        if (sharedProcessor == null) {
            throw new IllegalStateException("SharedSaxonProcessor has not been initialized. Call initialize() first.");
        }
        return sharedProcessor;
    }

    /**
     * Get the shared Saxon document builder instance.
     * @return the shared document builder
     */
    public static DocumentBuilder getDocumentBuilder() {
        if (sharedProcessor == null) {
            throw new IllegalStateException("SharedSaxonProcessor has not been initialized. Call initialize() first.");
        }
        return sharedProcessor.newDocumentBuilder();
    }

    /**
     * Get the shared Saxon transformer factory instance.
     * @return the shared transformer factory
     */
    public static SaxonTransformerFactory getTransformerFactory() {
        if (saxonTransformerFactory == null) {
            throw new IllegalStateException("SharedSaxonProcessor has not been initialized. Call initialize() first.");
        }
        return saxonTransformerFactory;
    }
}
