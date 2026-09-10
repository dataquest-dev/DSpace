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
 * <p>The CCMM schema is an xs:sequence, so element order is part of validity, and every
 * controlled value has to come from the register CCMM names for it.  Validating against the
 * schema itself would pull xml.xsd and GML over the network at build time, so the invariants
 * that matter are asserted here instead: the order of the dataset children, and closed-set
 * guards that fail if any off-register string or unresolvable IRI reappears.</p>
 *
 * @see <a href="https://github.com/techlib/CCMM">CCMM Schema</a>
 * @see <a href="https://github.com/ufal/clarin-dspace/issues/1145">Issue #1145</a>
 */
public class CcmmXslTest extends AbstractXSLTest {

    private static final String CCMM_NS = "https://schema.ccmm.cz/research-data/1.1";
    private static final String XML_NS = "http://www.w3.org/XML/1998/namespace";
    private static final String MAIN = "xoai-ccmm-test.xml";
    private static final String MINIMAL = "xoai-ccmm-minimal-test.xml";
    private static final String APPROX = "xoai-ccmm-approximate-date-test.xml";
    private static final String STOCK = "xoai-ccmm-stock-dspace-test.xml";
    private static final String DATES = "xoai-ccmm-date-shapes-test.xml";
    private static final String CLARIN = "xoai-ccmm-clarin-access-test.xml";
    private static final String ENUM = "xoai-ccmm-enumerated-date-test.xml";
    private static final String ACA = "xoai-ccmm-academic-licence-test.xml";
    private static final String BARE = "xoai-ccmm-no-identifier-test.xml";
    private static final String FLAG = "xoai-ccmm-restricted-flag-test.xml";
    private static final String EMBARGO = "xoai-ccmm-embargo-test.xml";

    @Test
    public void ccmmCanTransformInput() throws Exception {
        String result = apply("ccmm.xsl").to(resource(MAIN));
        assertThat(result, is(ccmm().withXPath("//ccmm:title", equalTo("Czech NLP Dataset v2.0"))));
    }

    @Test
    public void ccmmContainsPublicationYear() throws Exception {
        String result = apply("ccmm.xsl").to(resource(MAIN));
        assertThat(result, is(ccmm().withXPath("//ccmm:publication_year", equalTo("2025"))));
    }

    @Test
    public void ccmmContainsIdentifier() throws Exception {
        String result = apply("ccmm.xsl").to(resource(MAIN));
        assertThat(result, is(ccmm().withXPath(
            "//ccmm:dataset/ccmm:identifier[1]/ccmm:value",
            equalTo("http://hdl.handle.net/11234/1-5678"))));
        assertThat(result, is(ccmm().withXPath(
            "//ccmm:dataset/ccmm:identifier[1]/ccmm:scheme/ccmm:label", equalTo("Handle"))));
    }

    @Test
    public void ccmmKeepsIdentifiersOtherThanTheItemUri() throws Exception {
        // dc.identifier.other is an external identifier and must not be dropped
        String result = apply("ccmm.xsl").to(resource(MAIN));
        assertThat(result, is(ccmm().withXPath(
            "count(//ccmm:dataset/ccmm:identifier[ccmm:value='ISLRN-123-456-789'])", equalTo("1"))));
    }

    @Test
    public void ccmmContainsCreator() throws Exception {
        String result = apply("ccmm.xsl").to(resource(MAIN));
        assertThat(result, is(ccmm().withXPath(
            "//ccmm:dataset/ccmm:qualified_relation[1]/ccmm:relation/ccmm:person/ccmm:name",
            equalTo("Novak, Jan"))));
    }

    @Test
    public void ccmmSplitsPersonNamesIntoGivenAndFamily() throws Exception {
        String result = apply("ccmm.xsl").to(resource(MAIN));
        assertThat(result, is(ccmm().withXPath(
            "//ccmm:person[ccmm:name='Novak, Jan']/ccmm:family_name", equalTo("Novak"))));
        assertThat(result, is(ccmm().withXPath(
            "//ccmm:person[ccmm:name='Novak, Jan']/ccmm:given_name", equalTo("Jan"))));
    }

    @Test
    public void ccmmTypesCorporateCreditsAsOrganizationsNotPeople() throws Exception {
        // a newsreel producer is not a natural person
        String result = apply("ccmm.xsl").to(resource(MAIN));
        assertThat(result, is(ccmm().withXPath(
            "count(//ccmm:qualified_relation[ccmm:relation/ccmm:organization/ccmm:name='Aktualita'])",
            equalTo("1"))));
        assertThat(result, is(ccmm().withXPath(
            "count(//ccmm:person[ccmm:name='Aktualita'])", equalTo("0"))));
    }

    @Test
    public void ccmmDropsAnvlPlaceholderAgents() throws Exception {
        // "(:unav) Unknown author" names nobody and must not become an agent
        String result = apply("ccmm.xsl").to(resource(MAIN));
        assertThat(result, is(ccmm().withXPath(
            "count(//ccmm:person[contains(ccmm:name, '(:un')])", equalTo("0"))));
    }

