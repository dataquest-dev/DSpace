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
                    <ccmm:iri>https://model.ccmm.cz/vocabulary/datacite/contributorType/DataCurator</ccmm:iri>
                    <ccmm:label xml:lang="en">DataCurator</ccmm:label>
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
                            <xsl:variable name="uri" select="doc:metadata/doc:element[@name='dc']/doc:element[@name='identifier']/doc:element[@name='uri']/doc:element/doc:field[@name='value'][1]"/>
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
                    <ccmm:iri>https://model.ccmm.cz/vocabulary/datacite/contributorType/Creator</ccmm:iri>
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
                    <ccmm:iri>https://model.ccmm.cz/vocabulary/datacite/contributorType/Creator</ccmm:iri>
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
                    <ccmm:iri>https://model.ccmm.cz/vocabulary/datacite/contributorType/Editor</ccmm:iri>
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
                    <ccmm:iri>https://model.ccmm.cz/vocabulary/datacite/contributorType/Other</ccmm:iri>
                    <ccmm:label xml:lang="en">Other</ccmm:label>
                </ccmm:role>
            </ccmm:qualified_relation>
        </xsl:for-each>
        <!--
            dc.publisher mapped to Distributor role.
            In CCMM/DataCite vocabulary, the DSpace publisher typically acts as
            the distributing organization rather than the original publisher.
            See https://model.ccmm.cz/vocabulary/datacite/contributorType/Distributor
        -->
        <xsl:for-each select="doc:metadata/doc:element[@name='dc']/doc:element[@name='publisher']/doc:element/doc:field[@name='value']">
            <ccmm:qualified_relation>
                <ccmm:relation>
                    <ccmm:organization>
                        <ccmm:name><xsl:value-of select="."/></ccmm:name>
                    </ccmm:organization>
                </ccmm:relation>
                <ccmm:role>
                    <ccmm:iri>https://model.ccmm.cz/vocabulary/datacite/contributorType/Distributor</ccmm:iri>
                    <ccmm:label xml:lang="en">Distributor</ccmm:label>
                </ccmm:role>
            </ccmm:qualified_relation>
        </xsl:for-each>
    </xsl:template>

    <!-- ============================================================ -->
    <!-- publication_year (required)                                    -->
    <!-- ============================================================ -->
    <xsl:template name="PublicationYear">
        <ccmm:publication_year>
            <xsl:choose>
                <xsl:when test="doc:metadata/doc:element[@name='dc']/doc:element[@name='date']/doc:element[@name='issued']/doc:element/doc:field[@name='value']">
                    <xsl:value-of select="substring(doc:metadata/doc:element[@name='dc']/doc:element[@name='date']/doc:element[@name='issued']/doc:element/doc:field[@name='value'], 1, 4)"/>
                </xsl:when>
                <xsl:when test="doc:metadata/doc:element[@name='dc']/doc:element[@name='date']/doc:element[@name='accessioned']/doc:element/doc:field[@name='value']">
                    <xsl:value-of select="substring(doc:metadata/doc:element[@name='dc']/doc:element[@name='date']/doc:element[@name='accessioned']/doc:element/doc:field[@name='value'], 1, 4)"/>
                </xsl:when>
                <xsl:otherwise><xsl:value-of select="$FALLBACK_PUBLICATION_YEAR"/></xsl:otherwise>
            </xsl:choose>
        </ccmm:publication_year>
    </xsl:template>

    <!-- ============================================================ -->
    <!-- time_reference (required, unbounded)                          -->
    <!-- ============================================================ -->
    <xsl:template name="TimeReferences">
        <!-- dc.date.issued -> Issued -->
        <xsl:for-each select="doc:metadata/doc:element[@name='dc']/doc:element[@name='date']/doc:element[@name='issued']/doc:element/doc:field[@name='value']">
            <ccmm:time_reference>
                <ccmm:temporal_representation>
                    <ccmm:time_instant>
                        <xsl:call-template name="FormatDate">
                            <xsl:with-param name="dateStr" select="."/>
                        </xsl:call-template>
                    </ccmm:time_instant>
                </ccmm:temporal_representation>
                <ccmm:date_type>
                    <ccmm:iri>https://model.ccmm.cz/vocabulary/datacite/dateType/Issued</ccmm:iri>
                    <ccmm:label xml:lang="en">Issued</ccmm:label>
                </ccmm:date_type>
            </ccmm:time_reference>
        </xsl:for-each>
        <!-- dc.date.accessioned -> Accepted -->
        <xsl:for-each select="doc:metadata/doc:element[@name='dc']/doc:element[@name='date']/doc:element[@name='accessioned']/doc:element/doc:field[@name='value']">
            <ccmm:time_reference>
                <ccmm:temporal_representation>
                    <ccmm:time_instant>
                        <xsl:call-template name="FormatDate">
                            <xsl:with-param name="dateStr" select="."/>
                        </xsl:call-template>
                    </ccmm:time_instant>
                </ccmm:temporal_representation>
                <ccmm:date_type>
                    <ccmm:iri>https://model.ccmm.cz/vocabulary/datacite/dateType/Accepted</ccmm:iri>
                    <ccmm:label xml:lang="en">Accepted</ccmm:label>
                </ccmm:date_type>
            </ccmm:time_reference>
        </xsl:for-each>
        <!-- dc.date.available -> Available -->
        <xsl:for-each select="doc:metadata/doc:element[@name='dc']/doc:element[@name='date']/doc:element[@name='available']/doc:element/doc:field[@name='value']">
            <ccmm:time_reference>
                <ccmm:temporal_representation>
                    <ccmm:time_instant>
                        <xsl:call-template name="FormatDate">
                            <xsl:with-param name="dateStr" select="."/>
                        </xsl:call-template>
                    </ccmm:time_instant>
                </ccmm:temporal_representation>
                <ccmm:date_type>
                    <ccmm:iri>https://model.ccmm.cz/vocabulary/datacite/dateType/Available</ccmm:iri>
                    <ccmm:label xml:lang="en">Available</ccmm:label>
                </ccmm:date_type>
            </ccmm:time_reference>
        </xsl:for-each>
        <!-- Fallback: if no issued/available dates, create a minimal time_reference from accessioned -->
        <xsl:if test="not(doc:metadata/doc:element[@name='dc']/doc:element[@name='date']/doc:element[@name='issued']/doc:element/doc:field[@name='value']) and not(doc:metadata/doc:element[@name='dc']/doc:element[@name='date']/doc:element[@name='available']/doc:element/doc:field[@name='value']) and doc:metadata/doc:element[@name='dc']/doc:element[@name='date']/doc:element[@name='accessioned']/doc:element/doc:field[@name='value']">
            <xsl:variable name="accessionedDate"
                          select="doc:metadata/doc:element[@name='dc']/doc:element[@name='date']/doc:element[@name='accessioned']/doc:element/doc:field[@name='value'][1]"/>
            <ccmm:time_reference>
                <ccmm:temporal_representation>
                    <ccmm:time_instant>
                        <xsl:call-template name="FormatDate">
                            <xsl:with-param name="dateStr" select="$accessionedDate"/>
                        </xsl:call-template>
                    </ccmm:time_instant>
                </ccmm:temporal_representation>
                <ccmm:date_type>
                    <ccmm:iri>https://model.ccmm.cz/vocabulary/datacite/dateType/Issued</ccmm:iri>
                    <ccmm:label xml:lang="en">Issued</ccmm:label>
                </ccmm:date_type>
            </ccmm:time_reference>
        </xsl:if>
    </xsl:template>

    <!-- ============================================================ -->
    <!-- resource_type (optional)                                      -->
    <!-- ============================================================ -->
    <xsl:template name="ResourceType">
        <xsl:variable name="dctype" select="doc:metadata/doc:element[@name='dc']/doc:element[@name='type']/doc:element/doc:field[@name='value']"/>
        <xsl:if test="$dctype">
            <ccmm:resource_type>
                <ccmm:iri>
                    <xsl:choose>
                        <xsl:when test="$dctype='Dataset' or $dctype='corpus' or $dctype='lexicalConceptualResource' or $dctype='languageDescription'">https://model.ccmm.cz/vocabulary/datacite/resourceTypeGeneral/Dataset</xsl:when>
                        <xsl:when test="$dctype='Software' or $dctype='toolService'">https://model.ccmm.cz/vocabulary/datacite/resourceTypeGeneral/Software</xsl:when>
                        <xsl:when test="$dctype='Text' or $dctype='text'">https://model.ccmm.cz/vocabulary/datacite/resourceTypeGeneral/Text</xsl:when>
                        <xsl:when test="$dctype='Image' or $dctype='image'">https://model.ccmm.cz/vocabulary/datacite/resourceTypeGeneral/Image</xsl:when>
                        <xsl:when test="$dctype='Collection'">https://model.ccmm.cz/vocabulary/datacite/resourceTypeGeneral/Collection</xsl:when>
                        <xsl:otherwise>https://model.ccmm.cz/vocabulary/datacite/resourceTypeGeneral/Other</xsl:otherwise>
                    </xsl:choose>
                </ccmm:iri>
                <ccmm:label xml:lang="en"><xsl:value-of select="$dctype"/></ccmm:label>
            </ccmm:resource_type>
        </xsl:if>
    </xsl:template>

    <!-- ============================================================ -->
    <!-- primary_language (optional)                                    -->
    <!-- ============================================================ -->
    <xsl:template name="PrimaryLanguage">
        <xsl:variable name="lang" select="doc:metadata/doc:element[@name='dc']/doc:element[@name='language']/doc:element[@name='iso']/doc:element/doc:field[@name='value']"/>
        <xsl:variable name="langAlt" select="doc:metadata/doc:element[@name='dc']/doc:element[@name='language']/doc:element/doc:field[@name='value']"/>
        <xsl:choose>
            <xsl:when test="$lang">
                <ccmm:primary_language>
                    <ccmm:iri>
                        <xsl:value-of select="concat('https://iso639-3.sil.org/code/', $lang)"/>
                    </ccmm:iri>
                    <ccmm:label xml:lang="en"><xsl:value-of select="$lang"/></ccmm:label>
                </ccmm:primary_language>
            </xsl:when>
            <xsl:when test="$langAlt">
                <ccmm:primary_language>
                    <ccmm:iri>
                        <xsl:value-of select="concat('https://iso639-3.sil.org/code/', $langAlt)"/>
                    </ccmm:iri>
                    <ccmm:label xml:lang="en"><xsl:value-of select="$langAlt"/></ccmm:label>
                </ccmm:primary_language>
            </xsl:when>
        </xsl:choose>
    </xsl:template>

    <!-- ============================================================ -->
    <!-- other_language (optional)                                     -->
    <!-- ============================================================ -->
    <xsl:template name="OtherLanguages">
        <!-- If there are multiple language values, the first one becomes primary, rest become other -->
        <xsl:variable name="langs" select="doc:metadata/doc:element[@name='dc']/doc:element[@name='language']/doc:element[@name='iso']/doc:element/doc:field[@name='value']"/>
        <xsl:for-each select="$langs[position() > 1]">
            <ccmm:other_language>
                <ccmm:iri>
                    <xsl:value-of select="concat('https://iso639-3.sil.org/code/', .)"/>
                </ccmm:iri>
                <ccmm:label xml:lang="en"><xsl:value-of select="."/></ccmm:label>
            </ccmm:other_language>
        </xsl:for-each>
    </xsl:template>

    <!-- ============================================================ -->
    <!-- terms_of_use (required)                                       -->
    <!-- ============================================================ -->
    <xsl:template name="TermsOfUse">
        <ccmm:terms_of_use>
            <!-- access_rights -->
            <ccmm:access_rights>
                <xsl:choose>
                    <xsl:when test="doc:metadata/doc:element[@name='local']/doc:element[@name='embargo']/doc:element[@name='termslift']/doc:element/doc:field[@name='value']">
                        <ccmm:iri>http://purl.org/coar/access_right/c_f1cf</ccmm:iri>
                        <ccmm:label xml:lang="en">embargoed access</ccmm:label>
                    </xsl:when>
                    <xsl:when test="doc:metadata/doc:element[@name='others']/doc:field[@name='restrictedAccess']/text()='true'">
                        <ccmm:iri>http://purl.org/coar/access_right/c_16ec</ccmm:iri>
                        <ccmm:label xml:lang="en">restricted access</ccmm:label>
                    </xsl:when>
                    <xsl:otherwise>
                        <ccmm:iri>http://purl.org/coar/access_right/c_abf2</ccmm:iri>
                        <ccmm:label xml:lang="en">open access</ccmm:label>
                    </xsl:otherwise>
                </xsl:choose>
            </ccmm:access_rights>
            <!-- license -->
            <ccmm:license>
                <xsl:choose>
                    <xsl:when test="doc:metadata/doc:element[@name='dc']/doc:element[@name='rights']/doc:element[@name='uri']/doc:element/doc:field[@name='value']">
                        <ccmm:iri>
                            <xsl:value-of select="doc:metadata/doc:element[@name='dc']/doc:element[@name='rights']/doc:element[@name='uri']/doc:element/doc:field[@name='value']"/>
                        </ccmm:iri>
                        <xsl:if test="doc:metadata/doc:element[@name='dc']/doc:element[@name='rights']/doc:element[@name='label']/doc:element/doc:field[@name='value']">
                            <ccmm:label xml:lang="en">
                                <xsl:value-of select="doc:metadata/doc:element[@name='dc']/doc:element[@name='rights']/doc:element[@name='label']/doc:element/doc:field[@name='value']"/>
                            </ccmm:label>
                        </xsl:if>
                    </xsl:when>
                    <xsl:when test="doc:metadata/doc:element[@name='dc']/doc:element[@name='rights']/doc:element/doc:field[@name='value']">
                        <!-- Use the rights text itself as a fallback label -->
                        <ccmm:iri>https://model.ccmm.cz/vocabulary/ccmm/license/unspecified</ccmm:iri>
                        <ccmm:label xml:lang="en">
                            <xsl:value-of select="doc:metadata/doc:element[@name='dc']/doc:element[@name='rights']/doc:element/doc:field[@name='value']"/>
                        </ccmm:label>
                    </xsl:when>
                    <xsl:otherwise>
                        <ccmm:iri>https://model.ccmm.cz/vocabulary/ccmm/license/unspecified</ccmm:iri>
                        <ccmm:label xml:lang="en">unspecified</ccmm:label>
                    </xsl:otherwise>
                </xsl:choose>
            </ccmm:license>
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
                    <ccmm:iri>https://model.ccmm.cz/vocabulary/datacite/descriptionType/Abstract</ccmm:iri>
                    <ccmm:label xml:lang="en">Abstract</ccmm:label>
                </ccmm:description_type>
            </ccmm:description>
        </xsl:for-each>
        <!-- dc.description (general, non-qualified) -->
        <xsl:for-each select="doc:metadata/doc:element[@name='dc']/doc:element[@name='description']/doc:element/doc:field[@name='value']">
            <ccmm:description>
                <ccmm:description_text xml:lang="en"><xsl:value-of select="."/></ccmm:description_text>
                <ccmm:description_type>
                    <ccmm:iri>https://model.ccmm.cz/vocabulary/datacite/descriptionType/Abstract</ccmm:iri>
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
