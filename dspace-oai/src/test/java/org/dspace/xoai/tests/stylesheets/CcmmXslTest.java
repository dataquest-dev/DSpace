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
        // META-SHARE "corpus" maps to the COAR resource type "dataset"
        String result = apply("ccmm.xsl").to(resource("xoai-ccmm-test.xml"));
        assertThat(result, is(ccmm().withXPath(
            "//ccmm:dataset/ccmm:resource_type/ccmm:iri",
            equalTo("http://purl.org/coar/resource_type/c_ddb1"))));
        assertThat(result, is(ccmm().withXPath(
            "//ccmm:dataset/ccmm:resource_type/ccmm:label[1]",
            equalTo("dataset"))));
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
        // CCMM: "Use IRI identifier from the register
        // http://publications.europa.eu/resource/authority/language"
        String result = apply("ccmm.xsl").to(resource("xoai-ccmm-test.xml"));
        assertThat(result, is(ccmm().withXPath(
            "//ccmm:dataset/ccmm:primary_language/ccmm:iri",
            equalTo("http://publications.europa.eu/resource/authority/language/CES"))));
        assertThat(result, is(ccmm().withXPath(
            "count(//ccmm:dataset/ccmm:primary_language/ccmm:label)", equalTo("0"))));
    }

    @Test
    public void ccmmContainsAlternateTitle() throws Exception {
        String result = apply("ccmm.xsl").to(resource("xoai-ccmm-test.xml"));
        assertThat(result, is(ccmm().withXPath(
            "//ccmm:dataset/ccmm:alternate_title/ccmm:title",
            equalTo("CND 2.0"))));
    }

    @Test
    public void ccmmPublisherUsesPublisherRole() throws Exception {
        String result = apply("ccmm.xsl").to(resource("xoai-ccmm-test.xml"));
        assertThat(result, is(ccmm().withXPath(
            "//ccmm:dataset/ccmm:qualified_relation[ccmm:role/ccmm:label='Publisher']/ccmm:relation/ccmm:organization/ccmm:name",
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
    public void ccmmFallbackLicenseIsEmptyAndRightsTextBecomesDescription() throws Exception {
        // When no licence URI is known, ccmm:license stays empty (which is valid) instead of
        // carrying a made-up IRI; the free-text wording moves to terms_of_use/description.
        String result = apply("ccmm.xsl").to(resource("xoai-ccmm-minimal-test.xml"));
        assertThat(result, is(ccmm().withXPath(
            "count(//ccmm:dataset/ccmm:terms_of_use/ccmm:license/ccmm:iri)", equalTo("0"))));
        assertThat(result, is(ccmm().withXPath(
            "//ccmm:dataset/ccmm:terms_of_use/ccmm:description",
            equalTo("All rights reserved"))));
    }

    // ---- Controlled vocabularies ----

    @Test
    public void ccmmCreatorRoleUsesCcmmAgentRoleCodelist() throws Exception {
        String result = apply("ccmm.xsl").to(resource("xoai-ccmm-test.xml"));
        assertThat(result, is(ccmm().withXPath(
            "//ccmm:dataset/ccmm:qualified_relation[1]/ccmm:role/ccmm:iri",
            equalTo("https://vocabs.ccmm.cz/registry/codelist/AgentRole/Creator"))));
    }

    @Test
    public void ccmmDateTypeUsesCcmmTimeReferenceCodelist() throws Exception {
        String result = apply("ccmm.xsl").to(resource("xoai-ccmm-test.xml"));
        assertThat(result, is(ccmm().withXPath(
            "//ccmm:dataset/ccmm:time_reference[1]/ccmm:date_type/ccmm:iri",
            equalTo("https://vocabs.ccmm.cz/registry/codelist/TimeReference/Issued"))));
    }

    // ---- Access rights come from DSpace's own computed access status ----

    @Test
    public void ccmmAccessRightsFollowAccessStatus() throws Exception {
        // others/access-status = open.access
        String result = apply("ccmm.xsl").to(resource("xoai-ccmm-test.xml"));
        assertThat(result, is(ccmm().withXPath(
            "//ccmm:dataset/ccmm:terms_of_use/ccmm:access_rights/ccmm:iri",
            equalTo("http://purl.org/coar/access_right/c_abf2"))));
    }

    @Test
    public void ccmmAccessRightsFallBackToRestricted() throws Exception {
        // No others/access-status at all: never claim open access
        String result = apply("ccmm.xsl").to(resource("xoai-ccmm-minimal-test.xml"));
        assertThat(result, is(ccmm().withXPath(
            "//ccmm:dataset/ccmm:terms_of_use/ccmm:access_rights/ccmm:iri",
            equalTo("http://purl.org/coar/access_right/c_16ec"))));
    }

    @Test
    public void ccmmOriginalRepositoryIsTheRepositoryUrl() throws Exception {
        // XOAI carries repository/@url; deriving it from the item URI names the item itself
        String result = apply("ccmm.xsl").to(resource("xoai-ccmm-test.xml"));
        assertThat(result, is(ccmm().withXPath(
            "//ccmm:metadata_identification/ccmm:original_repository/ccmm:iri",
            equalTo("https://lindat.mff.cuni.cz/repository/"))));
    }

    @Test
    public void ccmmXmlLangFollowsTheXoaiLanguageWrapper() throws Exception {
        // a value stored under <element name="cs_CZ"> must not be tagged as English
        String result = apply("ccmm.xsl").to(resource("xoai-ccmm-test.xml"));
        assertThat(result, is(ccmm().withXPath(
            "//ccmm:subject/ccmm:title[.='korpus']/@xml:lang", equalTo("cs"))));
        assertThat(result, is(ccmm().withXPath(
            "//ccmm:subject/ccmm:title[.='linguistics']/@xml:lang", equalTo("en"))));
    }

    // ---- CLARIN approximate dates (dc.date.issued = "0000") ----

    @Test
    public void ccmmApproximateDateSetsPublicationYearToEarliestAttestedYear() throws Exception {
        String result = apply("ccmm.xsl").to(resource("xoai-ccmm-approximate-date-test.xml"));
        assertThat(result, is(ccmm().withXPath(
            "//ccmm:dataset/ccmm:publication_year", equalTo("1930"))));
    }

    @Test
    public void ccmmApproximateDateRangeBecomesTimeInterval() throws Exception {
        String result = apply("ccmm.xsl").to(resource("xoai-ccmm-approximate-date-test.xml"));
        assertThat(result, is(ccmm().withXPath(
            "//ccmm:time_reference/ccmm:temporal_representation/ccmm:time_interval"
                + "/ccmm:beginning/ccmm:date", equalTo("1930-01-01"))));
        assertThat(result, is(ccmm().withXPath(
            "//ccmm:time_reference/ccmm:temporal_representation/ccmm:time_interval"
                + "/ccmm:end/ccmm:date", equalTo("1950-12-31"))));
    }

    @Test
    public void ccmmApproximateDateKeepsOriginalTextVerbatim() throws Exception {
        String result = apply("ccmm.xsl").to(resource("xoai-ccmm-approximate-date-test.xml"));
        assertThat(result, is(ccmm().withXPath(
            "//ccmm:time_reference/ccmm:date_information", equalTo("cca 1930-1950"))));
    }

    @Test
    public void ccmmEmitsExactlyOneIssuedAndOneCreatedTimeReference() throws Exception {
        // CCMM requires a Created time reference, and requires publication_year to equal the
        // year of the Issued date - which several Issued references could not satisfy.
        String result = apply("ccmm.xsl").to(resource("xoai-ccmm-test.xml"));
        assertThat(result, is(ccmm().withXPath(
            "count(//ccmm:time_reference[ccmm:date_type/ccmm:label='Issued'])", equalTo("1"))));
        assertThat(result, is(ccmm().withXPath(
            "count(//ccmm:time_reference[ccmm:date_type/ccmm:label='Created'])", equalTo("1"))));
    }

    @Test
    public void ccmmApproximateDateNeverEmitsYearZero() throws Exception {
        String result = apply("ccmm.xsl").to(resource("xoai-ccmm-approximate-date-test.xml"));
        assertThat(result, is(ccmm().withXPath(
            "count(//ccmm:time_instant[starts-with(ccmm:date, '0000')])", equalTo("0"))));
    }

    private XmlMatcherBuilder ccmm() {
        return xml()
            .withNamespace("ccmm", CCMM_NS);
    }
}