    @Test
    public void ccmmEmitsCreatorAndPublisherEvenWhenTheRecordNamesNeither() throws Exception {
        String result = apply("ccmm.xsl").to(resource(MINIMAL));
        assertThat(result, is(ccmm().withXPath(
            "count(//ccmm:qualified_relation[ccmm:role/ccmm:label='Creator'])", equalTo("1"))));
        assertThat(result, is(ccmm().withXPath(
            "count(//ccmm:dataset/ccmm:qualified_relation[ccmm:role/ccmm:label='Publisher'])",
            equalTo("1"))));
    }

    @Test
    public void ccmmContainsSubjects() throws Exception {
        String result = apply("ccmm.xsl").to(resource(MAIN));
        assertThat(result, is(ccmm().withXPath(
            "//ccmm:dataset/ccmm:subject[1]/ccmm:title", equalTo("linguistics"))));
    }

    @Test
    public void ccmmHierarchicalSubjectKeepsThePathAsAClassificationCode() throws Exception {
        // "People::Masaryk ..." is a path through a thesaurus, not a subject heading
        String result = apply("ccmm.xsl").to(resource(MAIN));
        assertThat(result, is(ccmm().withXPath(
            "//ccmm:subject[ccmm:classification_code]/ccmm:title",
            equalTo("Masaryk Tomas Garrigue (1850-1937)"))));
        assertThat(result, is(ccmm().withXPath(
            "//ccmm:subject/ccmm:classification_code",
            equalTo("People::Masaryk Tomas Garrigue (1850-1937)"))));
    }

    @Test
    public void ccmmContainsResourceType() throws Exception {
        // META-SHARE "corpus" maps to the COAR resource type "dataset"
        String result = apply("ccmm.xsl").to(resource(MAIN));
        assertThat(result, is(ccmm().withXPath(
            "//ccmm:dataset/ccmm:resource_type/ccmm:iri",
            equalTo("http://purl.org/coar/resource_type/c_ddb1"))));
        assertThat(result, is(ccmm().withXPath(
            "//ccmm:dataset/ccmm:resource_type/ccmm:label[1]", equalTo("dataset"))));
    }

    @Test
    public void ccmmResourceTypeUnderstandsTheStockDspaceTypeList() throws Exception {
        String result = apply("ccmm.xsl").to(resource(STOCK));
        assertThat(result, is(ccmm().withXPath(
            "//ccmm:dataset/ccmm:resource_type/ccmm:iri",
            equalTo("http://purl.org/coar/resource_type/c_6501"))));
    }

    @Test
    public void ccmmContainsDescription() throws Exception {
        String result = apply("ccmm.xsl").to(resource(MAIN));
        assertThat(result, is(ccmm().withXPath(
            "//ccmm:description[ccmm:description_type/ccmm:label='Abstract']/ccmm:description_text",
            equalTo("A sample dataset for testing CCMM crosswalk output in the OAI-PMH protocol."))));
    }

    @Test
    public void ccmmUnqualifiedDescriptionIsNotAssertedToBeAnAbstract() throws Exception {
        // dc.description without a qualifier says nothing about what kind of description it is
        String result = apply("ccmm.xsl").to(resource(MAIN));
        assertThat(result, is(ccmm().withXPath(
            "//ccmm:description[ccmm:description_text='Built from newspaper text collected in 2024.']"
                + "/ccmm:description_type/ccmm:iri",
            equalTo("https://vocabs.ccmm.cz/registry/codelist/DescriptionType/Other"))));
    }

    @Test
    public void ccmmLicenseCarriesTheLicenceNameNotTheAccessCategory() throws Exception {
        // dc.rights.label is PUB/ACA/RES, which is a download gate, not the name of a licence
        String result = apply("ccmm.xsl").to(resource(MAIN));
        assertThat(result, is(ccmm().withXPath(
            "//ccmm:dataset/ccmm:terms_of_use/ccmm:license/ccmm:iri",
            equalTo("https://creativecommons.org/licenses/by/4.0/"))));
        assertThat(result, is(ccmm().withXPath(
            "//ccmm:dataset/ccmm:terms_of_use/ccmm:license/ccmm:label",
            equalTo("Creative Commons - Attribution 4.0 International"))));
        assertThat(result, is(ccmm().withXPath(
            "count(//ccmm:license/ccmm:label[.='PUB' or .='ACA' or .='RES'])", equalTo("0"))));
    }

    @Test
    public void ccmmContainsAccessRights() throws Exception {
        String result = apply("ccmm.xsl").to(resource(MAIN));
        assertThat(result, is(ccmm().withXPath(
            "//ccmm:dataset/ccmm:terms_of_use/ccmm:access_rights/ccmm:iri",
            equalTo("http://purl.org/coar/access_right/c_abf2"))));
    }

    @Test
    public void ccmmAccessRightsFollowTheClarinLicenceGate() throws Exception {
        // A PUB licence with files is downloadable by anyone, whatever DefaultAccessStatusHelper
        // computed from the primary bitstream's policies.
        String result = apply("ccmm.xsl").to(resource(MAIN));
        assertThat(result, is(ccmm().withXPath(
            "//ccmm:dataset/ccmm:terms_of_use/ccmm:access_rights/ccmm:label",
            equalTo("open access"))));
    }

    @Test
    public void ccmmAnItemWithNoFilesIsMetadataOnlyNotRestricted() throws Exception {
        String result = apply("ccmm.xsl").to(resource(MINIMAL));
        assertThat(result, is(ccmm().withXPath(
            "//ccmm:dataset/ccmm:terms_of_use/ccmm:access_rights/ccmm:iri",
            equalTo("http://purl.org/coar/access_right/c_14cb"))));
    }

