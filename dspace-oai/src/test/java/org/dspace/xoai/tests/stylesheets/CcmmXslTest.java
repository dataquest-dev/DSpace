/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.xoai.tests.stylesheets;

import static org.dspace.xoai.tests.support.XmlMatcherBuilder.xml;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsEqual.equalTo;

import org.dspace.xoai.tests.support.XmlMatcherBuilder;
import org.junit.Test;

/**
 * Tests for the CCMM (Czech Common Metadata Model) 1.1.0 OAI-PMH crosswalk.
 *
 * @see <a href="https://github.com/techlib/CCMM">CCMM Schema</a>
 * @see <a href="https://github.com/ufal/clarin-dspace/issues/1145">Issue #1145</a>
 */
public class CcmmXslTest extends AbstractXSLTest {

    private static final String CCMM_NS = "https://schema.ccmm.cz/research-data/1.1";

    @Test
    public void ccmmCanTransformInput() throws Exception {
        String result = apply("ccmm.xsl").to(resource("xoai-ccmm-test.xml"));
        assertThat(result, is(ccmm().withXPath("//ccmm:title", equalTo("Czech NLP Dataset v2.0"))));
    }

    @Test
    public void ccmmContainsPublicationYear() throws Exception {
        String result = apply("ccmm.xsl").to(resource("xoai-ccmm-test.xml"));
        assertThat(result, is(ccmm().withXPath("//ccmm:publication_year", equalTo("2025"))));
    }

    @Test
    public void ccmmContainsIdentifier() throws Exception {
        String result = apply("ccmm.xsl").to(resource("xoai-ccmm-test.xml"));
        assertThat(result, is(ccmm().withXPath(
            "//ccmm:dataset/ccmm:identifier/ccmm:value",
            equalTo("http://hdl.handle.net/11234/1-5678"))));
    }

    @Test
    public void ccmmContainsCreator() throws Exception {
        String result = apply("ccmm.xsl").to(resource("xoai-ccmm-test.xml"));
        assertThat(result, is(ccmm().withXPath(
            "//ccmm:dataset/ccmm:qualified_relation[1]/ccmm:relation/ccmm:person/ccmm:name",
            equalTo("Novak, Jan"))));
    }

    @Test
    public void ccmmContainsSubjects() throws Exception {
        String result = apply("ccmm.xsl").to(resource("xoai-ccmm-test.xml"));
        assertThat(result, is(ccmm().withXPath(
            "//ccmm:dataset/ccmm:subject[1]/ccmm:title",
            equalTo("linguistics"))));
    }

    @Test
    public void ccmmContainsResourceType() throws Exception {
        String result = apply("ccmm.xsl").to(resource("xoai-ccmm-test.xml"));
        assertThat(result, is(ccmm().withXPath(
            "//ccmm:dataset/ccmm:resource_type/ccmm:label",
            equalTo("corpus"))));
    }

    @Test
    public void ccmmContainsDescription() throws Exception {
        String result = apply("ccmm.xsl").to(resource("xoai-ccmm-test.xml"));
        assertThat(result, is(ccmm().withXPath(
            "//ccmm:dataset/ccmm:description/ccmm:description_text",
            equalTo("A sample dataset for testing CCMM crosswalk output in the OAI-PMH protocol."))));
    }

    @Test
    public void ccmmContainsLicense() throws Exception {
        String result = apply("ccmm.xsl").to(resource("xoai-ccmm-test.xml"));
        assertThat(result, is(ccmm().withXPath(
            "//ccmm:dataset/ccmm:terms_of_use/ccmm:license/ccmm:iri",
            equalTo("https://creativecommons.org/licenses/by/4.0/"))));
    }

    @Test
    public void ccmmContainsAccessRights() throws Exception {
        String result = apply("ccmm.xsl").to(resource("xoai-ccmm-test.xml"));
        assertThat(result, is(ccmm().withXPath(
            "//ccmm:dataset/ccmm:terms_of_use/ccmm:access_rights/ccmm:iri",
            equalTo("http://purl.org/coar/access_right/c_abf2"))));
    }

    @Test
    public void ccmmContainsPrimaryLanguage() throws Exception {
        String result = apply("ccmm.xsl").to(resource("xoai-ccmm-test.xml"));
        assertThat(result, is(ccmm().withXPath(
            "//ccmm:dataset/ccmm:primary_language/ccmm:label",
            equalTo("ces"))));
    }

