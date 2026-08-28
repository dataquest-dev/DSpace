<?xml version="1.0" encoding="UTF-8" ?>
<!--
    CCMM (Czech Common Metadata Model) crosswalk for OAI-PMH export.
    Produces CCMM 1.1.0 dataset XML from the XOAI internal DSpace metadata format.

    CCMM schema: https://techlib.github.io/CCMM/dataset/schema.xsd
    Namespace:   https://schema.ccmm.cz/research-data/1.1
    metadataPrefix: ccmm-xml

    See: https://github.com/techlib/CCMM
         https://github.com/ufal/clarin-dspace/issues/1145
-->
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:doc="http://www.lyncode.com/xoai"
                xmlns:ccmm="https://schema.ccmm.cz/research-data/1.1"
                exclude-result-prefixes="doc"
                version="2.0">

    <xsl:output omit-xml-declaration="yes" method="xml" indent="yes" />

    <!-- ============================================================ -->
    <!-- Fallback constants: extracted for maintainability             -->
    <!-- ============================================================ -->
    <xsl:variable name="FALLBACK_REPOSITORY_NAME" select="'Unknown Repository'"/>
    <xsl:variable name="FALLBACK_REPOSITORY_URL" select="'http://unknown.repository'"/>
    <xsl:variable name="FALLBACK_TITLE" select="'Untitled'"/>
    <xsl:variable name="FALLBACK_SUBJECT" select="'unspecified'"/>
    <xsl:variable name="FALLBACK_PUBLICATION_YEAR" select="'9999'"/>

    <!-- ============================================================ -->
    <!-- Controlled vocabularies.                                      -->
    <!-- The registers below are the ones CCMM 1.1.0 names in its own  -->
    <!-- specification (en/dsv.ttl, skos:scopeNote per term):          -->
    <!--   ResourceType    -> vocabularies.coar-repositories.org       -->
    <!--   AccessRights    -> purl.org/coar/access_right/              -->
    <!--   AgentRole       -> vocabs.ccmm.cz/registry/codelist/AgentRole/       -->
    <!--   DateType        -> vocabs.ccmm.cz/registry/codelist/TimeReference/   -->
    <!--   DescriptionType -> vocabs.ccmm.cz/registry/codelist/DescriptionType/ -->
    <!--   LanguageSystem  -> publications.europa.eu/resource/authority/language -->
    <!-- ============================================================ -->

    <!-- ============================================================ -->
    <!-- Dates.                                                        -->
    <!-- CLARIN-DSpace records an unknown issue date as the literal    -->
    <!-- "0000" and puts the real information into                     -->
    <!-- local.approximateDate.issued, either as a range               -->
    <!-- ("cca 1930-1965") or as an enumeration ("1920, 1932").        -->
    <!-- The precedence below is the one LINDAT applies to itself in   -->
    <!-- ClarinDateService.composeItemDate (dspace-angular): when the  -->
    <!-- local field is present it REPLACES dc.date.issued.  The       -->
    <!-- original string is never rewritten, it travels along in       -->
    <!-- ccmm:date_information.                                        -->
    <!-- ============================================================ -->
    <xsl:variable name="issuedAll"
        select="/doc:metadata/doc:element[@name='dc']/doc:element[@name='date']/doc:element[@name='issued']/doc:element/doc:field[@name='value']"/>
    <xsl:variable name="issuedFirst" select="$issuedAll[not(starts-with(normalize-space(.), '0000'))][1]"/>
    <xsl:variable name="approxRaw"
        select="normalize-space(string((/doc:metadata/doc:element[@name='local']/doc:element[@name='approximateDate']/doc:element[@name='issued']/doc:element/doc:field[@name='value'])[1]))"/>
    <xsl:variable name="approxYears" select="tokenize($approxRaw, '[^0-9]+')[string-length(.) = 4]"/>
    <xsl:variable name="approxMin" select="format-number(min(for $y in $approxYears return number($y)), '0000')"/>
    <xsl:variable name="approxMax" select="format-number(max(for $y in $approxYears return number($y)), '0000')"/>
    <!-- dc.language.iso, falling back to dc.language; only well-formed 3-letter codes survive -->
    <xsl:variable name="languageRaw"
        select="if (/doc:metadata/doc:element[@name='dc']/doc:element[@name='language']/doc:element[@name='iso']/doc:element/doc:field[@name='value'])
                then /doc:metadata/doc:element[@name='dc']/doc:element[@name='language']/doc:element[@name='iso']/doc:element/doc:field[@name='value']
                else /doc:metadata/doc:element[@name='dc']/doc:element[@name='language']/doc:element/doc:field[@name='value']"/>
    <xsl:variable name="languageCodes"
        select="distinct-values(for $l in $languageRaw
                                return lower-case(normalize-space($l)))[matches(., '^[a-z]{3}$')]"/>
    <xsl:variable name="accessionedAll"
        select="/doc:metadata/doc:element[@name='dc']/doc:element[@name='date']/doc:element[@name='accessioned']/doc:element/doc:field[@name='value']"/>
    <xsl:variable name="availableAll"
        select="/doc:metadata/doc:element[@name='dc']/doc:element[@name='date']/doc:element[@name='available']/doc:element/doc:field[@name='value']"/>
    <!-- the single year that publication_year and the Issued time reference must agree on -->
    <xsl:variable name="publicationYear">
        <xsl:choose>
            <xsl:when test="count($approxYears) &gt; 0"><xsl:value-of select="$approxMin"/></xsl:when>
            <xsl:when test="$issuedFirst"><xsl:value-of select="substring(normalize-space($issuedFirst), 1, 4)"/></xsl:when>
            <xsl:when test="$accessionedAll[1]"><xsl:value-of select="substring(normalize-space($accessionedAll[1]), 1, 4)"/></xsl:when>
            <xsl:otherwise><xsl:value-of select="$FALLBACK_PUBLICATION_YEAR"/></xsl:otherwise>
        </xsl:choose>
    </xsl:variable>

    <!-- Main template -->
    <xsl:template match="/">
        <ccmm:dataset xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                      xsi:schemaLocation="https://schema.ccmm.cz/research-data/1.1 https://techlib.github.io/CCMM/dataset/schema.xsd">

            <!-- metadata_identification (required, unbounded) -->
            <xsl:call-template name="MetadataIdentification"/>

            <!-- identifier (required, unbounded) -->
            <xsl:call-template name="Identifiers"/>

            <!-- version (optional) -->
            <xsl:call-template name="Version"/>

            <!-- title (required) -->
            <xsl:call-template name="Title"/>

            <!-- alternate_title (optional, unbounded) -->
            <xsl:call-template name="AlternateTitles"/>

            <!-- qualified_relation (optional, unbounded) - creators, contributors -->
            <xsl:call-template name="QualifiedRelations"/>

            <!-- publication_year (required) -->
            <xsl:call-template name="PublicationYear"/>

            <!-- time_reference (required, unbounded) -->
            <xsl:call-template name="TimeReferences"/>

            <!-- resource_type (optional) -->
            <xsl:call-template name="ResourceType"/>

            <!-- primary_language (optional) -->
            <xsl:call-template name="PrimaryLanguage"/>

            <!-- other_language (optional, unbounded) -->
            <xsl:call-template name="OtherLanguages"/>

            <!-- terms_of_use (required) -->
            <xsl:call-template name="TermsOfUse"/>

            <!-- subject (required, unbounded) -->
            <xsl:call-template name="Subjects"/>

            <!-- description (optional, unbounded) -->
            <xsl:call-template name="Descriptions"/>

            <!-- funding_reference (optional, unbounded) -->
            <xsl:call-template name="FundingReferences"/>

            <!-- related_resource (optional, unbounded) -->
            <xsl:call-template name="RelatedResources"/>

        </ccmm:dataset>
    </xsl:template>

    <!-- ============================================================ -->
    <!-- metadata_identification                                       -->
    <!-- ============================================================ -->
    <xsl:template name="MetadataIdentification">
        <ccmm:metadata_identification>
            <!-- qualified_relation for metadata record - use repository as curator -->
            <ccmm:qualified_relation>
                <ccmm:relation>
                    <ccmm:organization>
                        <ccmm:name>
                            <xsl:choose>
                                <xsl:when test="doc:metadata/doc:element[@name='repository']/doc:field[@name='name']">
                                    <xsl:value-of select="doc:metadata/doc:element[@name='repository']/doc:field[@name='name']"/>
                                </xsl:when>
                                <xsl:otherwise><xsl:value-of select="$FALLBACK_REPOSITORY_NAME"/></xsl:otherwise>
                            </xsl:choose>
                        </ccmm:name>
                    </ccmm:organization>
                </ccmm:relation>
                <ccmm:role>
                    <ccmm:iri>https://vocabs.ccmm.cz/registry/codelist/AgentRole/Contributor/DataManager</ccmm:iri>
                    <ccmm:label xml:lang="en">Data Manager</ccmm:label>
                </ccmm:role>
            </ccmm:qualified_relation>
            <!-- conforms_to_standard -->
            <ccmm:conforms_to_standard>
                <ccmm:iri>https://schema.ccmm.cz/research-data/1.1</ccmm:iri>
                <ccmm:label xml:lang="en">CCMM 1.1</ccmm:label>
            </ccmm:conforms_to_standard>
            <!-- original_repository -->
            <ccmm:original_repository>
                <ccmm:iri>
                    <xsl:choose>
                        <xsl:when test="doc:metadata/doc:element[@name='dc']/doc:element[@name='identifier']/doc:element[@name='uri']/doc:element/doc:field[@name='value']">
                            <xsl:variable name="uri" select="(doc:metadata/doc:element[@name='dc']/doc:element[@name='identifier']/doc:element[@name='uri']/doc:element/doc:field[@name='value'])[1]"/>
                            <!--
                                Extract repository base URL from Handle URI.
                                Assumes DSpace-style URLs like https://repo.example.org/handle/123/456.
                                Also handles hdl.handle.net URIs as-is.
                            -->
                            <xsl:choose>
                                <xsl:when test="contains($uri, '/handle/')">
                                    <xsl:value-of select="substring-before($uri, '/handle/')"/>
                                </xsl:when>
                                <xsl:otherwise>
                                    <xsl:value-of select="$uri"/>
                                </xsl:otherwise>
                            </xsl:choose>
                        </xsl:when>
                        <xsl:otherwise><xsl:value-of select="$FALLBACK_REPOSITORY_URL"/></xsl:otherwise>
                    </xsl:choose>
                </ccmm:iri>
                <xsl:if test="doc:metadata/doc:element[@name='repository']/doc:field[@name='name']">
                    <ccmm:label xml:lang="en">
                        <xsl:value-of select="doc:metadata/doc:element[@name='repository']/doc:field[@name='name']"/>
                    </ccmm:label>
                </xsl:if>
            </ccmm:original_repository>
        </ccmm:metadata_identification>
    </xsl:template>

    <!-- ============================================================ -->
    <!-- identifier                                                    -->
    <!-- ============================================================ -->
    <xsl:template name="Identifiers">
        <!-- Handle identifier -->
        <xsl:for-each select="doc:metadata/doc:element[@name='dc']/doc:element[@name='identifier']/doc:element[@name='uri']/doc:element/doc:field[@name='value']">
            <xsl:if test="contains(., 'hdl.handle.net') or contains(., '/handle/')">
                <ccmm:identifier>
                    <ccmm:value><xsl:value-of select="."/></ccmm:value>
                    <ccmm:scheme>
                        <ccmm:iri>https://hdl.handle.net/</ccmm:iri>
                        <ccmm:label xml:lang="en">Handle</ccmm:label>
                    </ccmm:scheme>
                </ccmm:identifier>
            </xsl:if>
        </xsl:for-each>
        <!-- DOI identifier -->
        <xsl:for-each select="doc:metadata/doc:element[@name='dc']/doc:element[@name='identifier']/doc:element[@name='doi']/doc:element/doc:field[@name='value']">
            <ccmm:identifier>
                <ccmm:value><xsl:value-of select="."/></ccmm:value>
                <ccmm:scheme>
                    <ccmm:iri>https://doi.org/</ccmm:iri>
                    <ccmm:label xml:lang="en">DOI</ccmm:label>
                </ccmm:scheme>
            </ccmm:identifier>
        </xsl:for-each>
        <!-- Other URI identifiers (non-handle) -->
        <xsl:for-each select="doc:metadata/doc:element[@name='dc']/doc:element[@name='identifier']/doc:element[@name='uri']/doc:element/doc:field[@name='value']">
            <xsl:if test="not(contains(., 'hdl.handle.net')) and not(contains(., '/handle/'))">
                <ccmm:identifier>
                    <ccmm:value><xsl:value-of select="."/></ccmm:value>
                    <ccmm:scheme>
                        <ccmm:iri>https://www.w3.org/ns/iana/uri-schemes</ccmm:iri>
                        <ccmm:label xml:lang="en">URI</ccmm:label>
                    </ccmm:scheme>
                </ccmm:identifier>
            </xsl:if>
        </xsl:for-each>
        <!--
            Fallback: use handle from 'others' section if no Handle or DOI
            was found from dc.identifier.uri/doi. This covers cases where
            dc.identifier.uri exists but contains non-Handle URIs.
        -->
        <xsl:if test="not(doc:metadata/doc:element[@name='dc']/doc:element[@name='identifier']/doc:element[@name='uri']/doc:element/doc:field[@name='value'][contains(., 'hdl.handle.net') or contains(., '/handle/')]) and not(doc:metadata/doc:element[@name='dc']/doc:element[@name='identifier']/doc:element[@name='doi']/doc:element/doc:field[@name='value'])">
            <xsl:if test="doc:metadata/doc:element[@name='others']/doc:field[@name='handle']">
                <ccmm:identifier>
                    <ccmm:value>
                        <xsl:value-of select="concat('http://hdl.handle.net/', doc:metadata/doc:element[@name='others']/doc:field[@name='handle'])"/>
                    </ccmm:value>
                    <ccmm:scheme>
                        <ccmm:iri>https://hdl.handle.net/</ccmm:iri>
                        <ccmm:label xml:lang="en">Handle</ccmm:label>
                    </ccmm:scheme>
                </ccmm:identifier>
            </xsl:if>
        </xsl:if>
    </xsl:template>

    <!-- ============================================================ -->
    <!-- version (optional)                                            -->
    <!-- ============================================================ -->
    <xsl:template name="Version">
        <xsl:if test="doc:metadata/doc:element[@name='dc']/doc:element[@name='description']/doc:element[@name='version']/doc:element/doc:field[@name='value']">
            <ccmm:version>
                <xsl:value-of select="doc:metadata/doc:element[@name='dc']/doc:element[@name='description']/doc:element[@name='version']/doc:element/doc:field[@name='value']"/>
            </ccmm:version>
        </xsl:if>
    </xsl:template>

    <!-- ============================================================ -->
    <!-- title (required)                                              -->
    <!-- ============================================================ -->
    <xsl:template name="Title">
        <ccmm:title>
            <xsl:choose>
                <xsl:when test="doc:metadata/doc:element[@name='dc']/doc:element[@name='title']/doc:element/doc:field[@name='value']">
                    <xsl:value-of select="doc:metadata/doc:element[@name='dc']/doc:element[@name='title']/doc:element/doc:field[@name='value']"/>
                </xsl:when>
                <xsl:otherwise><xsl:value-of select="$FALLBACK_TITLE"/></xsl:otherwise>
            </xsl:choose>
        </ccmm:title>
    </xsl:template>

    <!-- ============================================================ -->
    <!-- alternate_title (optional)                                    -->
    <!-- ============================================================ -->
    <xsl:template name="AlternateTitles">
        <!-- dc.title.alternative mapped to alternate_title -->
        <xsl:for-each select="doc:metadata/doc:element[@name='dc']/doc:element[@name='title']/doc:element[@name='alternative']/doc:element/doc:field[@name='value']">
            <ccmm:alternate_title>
                <ccmm:title xml:lang="en">
                    <xsl:value-of select="."/>
                </ccmm:title>
            </ccmm:alternate_title>
        </xsl:for-each>
    </xsl:template>

    <!-- ============================================================ -->
    <!-- qualified_relation (creators and contributors)                -->
    <!-- ============================================================ -->
    <xsl:template name="QualifiedRelations">
        <!-- dc.contributor.author -> Creator role -->
        <xsl:for-each select="doc:metadata/doc:element[@name='dc']/doc:element[@name='contributor']/doc:element[@name='author']/doc:element/doc:field[@name='value']">
            <ccmm:qualified_relation>
                <ccmm:relation>
                    <ccmm:person>
                        <ccmm:name><xsl:value-of select="."/></ccmm:name>
                    </ccmm:person>
                </ccmm:relation>
                <ccmm:role>
                    <ccmm:iri>https://vocabs.ccmm.cz/registry/codelist/AgentRole/Creator</ccmm:iri>
                    <ccmm:label xml:lang="en">Creator</ccmm:label>
                </ccmm:role>
            </ccmm:qualified_relation>
        </xsl:for-each>
        <!-- dc.creator -> Creator role -->
        <xsl:for-each select="doc:metadata/doc:element[@name='dc']/doc:element[@name='creator']/doc:element/doc:field[@name='value']">
            <ccmm:qualified_relation>
                <ccmm:relation>
                    <ccmm:person>
                        <ccmm:name><xsl:value-of select="."/></ccmm:name>
                    </ccmm:person>
                </ccmm:relation>
                <ccmm:role>
                    <ccmm:iri>https://vocabs.ccmm.cz/registry/codelist/AgentRole/Creator</ccmm:iri>
                    <ccmm:label xml:lang="en">Creator</ccmm:label>
                </ccmm:role>
            </ccmm:qualified_relation>
        </xsl:for-each>
        <!-- dc.contributor.editor -> Editor role -->
        <xsl:for-each select="doc:metadata/doc:element[@name='dc']/doc:element[@name='contributor']/doc:element[@name='editor']/doc:element/doc:field[@name='value']">
            <ccmm:qualified_relation>
                <ccmm:relation>
                    <ccmm:person>
                        <ccmm:name><xsl:value-of select="."/></ccmm:name>
                    </ccmm:person>
                </ccmm:relation>
                <ccmm:role>
                    <ccmm:iri>https://vocabs.ccmm.cz/registry/codelist/AgentRole/Contributor/Editor</ccmm:iri>
                    <ccmm:label xml:lang="en">Editor</ccmm:label>
                </ccmm:role>
            </ccmm:qualified_relation>
        </xsl:for-each>
        <!-- dc.contributor.other -> Other role -->
        <xsl:for-each select="doc:metadata/doc:element[@name='dc']/doc:element[@name='contributor']/doc:element[@name='other']/doc:element/doc:field[@name='value']">
            <ccmm:qualified_relation>
                <ccmm:relation>
                    <ccmm:person>
                        <ccmm:name><xsl:value-of select="."/></ccmm:name>
                    </ccmm:person>
                </ccmm:relation>
                <ccmm:role>
                    <ccmm:iri>https://vocabs.ccmm.cz/registry/codelist/AgentRole/Contributor/Other</ccmm:iri>
                    <ccmm:label xml:lang="en">Other</ccmm:label>
                </ccmm:role>
            </ccmm:qualified_relation>
        </xsl:for-each>
        <!--
            dc.publisher -> Publisher.  Publisher is a top-level role in the CCMM AgentRole
            codelist alongside Creator and Contributor; Distributor exists only below
            Contributor/ and would demote the publisher to a kind of contributor.
        -->
        <xsl:for-each select="doc:metadata/doc:element[@name='dc']/doc:element[@name='publisher']/doc:element/doc:field[@name='value']">
            <ccmm:qualified_relation>
                <ccmm:relation>
                    <ccmm:organization>
                        <ccmm:name><xsl:value-of select="."/></ccmm:name>
                    </ccmm:organization>
                </ccmm:relation>
                <ccmm:role>
                    <ccmm:iri>https://vocabs.ccmm.cz/registry/codelist/AgentRole/Publisher</ccmm:iri>
                    <ccmm:label xml:lang="en">Publisher</ccmm:label>
                </ccmm:role>
            </ccmm:qualified_relation>
        </xsl:for-each>
    </xsl:template>

    <!-- ============================================================ -->
    <!-- publication_year (required)                                    -->
    <!-- ============================================================ -->
    <xsl:template name="PublicationYear">
        <ccmm:publication_year><xsl:value-of select="$publicationYear"/></ccmm:publication_year>
    </xsl:template>

    <!-- ============================================================ -->
    <!-- time_reference (required, unbounded)                          -->
    <!-- ============================================================ -->
    <!--
        The temporal representation shared by the Issued and Created time references.
        An approximate date wins over dc.date.issued; a range becomes a time_interval,
        a single year an instant.
    -->
    <xsl:template name="PrimaryTemporalRepresentation">
        <ccmm:temporal_representation>
            <xsl:choose>
                <xsl:when test="count($approxYears) &gt; 0 and $approxMin != $approxMax">
                    <ccmm:time_interval>
                        <ccmm:beginning>
                            <ccmm:date><xsl:value-of select="concat($approxMin, '-01-01')"/></ccmm:date>
                        </ccmm:beginning>
                        <ccmm:end>
                            <ccmm:date><xsl:value-of select="concat($approxMax, '-12-31')"/></ccmm:date>
                        </ccmm:end>
                    </ccmm:time_interval>
                </xsl:when>
                <xsl:when test="count($approxYears) &gt; 0">
                    <ccmm:time_instant>
                        <ccmm:date><xsl:value-of select="concat($approxMin, '-01-01')"/></ccmm:date>
                    </ccmm:time_instant>
                </xsl:when>
                <xsl:when test="$issuedFirst">
                    <ccmm:time_instant>
                        <xsl:call-template name="FormatDate">
                            <xsl:with-param name="dateStr" select="$issuedFirst"/>
                        </xsl:call-template>
                    </ccmm:time_instant>
                </xsl:when>
                <xsl:when test="$accessionedAll[1]">
                    <ccmm:time_instant>
                        <xsl:call-template name="FormatDate">
                            <xsl:with-param name="dateStr" select="$accessionedAll[1]"/>
                        </xsl:call-template>
                    </ccmm:time_instant>
                </xsl:when>
                <xsl:otherwise>
                    <ccmm:time_instant>
                        <ccmm:date><xsl:value-of select="concat($FALLBACK_PUBLICATION_YEAR, '-01-01')"/></ccmm:date>
                    </ccmm:time_instant>
                </xsl:otherwise>
            </xsl:choose>
        </ccmm:temporal_representation>
    </xsl:template>

    <!-- ============================================================ -->
    <!-- time_reference (required, unbounded)                          -->
    <!-- ============================================================ -->
    <xsl:template name="TimeReferences">
        <!--
            Exactly one Issued reference: CCMM requires publication_year and the year of the
            Issued date to be the same, which several Issued references could not satisfy.
        -->
        <ccmm:time_reference>
            <xsl:call-template name="PrimaryTemporalRepresentation"/>
            <ccmm:date_type>
                <ccmm:iri>https://vocabs.ccmm.cz/registry/codelist/TimeReference/Issued</ccmm:iri>
                <ccmm:label xml:lang="en">Issued</ccmm:label>
            </ccmm:date_type>
            <xsl:if test="$approxRaw != ''">
                <ccmm:date_information xml:lang="cs"><xsl:value-of select="$approxRaw"/></ccmm:date_information>
            </xsl:if>
        </ccmm:time_reference>
        <!--
            CCMM requires at least one time reference of type Created.  DSpace holds no
            creation date, so the same value is reused: for a deposited resource the date the
            depositor states is the closest available statement about when it came into being.
        -->
        <ccmm:time_reference>
            <xsl:call-template name="PrimaryTemporalRepresentation"/>
            <ccmm:date_type>
                <ccmm:iri>https://vocabs.ccmm.cz/registry/codelist/TimeReference/Created</ccmm:iri>
                <ccmm:label xml:lang="en">Created</ccmm:label>
            </ccmm:date_type>
        </ccmm:time_reference>
        <!-- dc.date.accessioned -> Accepted -->
        <xsl:for-each select="$accessionedAll">
            <ccmm:time_reference>
                <ccmm:temporal_representation>
                    <ccmm:time_instant>
                        <xsl:call-template name="FormatDate">
                            <xsl:with-param name="dateStr" select="."/>
                        </xsl:call-template>
                    </ccmm:time_instant>
                </ccmm:temporal_representation>
                <ccmm:date_type>
                    <ccmm:iri>https://vocabs.ccmm.cz/registry/codelist/TimeReference/Accepted</ccmm:iri>
                    <ccmm:label xml:lang="en">Accepted</ccmm:label>
                </ccmm:date_type>
            </ccmm:time_reference>
        </xsl:for-each>
        <!-- dc.date.available -> Available -->
        <xsl:for-each select="$availableAll">
            <ccmm:time_reference>
                <ccmm:temporal_representation>
                    <ccmm:time_instant>
                        <xsl:call-template name="FormatDate">
                            <xsl:with-param name="dateStr" select="."/>
                        </xsl:call-template>
                    </ccmm:time_instant>
                </ccmm:temporal_representation>
                <ccmm:date_type>
                    <ccmm:iri>https://vocabs.ccmm.cz/registry/codelist/TimeReference/Available</ccmm:iri>
                    <ccmm:label xml:lang="en">Available</ccmm:label>
                </ccmm:date_type>
            </ccmm:time_reference>
        </xsl:for-each>
    </xsl:template>

    <!-- ============================================================ -->
    <!-- resource_type (optional)                                      -->
    <!-- ============================================================ -->
    <xsl:template name="ResourceType">
        <!--
            dc.type -> COAR Resource Type vocabulary, which CCMM names for this term.
            LINDAT values come from META-SHARE: corpus / lexicalConceptualResource /
            languageDescription are data sets, toolService is software, clip is video.
        -->
        <xsl:variable name="dctype" select="normalize-space((doc:metadata/doc:element[@name='dc']/doc:element[@name='type']/doc:element/doc:field[@name='value'])[1])"/>
        <xsl:if test="$dctype != ''">
            <ccmm:resource_type>
                <xsl:choose>
                    <xsl:when test="$dctype = 'corpus' or $dctype = 'lexicalConceptualResource'
                                 or $dctype = 'languageDescription' or $dctype = 'Dataset'
                                 or $dctype = 'dataset' or $dctype = 'Datafile/dataset'
                                 or $dctype = 'Spreadsheet' or $dctype = 'language test'">
                        <ccmm:iri>http://purl.org/coar/resource_type/c_ddb1</ccmm:iri>
                        <ccmm:label xml:lang="en">dataset</ccmm:label>
                    </xsl:when>
                    <xsl:when test="$dctype = 'toolService' or $dctype = 'Software' or $dctype = 'software'">
                        <ccmm:iri>http://purl.org/coar/resource_type/c_5ce6</ccmm:iri>
                        <ccmm:label xml:lang="en">software</ccmm:label>
                    </xsl:when>
                    <xsl:when test="$dctype = 'clip' or $dctype = 'Video' or $dctype = 'video'">
                        <ccmm:iri>http://purl.org/coar/resource_type/c_12ce</ccmm:iri>
                        <ccmm:label xml:lang="en">video</ccmm:label>
                    </xsl:when>
                    <xsl:when test="$dctype = 'Sound' or $dctype = 'sound' or $dctype = 'Audio' or $dctype = 'audio'">
                        <ccmm:iri>http://purl.org/coar/resource_type/c_18cc</ccmm:iri>
                        <ccmm:label xml:lang="en">sound</ccmm:label>
                    </xsl:when>
                    <xsl:when test="$dctype = 'Image' or $dctype = 'image' or $dctype = 'IMAGE'">
                        <ccmm:iri>http://purl.org/coar/resource_type/c_ecc8</ccmm:iri>
                        <ccmm:label xml:lang="en">still image</ccmm:label>
                    </xsl:when>
                    <xsl:when test="$dctype = 'Text' or $dctype = 'text' or $dctype = 'Sentences'
                                 or $dctype = 'Transcribed document'">
                        <ccmm:iri>http://purl.org/coar/resource_type/c_18cf</ccmm:iri>
                        <ccmm:label xml:lang="en">text</ccmm:label>
                    </xsl:when>
                    <xsl:when test="$dctype = 'Music notation'">
                        <ccmm:iri>http://purl.org/coar/resource_type/c_18cw</ccmm:iri>
                        <ccmm:label xml:lang="en">musical notation</ccmm:label>
                    </xsl:when>
                    <xsl:when test="$dctype = 'bibliography'">
                        <ccmm:iri>http://purl.org/coar/resource_type/c_86bc</ccmm:iri>
                        <ccmm:label xml:lang="en">bibliography</ccmm:label>
                    </xsl:when>
                    <xsl:when test="$dctype = 'onlineCourse'">
                        <ccmm:iri>http://purl.org/coar/resource_type/c_e059</ccmm:iri>
                        <ccmm:label xml:lang="en">learning object</ccmm:label>
                    </xsl:when>
                    <xsl:when test="$dctype = 'dashboard'">
                        <ccmm:iri>http://purl.org/coar/resource_type/c_e9a0</ccmm:iri>
                        <ccmm:label xml:lang="en">interactive resource</ccmm:label>
                    </xsl:when>
                    <xsl:otherwise>
                        <ccmm:iri>http://purl.org/coar/resource_type/c_1843</ccmm:iri>
                        <ccmm:label xml:lang="en">other</ccmm:label>
                    </xsl:otherwise>
                </xsl:choose>
            </ccmm:resource_type>
        </xsl:if>
    </xsl:template>

    <!-- ============================================================ -->
    <!-- primary_language (optional)                                    -->
    <!-- ============================================================ -->
    <xsl:template name="PrimaryLanguage">
        <!--
            CCMM: "Use IRI identifier from the register
            http://publications.europa.eu/resource/authority/language."  The register keys on the
            uppercased 3-letter ISO 639-3 code, so the value is emitted only when it has exactly
            that shape; anything else (free text such as "English; Czech") would produce an IRI
            that does not resolve, and language_system is optional, so the element is omitted.
            No label: only a minority of the codes LINDAT uses carry an English label in the
            register, and the code itself is already the last segment of the IRI.
            AJP and HBS are marked deprecated in the register but are still real, resolvable
            concepts; they are emitted unchanged rather than silently remapped (APC is a
            broadening of AJP, and HBS has no single successor).
        -->
        <xsl:variable name="langCode" select="$languageCodes[1]"/>
        <xsl:if test="$langCode">
            <ccmm:primary_language>
                <ccmm:iri><xsl:value-of select="concat('http://publications.europa.eu/resource/authority/language/', upper-case($langCode))"/></ccmm:iri>
            </ccmm:primary_language>
        </xsl:if>
    </xsl:template>

    <!-- ============================================================ -->
    <!-- other_language (optional)                                     -->
    <!-- ============================================================ -->
    <xsl:template name="OtherLanguages">
        <!-- every remaining distinct code; XOAI can repeat the same code under several
             language wrappers, hence distinct-values() -->
        <xsl:for-each select="distinct-values($languageCodes[position() &gt; 1])">
            <ccmm:other_language>
                <ccmm:iri><xsl:value-of select="concat('http://publications.europa.eu/resource/authority/language/', upper-case(.))"/></ccmm:iri>
            </ccmm:other_language>
        </xsl:for-each>
    </xsl:template>

    <!-- ============================================================ -->
    <!-- terms_of_use (required)                                       -->
    <!-- ============================================================ -->
    <xsl:template name="TermsOfUse">
        <!--
            access_rights comes from others/access-status, which DSpace computes itself in
            org.dspace.access.status.DefaultAccessStatusHelper from the actual READ policies on
            the primary bitstream and publishes through AccessStatusElementItemCompilePlugin.
            It is the value the user interface shows and it is present on every item.
            Labels are the four English strings CCMM allows for this term.
        -->
        <xsl:variable name="accessStatus"
            select="normalize-space((doc:metadata/doc:element[@name='others']/doc:element[@name='access-status']/doc:field[@name='value'])[1])"/>
        <xsl:variable name="licenseUri"
            select="normalize-space((doc:metadata/doc:element[@name='dc']/doc:element[@name='rights']/doc:element[@name='uri']/doc:element/doc:field[@name='value'])[1])"/>
        <xsl:variable name="licenseLabel"
            select="normalize-space((doc:metadata/doc:element[@name='dc']/doc:element[@name='rights']/doc:element[@name='label']/doc:element/doc:field[@name='value'])[1])"/>
        <xsl:variable name="ccUri"
            select="normalize-space((doc:metadata/doc:element[@name='others']/doc:element[@name='cc']/doc:field[@name='uri'])[1])"/>
        <xsl:variable name="ccName"
            select="normalize-space((doc:metadata/doc:element[@name='others']/doc:element[@name='cc']/doc:field[@name='name'])[1])"/>
        <ccmm:terms_of_use>
            <ccmm:access_rights>
                <xsl:choose>
                    <xsl:when test="$accessStatus = 'open.access'">
                        <ccmm:iri>http://purl.org/coar/access_right/c_abf2</ccmm:iri>
                        <ccmm:label xml:lang="en">open access</ccmm:label>
                    </xsl:when>
                    <xsl:when test="$accessStatus = 'embargo'">
                        <ccmm:iri>http://purl.org/coar/access_right/c_f1cf</ccmm:iri>
                        <!-- spelling as listed in the CCMM 1.1.0 specification -->
                        <ccmm:label xml:lang="en">embargoes access</ccmm:label>
                    </xsl:when>
                    <xsl:when test="$accessStatus = 'metadata.only'">
                        <ccmm:iri>http://purl.org/coar/access_right/c_14cb</ccmm:iri>
                        <ccmm:label xml:lang="en">metadata only access</ccmm:label>
                    </xsl:when>
                    <xsl:otherwise>
                        <!-- 'restricted', 'unknown' and a missing value all mean "not freely
                             downloadable"; never default to claiming open access -->
                        <ccmm:iri>http://purl.org/coar/access_right/c_16ec</ccmm:iri>
                        <ccmm:label xml:lang="en">restricted access</ccmm:label>
                    </xsl:otherwise>
                </xsl:choose>
            </ccmm:access_rights>
            <!--
                license is mandatory inside terms_of_use, but license_document has no mandatory
                children, so an empty element is valid.  A made-up IRI is not, so when no licence
                URI is known the element stays empty and the wording moves to description below.
            -->
            <ccmm:license>
                <xsl:choose>
                    <xsl:when test="$licenseUri != ''">
                        <ccmm:iri><xsl:value-of select="$licenseUri"/></ccmm:iri>
                        <xsl:if test="$licenseLabel != ''">
                            <ccmm:label xml:lang="en"><xsl:value-of select="$licenseLabel"/></ccmm:label>
                        </xsl:if>
                    </xsl:when>
                    <xsl:when test="$ccUri != ''">
                        <ccmm:iri><xsl:value-of select="$ccUri"/></ccmm:iri>
                        <xsl:if test="$ccName != ''">
                            <ccmm:label xml:lang="en"><xsl:value-of select="$ccName"/></ccmm:label>
                        </xsl:if>
                    </xsl:when>
                </xsl:choose>
            </ccmm:license>
            <xsl:for-each select="doc:metadata/doc:element[@name='dc']/doc:element[@name='rights']/doc:element/doc:field[@name='value']">
                <ccmm:description xml:lang="en"><xsl:value-of select="."/></ccmm:description>
            </xsl:for-each>
        </ccmm:terms_of_use>
    </xsl:template>

    <!-- ============================================================ -->
    <!-- subject (required, unbounded)                                 -->
    <!-- ============================================================ -->
    <xsl:template name="Subjects">
        <!-- dc.subject -->
        <xsl:for-each select="doc:metadata/doc:element[@name='dc']/doc:element[@name='subject']/doc:element/doc:field[@name='value']">
            <ccmm:subject>
                <ccmm:title xml:lang="en"><xsl:value-of select="."/></ccmm:title>
            </ccmm:subject>
        </xsl:for-each>
        <!-- dc.subject.* (nested qualifiers) -->
        <xsl:for-each select="doc:metadata/doc:element[@name='dc']/doc:element[@name='subject']/doc:element/doc:element/doc:field[@name='value']">
            <ccmm:subject>
                <ccmm:title xml:lang="en"><xsl:value-of select="."/></ccmm:title>
            </ccmm:subject>
        </xsl:for-each>
        <!-- Fallback: if no subjects, provide a placeholder -->
        <xsl:if test="not(doc:metadata/doc:element[@name='dc']/doc:element[@name='subject']/doc:element/doc:field[@name='value']) and not(doc:metadata/doc:element[@name='dc']/doc:element[@name='subject']/doc:element/doc:element/doc:field[@name='value'])">
            <ccmm:subject>
                <ccmm:title xml:lang="en"><xsl:value-of select="$FALLBACK_SUBJECT"/></ccmm:title>
            </ccmm:subject>
        </xsl:if>
    </xsl:template>

    <!-- ============================================================ -->
    <!-- description (optional, unbounded)                             -->
    <!-- ============================================================ -->
    <xsl:template name="Descriptions">
        <!-- dc.description (abstract) -->
        <xsl:for-each select="doc:metadata/doc:element[@name='dc']/doc:element[@name='description']/doc:element[@name='abstract']/doc:element/doc:field[@name='value']">
            <ccmm:description>
                <ccmm:description_text xml:lang="en"><xsl:value-of select="."/></ccmm:description_text>
                <ccmm:description_type>
                    <ccmm:iri>https://vocabs.ccmm.cz/registry/codelist/DescriptionType/Abstract</ccmm:iri>
                    <ccmm:label xml:lang="en">Abstract</ccmm:label>
                </ccmm:description_type>
            </ccmm:description>
        </xsl:for-each>
        <!-- dc.description (general, non-qualified) -->
        <xsl:for-each select="doc:metadata/doc:element[@name='dc']/doc:element[@name='description']/doc:element/doc:field[@name='value']">
            <ccmm:description>
                <ccmm:description_text xml:lang="en"><xsl:value-of select="."/></ccmm:description_text>
                <ccmm:description_type>
                    <ccmm:iri>https://vocabs.ccmm.cz/registry/codelist/DescriptionType/Abstract</ccmm:iri>
                    <ccmm:label xml:lang="en">Abstract</ccmm:label>
                </ccmm:description_type>
            </ccmm:description>
        </xsl:for-each>
    </xsl:template>

    <!-- ============================================================ -->
    <!-- funding_reference (optional, unbounded)                       -->
    <!-- ============================================================ -->
    <xsl:template name="FundingReferences">
        <!-- dc.relation with info:eu-repo/grantAgreement pattern -->
        <xsl:for-each select="doc:metadata/doc:element[@name='dc']/doc:element[@name='relation']/doc:element/doc:field[@name='value']">
            <xsl:if test="starts-with(., 'info:')">
                <xsl:variable name="parts" select="tokenize(., '/')"/>
                <ccmm:funding_reference>
                    <xsl:if test="count($parts) >= 5">
                        <ccmm:local_identifier><xsl:value-of select="$parts[5]"/></ccmm:local_identifier>
                    </xsl:if>
                    <ccmm:funder>
                        <ccmm:organization>
                            <ccmm:name>
                                <xsl:choose>
                                    <xsl:when test="$parts[3]='EC'">European Commission</xsl:when>
                                    <xsl:otherwise><xsl:value-of select="$parts[3]"/></xsl:otherwise>
                                </xsl:choose>
                            </ccmm:name>
                        </ccmm:organization>
                    </ccmm:funder>
                </ccmm:funding_reference>
            </xsl:if>
        </xsl:for-each>
    </xsl:template>

    <!-- ============================================================ -->
    <!-- related_resource (optional, unbounded)                        -->
    <!-- ============================================================ -->
    <xsl:template name="RelatedResources">
        <!-- dc.relation.uri -->
        <xsl:for-each select="doc:metadata/doc:element[@name='dc']/doc:element[@name='relation']/doc:element[@name='uri']/doc:element/doc:field[@name='value']">
            <ccmm:related_resource>
                <ccmm:identifier>
                    <ccmm:value><xsl:value-of select="."/></ccmm:value>
                    <ccmm:scheme>
                        <ccmm:iri>https://www.w3.org/ns/iana/uri-schemes</ccmm:iri>
                        <ccmm:label xml:lang="en">URI</ccmm:label>
                    </ccmm:scheme>
                </ccmm:identifier>
            </ccmm:related_resource>
        </xsl:for-each>
        <!-- dc.relation.ispartof -->
        <xsl:for-each select="doc:metadata/doc:element[@name='dc']/doc:element[@name='relation']/doc:element[@name='ispartof']/doc:element/doc:field[@name='value']">
            <ccmm:related_resource>
                <ccmm:title><xsl:value-of select="."/></ccmm:title>
            </ccmm:related_resource>
        </xsl:for-each>
    </xsl:template>

    <!-- ============================================================ -->
    <!-- Helper: Format a date string to xs:date or xs:dateTime        -->
    <!-- ============================================================ -->
    <xsl:template name="FormatDate">
        <xsl:param name="dateStr"/>
        <xsl:choose>
            <!-- Full ISO dateTime: 2024-01-15T10:30:00Z -->
            <xsl:when test="contains($dateStr, 'T')">
                <ccmm:date_time><xsl:value-of select="$dateStr"/></ccmm:date_time>
            </xsl:when>
            <!-- Full date: 2024-01-15 -->
            <xsl:when test="string-length($dateStr) >= 10 and substring($dateStr, 5, 1) = '-' and substring($dateStr, 8, 1) = '-'">
                <ccmm:date><xsl:value-of select="substring($dateStr, 1, 10)"/></ccmm:date>
            </xsl:when>
            <!-- Year-month: 2024-01 -->
            <xsl:when test="string-length($dateStr) >= 7 and substring($dateStr, 5, 1) = '-'">
                <ccmm:date><xsl:value-of select="concat($dateStr, '-01')"/></ccmm:date>
            </xsl:when>
            <!-- Year only: 2024 -->
            <xsl:when test="string-length($dateStr) = 4">
                <ccmm:date><xsl:value-of select="concat($dateStr, '-01-01')"/></ccmm:date>
            </xsl:when>
            <!-- Fallback: attempt to derive a date only if the first 4 chars form a valid year -->
            <xsl:otherwise>
                <xsl:variable name="year" select="substring($dateStr, 1, 4)"/>
                <xsl:choose>
                    <xsl:when test="string-length($dateStr) &gt;= 4 and not(translate($year, '0123456789', ''))">
                        <ccmm:date><xsl:value-of select="concat($year, '-01-01')"/></ccmm:date>
                    </xsl:when>
                    <xsl:otherwise>
                        <ccmm:date/>
                    </xsl:otherwise>
                </xsl:choose>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

</xsl:stylesheet>