    @Test
    public void ccmmContainsPrimaryLanguage() throws Exception {
        // CCMM: "Use IRI identifier from the register
        // http://publications.europa.eu/resource/authority/language"
        String result = apply("ccmm.xsl").to(resource(MAIN));
        assertThat(result, is(ccmm().withXPath(
            "//ccmm:dataset/ccmm:primary_language/ccmm:iri",
            equalTo("http://publications.europa.eu/resource/authority/language/CES"))));
        assertThat(result, is(ccmm().withXPath(
            "//ccmm:dataset/ccmm:primary_language/ccmm:label", equalTo("Czech"))));
    }

    @Test
    public void ccmmOtherLanguageKeepsItsNameAligned() throws Exception {
        String result = apply("ccmm.xsl").to(resource(MAIN));
        assertThat(result, is(ccmm().withXPath(
            "//ccmm:dataset/ccmm:other_language/ccmm:iri",
            equalTo("http://publications.europa.eu/resource/authority/language/ENG"))));
        assertThat(result, is(ccmm().withXPath(
            "//ccmm:dataset/ccmm:other_language/ccmm:label", equalTo("English"))));
    }

    @Test
    public void ccmmAcceptsIso6391AndRegionSubtags() throws Exception {
        // stock DSpace stores "en_US"; the register keys on the uppercased 639-3 code
        String result = apply("ccmm.xsl").to(resource(STOCK));
        assertThat(result, is(ccmm().withXPath(
            "//ccmm:dataset/ccmm:primary_language/ccmm:iri",
            equalTo("http://publications.europa.eu/resource/authority/language/ENG"))));
    }

    @Test
    public void ccmmContainsAlternateTitle() throws Exception {
        String result = apply("ccmm.xsl").to(resource(MAIN));
        assertThat(result, is(ccmm().withXPath(
            "//ccmm:dataset/ccmm:alternate_title/ccmm:title", equalTo("CND 2.0"))));
        assertThat(result, is(ccmm().withXPath(
            "//ccmm:dataset/ccmm:alternate_title/ccmm:alternate_title_type/ccmm:iri",
            equalTo("https://vocabs.ccmm.cz/registry/codelist/AlternateTitle/TranslatedTitle"))));
    }

    @Test
    public void ccmmPublisherUsesPublisherRole() throws Exception {
        String result = apply("ccmm.xsl").to(resource(MAIN));
        assertThat(result, is(ccmm().withXPath(
            "//ccmm:dataset/ccmm:qualified_relation[ccmm:role/ccmm:label='Publisher']"
                + "/ccmm:relation/ccmm:organization/ccmm:name",
            equalTo("Charles University, Faculty of Mathematics and Physics, "
                + "Institute of Formal and Applied Linguistics"))));
    }

    @Test
    public void ccmmContributorOtherKeepsItsOwnRole() throws Exception {
        String result = apply("ccmm.xsl").to(resource(MAIN));
        assertThat(result, is(ccmm().withXPath(
            "//ccmm:qualified_relation[ccmm:role/ccmm:label='Other']/ccmm:role/ccmm:iri",
            equalTo("https://vocabs.ccmm.cz/registry/codelist/AgentRole/Contributor/Other"))));
    }

    @Test
    public void ccmmContainsMetadataIdentification() throws Exception {
        String result = apply("ccmm.xsl").to(resource(MAIN));
        assertThat(result, is(ccmm().withXPath(
            "//ccmm:dataset/ccmm:metadata_identification/ccmm:conforms_to_standard/ccmm:iri",
            equalTo("https://schema.ccmm.cz/research-data/1.1.0"))));
        assertThat(result, is(ccmm().withXPath(
            "//ccmm:dataset/ccmm:metadata_identification/ccmm:conforms_to_standard/ccmm:label",
            equalTo("CCMM RD 1.1.0"))));
    }

    @Test
    public void ccmmMetadataRecordCarriesItsOwnOaiIdentifier() throws Exception {
        String result = apply("ccmm.xsl").to(resource(MAIN));
        assertThat(result, is(ccmm().withXPath(
            "//ccmm:dataset/ccmm:metadata_identification/ccmm:iri",
            equalTo("oai:lindat.mff.cuni.cz:11234/1-5678"))));
    }

    @Test
    public void ccmmMetadataRecordHasADataManager() throws Exception {
        // dsv.ttl: the relationship shall contain at least one agent with role "Data Manager"
        String result = apply("ccmm.xsl").to(resource(MAIN));
        assertThat(result, is(ccmm().withXPath(
            "//ccmm:metadata_identification/ccmm:qualified_relation/ccmm:role/ccmm:iri",
            equalTo("https://vocabs.ccmm.cz/registry/codelist/AgentRole/Contributor/DataManager"))));
    }

    @Test
    public void ccmmCanTransformBasicXoaiInput() throws Exception {
        // the default xoai-test1.xml exercises the paths where almost every field is missing
        String result = apply("ccmm.xsl").to(resource("xoai-test1.xml"));
        assertThat(result, is(ccmm().withXPath("//ccmm:title", equalTo("Test Webpage"))));
        assertThat(result, is(ccmm().withXPath(
            "count(//ccmm:dataset/ccmm:identifier)", equalTo("2"))));
        // dc.identifier.issn names its own scheme; it used to be dropped entirely
        assertThat(result, is(ccmm().withXPath(
            "//ccmm:identifier[ccmm:value='123456789']/ccmm:scheme/ccmm:label", equalTo("ISSN"))));
        assertThat(result, is(ccmm().withXPath(
            "count(//ccmm:dataset/ccmm:terms_of_use)", equalTo("1"))));
    }