    @Test
    public void ccmmContainsAlternateTitle() throws Exception {
        String result = apply("ccmm.xsl").to(resource("xoai-ccmm-test.xml"));
        assertThat(result, is(ccmm().withXPath(
            "//ccmm:dataset/ccmm:alternate_title/ccmm:title",
            equalTo("CND 2.0"))));
    }

    @Test
    public void ccmmContainsPublisher() throws Exception {
        String result = apply("ccmm.xsl").to(resource("xoai-ccmm-test.xml"));
        assertThat(result, is(ccmm().withXPath(
            "//ccmm:dataset/ccmm:qualified_relation[ccmm:role/ccmm:label='Distributor']/ccmm:relation/ccmm:organization/ccmm:name",
            equalTo("Charles University, Faculty of Mathematics and Physics, Institute of Formal and Applied Linguistics"))));
    }

    @Test
    public void ccmmContainsMetadataIdentification() throws Exception {
        String result = apply("ccmm.xsl").to(resource("xoai-ccmm-test.xml"));
        assertThat(result, is(ccmm().withXPath(
            "//ccmm:dataset/ccmm:metadata_identification/ccmm:conforms_to_standard/ccmm:iri",
            equalTo("https://schema.ccmm.cz/research-data/1.1"))));
    }

    @Test
    public void ccmmCanTransformBasicXoaiInput() throws Exception {
        // Test with the default xoai-test1.xml (simpler data) to ensure crosswalk
        // handles missing fields gracefully
        String result = apply("ccmm.xsl").to(resource("xoai-test1.xml"));
        assertThat(result, is(ccmm().withXPath("//ccmm:title", equalTo("Test Webpage"))));
    }

    // ---- Fallback scenario tests ----

    @Test
    public void ccmmFallbackTitleIsUntitled() throws Exception {
        // When dc.title is missing, fallback to "Untitled"
        String result = apply("ccmm.xsl").to(resource("xoai-ccmm-minimal-test.xml"));
        assertThat(result, is(ccmm().withXPath("//ccmm:dataset/ccmm:title", equalTo("Untitled"))));
    }

    @Test
    public void ccmmFallbackPublicationYearIs9999() throws Exception {
        // When dc.date.issued and dc.date.accessioned are missing, fallback to "9999"
        String result = apply("ccmm.xsl").to(resource("xoai-ccmm-minimal-test.xml"));
        assertThat(result, is(ccmm().withXPath("//ccmm:dataset/ccmm:publication_year", equalTo("9999"))));
    }

    @Test
    public void ccmmFallbackSubjectIsUnspecified() throws Exception {
        // When dc.subject is missing, fallback to "unspecified"
        String result = apply("ccmm.xsl").to(resource("xoai-ccmm-minimal-test.xml"));
        assertThat(result, is(ccmm().withXPath(
            "//ccmm:dataset/ccmm:subject/ccmm:title", equalTo("unspecified"))));
    }

    @Test
    public void ccmmFallbackIdentifierUsesOthersHandle() throws Exception {
        // When dc.identifier.uri and dc.identifier.doi are missing, use others/handle
        String result = apply("ccmm.xsl").to(resource("xoai-ccmm-minimal-test.xml"));
        assertThat(result, is(ccmm().withXPath(
            "//ccmm:dataset/ccmm:identifier/ccmm:value",
            equalTo("http://hdl.handle.net/99999/test-1"))));
    }

    @Test
    public void ccmmFallbackRepositoryNameIsUnknown() throws Exception {
        // When repository/name is missing, fallback to "Unknown Repository"
        String result = apply("ccmm.xsl").to(resource("xoai-ccmm-minimal-test.xml"));
        assertThat(result, is(ccmm().withXPath(
            "//ccmm:dataset/ccmm:metadata_identification/ccmm:qualified_relation/ccmm:relation/ccmm:organization/ccmm:name",
            equalTo("Unknown Repository"))));
    }

    @Test
    public void ccmmFallbackLicenseIsUnspecified() throws Exception {
        // When dc.rights.uri is missing but dc.rights text exists, license IRI is unspecified
        String result = apply("ccmm.xsl").to(resource("xoai-ccmm-minimal-test.xml"));
        assertThat(result, is(ccmm().withXPath(
            "//ccmm:dataset/ccmm:terms_of_use/ccmm:license/ccmm:iri",
            equalTo("https://model.ccmm.cz/vocabulary/ccmm/license/unspecified"))));
    }

    private XmlMatcherBuilder ccmm() {
        return xml()
            .withNamespace("ccmm", CCMM_NS);
    }
}