    // ---- Fallback scenario tests ----

    @Test
    public void ccmmFallbackTitleIsUntitled() throws Exception {
        String result = apply("ccmm.xsl").to(resource(MINIMAL));
        assertThat(result, is(ccmm().withXPath("//ccmm:dataset/ccmm:title", equalTo("Untitled"))));
    }

    @Test
    public void ccmmFallbackPublicationYearIs9999() throws Exception {
        String result = apply("ccmm.xsl").to(resource(MINIMAL));
        assertThat(result, is(ccmm().withXPath("//ccmm:dataset/ccmm:publication_year", equalTo("9999"))));
    }

    @Test
    public void ccmmFallbackSubjectIsUnspecified() throws Exception {
        String result = apply("ccmm.xsl").to(resource(MINIMAL));
        assertThat(result, is(ccmm().withXPath(
            "//ccmm:dataset/ccmm:subject/ccmm:title", equalTo("unspecified"))));
    }

    @Test
    public void ccmmFallbackIdentifierUsesOthersHandle() throws Exception {
        String result = apply("ccmm.xsl").to(resource(MINIMAL));
        assertThat(result, is(ccmm().withXPath(
            "//ccmm:dataset/ccmm:identifier/ccmm:value",
            equalTo("http://hdl.handle.net/99999/test-1"))));
    }

    @Test
    public void ccmmFallbackRepositoryDeclaresItselfUnavailable() throws Exception {
        // an absent repository name is stated as the ANVL ":unav", not invented
        String result = apply("ccmm.xsl").to(resource(MINIMAL));
        assertThat(result, is(ccmm().withXPath(
            "//ccmm:dataset/ccmm:metadata_identification/ccmm:qualified_relation"
                + "/ccmm:relation/ccmm:organization/ccmm:name",
            equalTo(":unav"))));
    }

    @Test
    public void ccmmFallbackLicenseIsEmptyAndRightsTextBecomesTheLabel() throws Exception {
        // With no licence URI, ccmm:license carries the wording as its label rather than a
        // made-up IRI; license_document allows a label without one.
        String result = apply("ccmm.xsl").to(resource(MINIMAL));
        assertThat(result, is(ccmm().withXPath(
            "count(//ccmm:dataset/ccmm:terms_of_use/ccmm:license/ccmm:iri)", equalTo("0"))));
        assertThat(result, is(ccmm().withXPath(
            "//ccmm:dataset/ccmm:terms_of_use/ccmm:license/ccmm:label",
            equalTo("All rights reserved"))));
    }

    @Test
    public void ccmmOriginalRepositoryIsTheRepositoryUrl() throws Exception {
        String result = apply("ccmm.xsl").to(resource(MAIN));
        assertThat(result, is(ccmm().withXPath(
            "//ccmm:metadata_identification/ccmm:original_repository/ccmm:iri",
            equalTo("https://lindat.mff.cuni.cz/repository/"))));
    }

    @Test
    public void ccmmOriginalRepositoryIsNeverTheItemItself() throws Exception {
        // without repository/url there is no repository to name, and a handle URL is not one
        String result = apply("ccmm.xsl").to(resource(STOCK));
        assertThat(result, is(ccmm().withXPath(
            "//ccmm:metadata_identification/ccmm:original_repository/ccmm:iri",
            equalTo("urn:x-ccmm:repository-unknown"))));
        assertThat(result, is(ccmm().withXPath(
            "count(//ccmm:original_repository[contains(ccmm:iri, '123/456')])", equalTo("0"))));
    }

    @Test
    public void ccmmXmlLangFollowsTheXoaiLanguageWrapper() throws Exception {
        String result = apply("ccmm.xsl").to(resource(MAIN));
        assertThat(result, is(ccmm().withXPath(
            "//ccmm:subject/ccmm:title[.='korpus']/@xml:lang", equalTo("cs"))));
        assertThat(result, is(ccmm().withXPath(
            "//ccmm:subject/ccmm:title[.='Korpus']/@xml:lang", equalTo("de"))));
        assertThat(result, is(ccmm().withXPath(
            "//ccmm:subject/ccmm:title[.='linguistics']/@xml:lang", equalTo("en"))));
    }

    // ---- Contact, distribution, relations ----

    @Test
    public void ccmmContainsANamedContactPoint() throws Exception {
        String result = apply("ccmm.xsl").to(resource(MAIN));
        assertThat(result, is(ccmm().withXPath(
            "//ccmm:terms_of_use/ccmm:contact_point/ccmm:person/ccmm:name", equalTo("Rosa, Rudolf"))));
        assertThat(result, is(ccmm().withXPath(
            "//ccmm:terms_of_use/ccmm:contact_point/ccmm:person/ccmm:contact_point/ccmm:email",
            equalTo("rosa@example.org"))));
    }

    @Test
    public void ccmmContainsTheRepositoryContactAsAnOrganization() throws Exception {
        String result = apply("ccmm.xsl").to(resource(MAIN));
        assertThat(result, is(ccmm().withXPath(
            "//ccmm:contact_point/ccmm:organization/ccmm:contact_point/ccmm:email",
            equalTo("lindat-help@ufal.mff.cuni.cz"))));
    }

    @Test
    public void ccmmBitstreamsBecomeDownloadableDistributions() throws Exception {
        String result = apply("ccmm.xsl").to(resource(MAIN));
        assertThat(result, is(ccmm().withXPath(
            "//ccmm:distribution/ccmm:distribution_downloadable_file/ccmm:title",
            equalTo("corpus.txt"))));
        assertThat(result, is(ccmm().withXPath(
            "//ccmm:distribution_downloadable_file/ccmm:byte_size", equalTo("2048"))));
        assertThat(result, is(ccmm().withXPath(
            "//ccmm:distribution_downloadable_file/ccmm:format/ccmm:label", equalTo("text/plain"))));
        assertThat(result, is(ccmm().withXPath(
            "//ccmm:distribution_downloadable_file/ccmm:checksum/ccmm:checksum_value",
            equalTo("ee1c4e448a8f9f838df348dfee1fc11f"))));
    }

    @Test
    public void ccmmRelationQualifiersBecomeTypedRelatedResources() throws Exception {
        // the register has no "Replaces"; superseding is Obsoletes
        String result = apply("ccmm.xsl").to(resource(MAIN));
        assertThat(result, is(ccmm().withXPath(
            "//ccmm:related_resource[ccmm:iri='http://hdl.handle.net/11234/1-1111']"
                + "/ccmm:resource_relation_type/ccmm:iri",
            equalTo("https://vocabs.ccmm.cz/registry/codelist/RelationType/Obsoletes"))));
    }

    @Test
    public void ccmmOwningCollectionBecomesAPartOfRelation() throws Exception {
        String result = apply("ccmm.xsl").to(resource(MAIN));
        assertThat(result, is(ccmm().withXPath(
            "//ccmm:related_resource[ccmm:title='Test Collection']"
                + "/ccmm:resource_relation_type/ccmm:iri",
            equalTo("https://vocabs.ccmm.cz/registry/codelist/RelationType/IsPartOf"))));
    }

    @Test
    public void ccmmProjectPageAndDemoBecomeRelatedResources() throws Exception {
        String result = apply("ccmm.xsl").to(resource(MAIN));
        assertThat(result, is(ccmm().withXPath(
            "count(//ccmm:related_resource[ccmm:resource_url='https://example.org/czech-nlp-dataset'])",
            equalTo("1"))));
        assertThat(result, is(ccmm().withXPath(
            "count(//ccmm:related_resource[ccmm:resource_url='https://example.org/demo'])",
            equalTo("1"))));
    }

    @Test
    public void ccmmContainsLocationForACoveredPlace() throws Exception {
        String result = apply("ccmm.xsl").to(resource(MAIN));
        assertThat(result, is(ccmm().withXPath("//ccmm:location/ccmm:name", equalTo("Praha"))));
        assertThat(result, is(ccmm().withXPath(
            "//ccmm:location/ccmm:relation_type/ccmm:iri",
            equalTo("https://vocabs.ccmm.cz/registry/codelist/LocationRelation/Refers"))));
    }

    @Test
    public void ccmmContainsValidationResult() throws Exception {
        String result = apply("ccmm.xsl").to(resource(MAIN));
        assertThat(result, is(ccmm().withXPath(
            "//ccmm:validation_result/ccmm:label", equalTo("Validated by the repository"))));
    }

    @Test
    public void ccmmFundingReferenceSpellsOutTheFunderAndTheProgramme() throws Exception {
        String result = apply("ccmm.xsl").to(resource(MAIN));
        assertThat(result, is(ccmm().withXPath(
            "//ccmm:funding_reference/ccmm:local_identifier", equalTo("731015"))));
        assertThat(result, is(ccmm().withXPath(
            "//ccmm:funding_reference/ccmm:funding_program", equalTo("H2020"))));
        assertThat(result, is(ccmm().withXPath(
            "//ccmm:funding_reference/ccmm:funder/ccmm:organization/ccmm:name",
            equalTo("European Commission"))));
    }

    @Test
    public void ccmmNonGrantInfoUrisNeverInventAFunder() throws Exception {
        // info:eu-repo/semantics/... is not a grant agreement
        String result = apply("ccmm.xsl").to(resource(STOCK));
        assertThat(result, is(ccmm().withXPath("count(//ccmm:funding_reference)", equalTo("0"))));
    }

    // ---- Controlled vocabularies ----

    @Test
    public void ccmmCreatorRoleUsesCcmmAgentRoleCodelist() throws Exception {
        String result = apply("ccmm.xsl").to(resource(MAIN));
        assertThat(result, is(ccmm().withXPath(
            "//ccmm:dataset/ccmm:qualified_relation[1]/ccmm:role/ccmm:iri",
            equalTo("https://vocabs.ccmm.cz/registry/codelist/AgentRole/Creator"))));
    }

    @Test
    public void ccmmDateTypeUsesTheRegistersOwnLabels() throws Exception {
        // the register's English prefLabel is "Date Issued", not the concept id "Issued"
        String result = apply("ccmm.xsl").to(resource(MAIN));
        assertThat(result, is(ccmm().withXPath(
            "//ccmm:dataset/ccmm:time_reference[1]/ccmm:date_type/ccmm:iri",
            equalTo("https://vocabs.ccmm.cz/registry/codelist/TimeReference/Issued"))));
        assertThat(result, is(ccmm().withXPath(
            "//ccmm:dataset/ccmm:time_reference[1]/ccmm:date_type/ccmm:label[1]",
            equalTo("Date Issued"))));
    }

    @Test
    public void ccmmEmitsNoOffRegisterVocabularyStrings() throws Exception {
        // a closed-set guard: these are the strings and hosts that were wrong before, and no
        // future edit may bring them back
        String result = apply("ccmm.xsl").to(resource(MAIN));
        assertThat(result, is(ccmm().withXPath(
            "count(//ccmm:date_type/ccmm:label[.='Issued' or .='Created'"
                + " or .='Accepted' or .='Available'])", equalTo("0"))));
        assertThat(result, is(ccmm().withXPath(
            "count(//ccmm:iri[contains(., 'model.ccmm.cz')])", equalTo("0"))));
        assertThat(result, is(ccmm().withXPath(
            "count(//ccmm:iri[contains(., 'w3.org/ns/iana')])", equalTo("0"))));
        assertThat(result, is(ccmm().withXPath(
            "count(//ccmm:iri[contains(., 'RelationType/Replaces')"
                + " or contains(., 'RelationType/IsReplacedBy')])", equalTo("0"))));
    }

    // ---- Element order: the CCMM dataset is an xs:sequence ----

    @Test
    public void ccmmEmitsTheDatasetChildrenInSchemaOrder() throws Exception {
        String result = apply("ccmm.xsl").to(resource(MAIN));
        assertThat(result, is(ccmm().withXPath(
            "count(//ccmm:dataset/ccmm:identifier[preceding-sibling::ccmm:title])", equalTo("0"))));
        assertThat(result, is(ccmm().withXPath(
            "count(//ccmm:dataset/ccmm:title[preceding-sibling::ccmm:publication_year])",
            equalTo("0"))));
        assertThat(result, is(ccmm().withXPath(
            "count(//ccmm:dataset/ccmm:publication_year[preceding-sibling::ccmm:time_reference])",
            equalTo("0"))));
        assertThat(result, is(ccmm().withXPath(
            "count(//ccmm:dataset/ccmm:resource_type[preceding-sibling::ccmm:terms_of_use])",
            equalTo("0"))));
        assertThat(result, is(ccmm().withXPath(
            "count(//ccmm:dataset/ccmm:terms_of_use[preceding-sibling::ccmm:subject])",
            equalTo("0"))));
        assertThat(result, is(ccmm().withXPath(
            "count(//ccmm:dataset/ccmm:subject[preceding-sibling::ccmm:location])", equalTo("0"))));
        assertThat(result, is(ccmm().withXPath(
            "count(//ccmm:dataset/ccmm:related_resource[preceding-sibling::ccmm:distribution])",
            equalTo("0"))));
        assertThat(result, is(ccmm().withXPath(
            "count(//ccmm:dataset/ccmm:distribution[preceding-sibling::ccmm:validation_result])",
            equalTo("0"))));
    }

    @Test
    public void ccmmEmitsNestedElementsInSchemaOrder() throws Exception {
        String result = apply("ccmm.xsl").to(resource(MAIN));
        assertThat(result, is(ccmm().withXPath(
            "count(//ccmm:person/ccmm:name[preceding-sibling::ccmm:given_name])", equalTo("0"))));
        assertThat(result, is(ccmm().withXPath(
            "count(//ccmm:subject/ccmm:title[preceding-sibling::ccmm:classification_code])",
            equalTo("0"))));
        assertThat(result, is(ccmm().withXPath(
            "count(//ccmm:terms_of_use/ccmm:access_rights[preceding-sibling::ccmm:license])",
            equalTo("0"))));
        assertThat(result, is(ccmm().withXPath(
            "count(//ccmm:terms_of_use/ccmm:license[preceding-sibling::ccmm:contact_point])",
            equalTo("0"))));
        assertThat(result, is(ccmm().withXPath(
            "count(//ccmm:time_reference/ccmm:temporal_representation"
                + "[preceding-sibling::ccmm:date_type])", equalTo("0"))));
    }

    // ---- CLARIN approximate dates (dc.date.issued = "0000") ----

    @Test
    public void ccmmApproximateDateSetsPublicationYearToTheStartOfTheRange() throws Exception {
        String result = apply("ccmm.xsl").to(resource(APPROX));
        assertThat(result, is(ccmm().withXPath(
            "//ccmm:dataset/ccmm:publication_year", equalTo("1930"))));
    }

    @Test
    public void ccmmApproximateDateRangeBecomesTimeInterval() throws Exception {
        String result = apply("ccmm.xsl").to(resource(APPROX));
        assertThat(result, is(ccmm().withXPath(
            "//ccmm:time_reference/ccmm:temporal_representation/ccmm:time_interval"
                + "/ccmm:beginning/ccmm:date", equalTo("1930-01-01"))));
        assertThat(result, is(ccmm().withXPath(
            "//ccmm:time_reference/ccmm:temporal_representation/ccmm:time_interval"
                + "/ccmm:end/ccmm:date", equalTo("1950-12-31"))));
    }

    @Test
    public void ccmmApproximateDateKeepsOriginalTextVerbatim() throws Exception {
        String result = apply("ccmm.xsl").to(resource(APPROX));
        assertThat(result, is(ccmm().withXPath(
            "//ccmm:time_reference/ccmm:date_information", equalTo("cca 1930-1950"))));
    }

    @Test
    public void ccmmEmitsExactlyOneIssuedAndOneCreatedTimeReference() throws Exception {
        // CCMM requires a Created time reference, and requires publication_year to equal the
        // year of the Issued date - which several Issued references could not satisfy.
        String result = apply("ccmm.xsl").to(resource(MAIN));
        assertThat(result, is(ccmm().withXPath(
            "count(//ccmm:time_reference[ccmm:date_type/ccmm:label='Date Issued'])", equalTo("1"))));
        assertThat(result, is(ccmm().withXPath(
            "count(//ccmm:time_reference[ccmm:date_type/ccmm:label='Date Created'])", equalTo("1"))));
    }

    @Test
    public void ccmmApproximateDateNeverEmitsYearZero() throws Exception {
        String result = apply("ccmm.xsl").to(resource(APPROX));
        assertThat(result, is(ccmm().withXPath(
            "count(//ccmm:time_instant[starts-with(ccmm:date, '0000')])", equalTo("0"))));
    }

    // ---- Dates that no format string can parse ----

    @Test
    public void ccmmEveryEmittedDateIsAValidXsDate() throws Exception {
        String result = apply("ccmm.xsl").to(resource(DATES));
        assertThat(result, is(ccmm().withXPath(
            "count(//ccmm:date[string-length(normalize-space(.)) != 10])", equalTo("0"))));
        assertThat(result, is(ccmm().withXPath(
            "count(//ccmm:date[normalize-space(.) = ''])", equalTo("0"))));
    }

    @Test
    public void ccmmUnparsableDateProducesNoTimeReferenceAtAll() throws Exception {
        // "n.d." is one of two dc.date.available values; only the parsable one survives
        String result = apply("ccmm.xsl").to(resource(DATES));
        assertThat(result, is(ccmm().withXPath(
            "count(//ccmm:time_reference[ccmm:date_type/ccmm:label='Date Available'])",
            equalTo("1"))));
    }

    @Test
    public void ccmmYearOnlyAndYearMonthDatesArePadded() throws Exception {
        String result = apply("ccmm.xsl").to(resource(DATES));
        assertThat(result, is(ccmm().withXPath(
            "//ccmm:time_reference[ccmm:date_type/ccmm:label='Date Issued']"
                + "/ccmm:temporal_representation/ccmm:time_instant/ccmm:date",
            equalTo("2014-01-01"))));
        assertThat(result, is(ccmm().withXPath(
            "//ccmm:time_reference[ccmm:date_type/ccmm:label='Date Accepted']"
                + "/ccmm:temporal_representation/ccmm:time_instant/ccmm:date",
            equalTo("2011-12-01"))));
    }

    @Test
    public void ccmmDateTimeValuesKeepTheirTimeAndUseTheDateTimeElement() throws Exception {
        // time_instant is a choice of date_time or date; truncating a timestamp into date
        // would silently drop the time
        String result = apply("ccmm.xsl").to(resource(MAIN));
        assertThat(result, is(ccmm().withXPath(
            "//ccmm:time_reference[ccmm:date_type/ccmm:label='Date Accepted']"
                + "/ccmm:temporal_representation/ccmm:time_instant/ccmm:date_time",
            equalTo("2025-06-15T10:30:00Z"))));
        assertThat(result, is(ccmm().withXPath(
            "count(//ccmm:time_reference[ccmm:date_type/ccmm:label='Date Accepted']"
                + "/ccmm:temporal_representation/ccmm:time_instant/ccmm:date)",
            equalTo("0"))));
    }

    @Test
    public void ccmmPublicationYearAlwaysMatchesTheIssuedReference() throws Exception {
        // dsv.ttl requires the two to agree; the fixtures carry different years so this can fail
        for (String fixture : new String[] { MAIN, MINIMAL, APPROX, STOCK, DATES }) {
            String result = apply("ccmm.xsl").to(resource(fixture));
            assertThat(fixture, result, is(ccmm().withXPath(
                "substring((//ccmm:time_reference[ccmm:date_type/ccmm:label='Date Issued']"
                    + "//ccmm:date | //ccmm:time_reference[ccmm:date_type/ccmm:label='Date Issued']"
                    + "//ccmm:date_time)[1], 1, 4) = string(//ccmm:dataset/ccmm:publication_year)",
                equalTo("true"))));
        }
    }

    @Test
    public void ccmmPubLicenceWithFilesIsOpenEvenWhenAccessStatusSaysRestricted() throws Exception {
        // 1161 live records are anonymously downloadable while access-status calls them restricted
        String result = apply("ccmm.xsl").to(resource(CLARIN));
        assertThat(result, is(ccmm().withXPath(
            "//ccmm:terms_of_use/ccmm:access_rights/ccmm:iri",
            equalTo("http://purl.org/coar/access_right/c_abf2"))));
    }

    @Test
    public void ccmmEnumeratedYearsNeverBecomeAContinuousInterval() throws Exception {
        // "1920, 1932" names two years; the source never claims the years between them
        String result = apply("ccmm.xsl").to(resource(ENUM));
        assertThat(result, is(ccmm().withXPath("count(//ccmm:time_interval)", equalTo("0"))));
        assertThat(result, is(ccmm().withXPath(
            "//ccmm:dataset/ccmm:publication_year", equalTo("1932"))));
    }

    @Test
    public void ccmmCreatedUsesTheStatedYearOfCreation() throws Exception {
        // local.additional.metadata states field_year; the deposit year is not a creation date
        String result = apply("ccmm.xsl").to(resource(ENUM));
        assertThat(result, is(ccmm().withXPath(
            "//ccmm:time_reference[ccmm:date_type/ccmm:label='Date Created']"
                + "/ccmm:temporal_representation/ccmm:time_instant/ccmm:date",
            equalTo("1787-01-01"))));
    }

    @Test
    public void ccmmTitleIsNeverAConcatenationOfSeveralValues() throws Exception {
        String result = apply("ccmm.xsl").to(resource(STOCK));
        assertThat(result, is(ccmm().withXPath(
            "//ccmm:dataset/ccmm:title", equalTo("A Stock DSpace Item"))));
        assertThat(result, is(ccmm().withXPath(
            "count(//ccmm:dataset/ccmm:title)", equalTo("1"))));
        assertThat(result, is(ccmm().withXPath(
            "//ccmm:dataset/ccmm:alternate_title/ccmm:title",
            equalTo("Second Title Nobody Entered As One"))));
    }

    @Test
    public void ccmmUnparsableAccessionDateProducesNoTimeReference() throws Exception {
        String result = apply("ccmm.xsl").to(resource(DATES));
        assertThat(result, is(ccmm().withXPath(
            "count(//ccmm:time_reference[ccmm:date_type/ccmm:label='Date Accepted'])",
            equalTo("1"))));
    }

    @Test
    public void ccmmUriIdentifierSchemeUsesTheIanaRegistry() throws Exception {
        String result = apply("ccmm.xsl").to(resource(MAIN));
        assertThat(result, is(ccmm().withXPath(
            "//ccmm:related_resource[ccmm:resource_url='https://example.org/czech-nlp-dataset']"
                + "/ccmm:identifier/ccmm:scheme/ccmm:iri",
            equalTo("https://www.iana.org/assignments/uri-schemes"))));
    }

    @Test
    public void ccmmAcademicLicenceIsRestrictedEvenWhenAccessStatusSaysOpen() throws Exception {
        // the CLARIN identity form gates the download; open.access on the bitstream does not
        String result = apply("ccmm.xsl").to(resource(ACA));
        assertThat(result, is(ccmm().withXPath(
            "//ccmm:terms_of_use/ccmm:access_rights/ccmm:iri",
            equalTo("http://purl.org/coar/access_right/c_16ec"))));
    }

    @Test
    public void ccmmRestrictedAccessFlagOutranksAPubLicence() throws Exception {
        // ItemUtils sets others/restrictedAccess when a bitstream licence demands the identity
        // form; that gate outranks the PUB licence category
        String result = apply("ccmm.xsl").to(resource(FLAG));
        assertThat(result, is(ccmm().withXPath(
            "//ccmm:terms_of_use/ccmm:access_rights/ccmm:iri",
            equalTo("http://purl.org/coar/access_right/c_16ec"))));
        assertThat(result, is(ccmm().withXPath(
            "//ccmm:terms_of_use/ccmm:access_rights/ccmm:label",
            equalTo("restricted access"))));
    }

    @Test
    public void ccmmEmbargoedAccessStatusMapsToTheCoarEmbargoTerm() throws Exception {
        // with no CLARIN licence category present, others/access-status decides
        String result = apply("ccmm.xsl").to(resource(EMBARGO));
        assertThat(result, is(ccmm().withXPath(
            "//ccmm:terms_of_use/ccmm:access_rights/ccmm:iri",
            equalTo("http://purl.org/coar/access_right/c_f1cf"))));
        assertThat(result, is(ccmm().withXPath(
            "//ccmm:terms_of_use/ccmm:access_rights/ccmm:label",
            equalTo("embargoes access"))));
    }

    @Test
    public void ccmmAlwaysEmitsAtLeastOneIdentifier() throws Exception {
        // identifier is 1..unbounded and this record carries none of the usual sources
        String result = apply("ccmm.xsl").to(resource(BARE));
        assertThat(result, is(ccmm().withXPath(
            "count(//ccmm:dataset/ccmm:identifier)", equalTo("1"))));
        assertThat(result, is(ccmm().withXPath(
            "//ccmm:dataset/ccmm:identifier/ccmm:value",
            equalTo("oai:repo.example.org:no-handle-1"))));
        assertThat(result, is(ccmm().withXPath(
            "//ccmm:dataset/ccmm:identifier/ccmm:scheme/ccmm:iri",
            equalTo("https://www.iana.org/assignments/uri-schemes"))));
    }

    private XmlMatcherBuilder ccmm() {
        // XmlMatcherBuilder's NamespaceContext is a plain map, so the reserved "xml" prefix has
        // to be registered explicitly or JAXP cannot compile an @xml:lang step.
        return xml()
            .withNamespace("ccmm", CCMM_NS)
            .withNamespace("xml", XML_NS);
    }
}
