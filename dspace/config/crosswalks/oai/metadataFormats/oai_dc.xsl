<?xml version="1.0" encoding="UTF-8" ?>
<!--  http://www.openarchives.org/OAI/2.0/oai_dc.xsl-->
<xsl:stylesheet 
	xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
	xmlns:doc="http://www.lyncode.com/xoai"
	version="1.0">
	<xsl:output omit-xml-declaration="yes" method="xml" indent="yes" />

	<xsl:template name="transform-OA">
		<xsl:for-each select ="/doc:metadata/doc:element[@name='dc']/doc:element[@name='rights']/doc:element/doc:field/text()">
			<xsl:choose>
				<xsl:when test="contains(., 'OA')">
					<xsl:text>info:eu-repo/semantics/openAccess</xsl:text>
				</xsl:when>
				<xsl:when test="contains(., 'openAccess')">
					<xsl:text>info:eu-repo/semantics/openAccess</xsl:text>
				</xsl:when>
				<xsl:when test="contains(., 'restrictedAccess')">
					<xsl:text>info:eu-repo/semantics/restrictedAccess</xsl:text>
				</xsl:when>
				<xsl:when test="contains(., 'embargoedAccess')">
					<xsl:text>info:eu-repo/semantics/embargoedAccess</xsl:text>
				</xsl:when>
			</xsl:choose>
		</xsl:for-each>
	</xsl:template>
	
	<xsl:template match="/">
		<oai_dc:dc xmlns:oai_dc="http://www.openarchives.org/OAI/2.0/oai_dc/" 
			xmlns:dc="http://purl.org/dc/elements/1.1/" 
			xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" 
			xsi:schemaLocation="http://www.openarchives.org/OAI/2.0/oai_dc/ http://www.openarchives.org/OAI/2.0/oai_dc.xsd">
			<!-- dc.title -->
			<xsl:for-each select="doc:metadata/doc:element[@name='dc']/doc:element[@name='title']/doc:element/doc:field[@name='value']">
				<dc:title><xsl:value-of select="." /></dc:title>
			</xsl:for-each>
			<!-- dc.title.* -->
			<xsl:for-each select="doc:metadata/doc:element[@name='dc']/doc:element[@name='title']/doc:element/doc:element/doc:field[@name='value']">
				<dc:title><xsl:value-of select="." /></dc:title>
			</xsl:for-each>
			<!-- dc.creator -->
			<xsl:for-each select="doc:metadata/doc:element[@name='dc']/doc:element[@name='creator']/doc:element/doc:field[@name='value']">
				<dc:creator><xsl:value-of select="." /></dc:creator>
			</xsl:for-each>
			<!-- dc.contributor.author -->
			<xsl:for-each select="doc:metadata/doc:element[@name='dc']/doc:element[@name='contributor']/doc:element[@name='author']/doc:element/doc:field[@name='value']">
				<dc:creator><xsl:value-of select="." /></dc:creator>
			</xsl:for-each>
			<!-- dc.contributor.* (!author) -->
			<xsl:for-each select="doc:metadata/doc:element[@name='dc']/doc:element[@name='contributor']/doc:element[@name!='author']/doc:element/doc:field[@name='value']">
				<dc:contributor><xsl:value-of select="." /></dc:contributor>
			</xsl:for-each>
			<!-- dc.contributor -->
			<xsl:for-each select="doc:metadata/doc:element[@name='dc']/doc:element[@name='contributor']/doc:element/doc:field[@name='value']">
				<dc:contributor><xsl:value-of select="." /></dc:contributor>
			</xsl:for-each>
			<!-- dc.subject -->
			<xsl:for-each select="doc:metadata/doc:element[@name='dc']/doc:element[@name='subject']/doc:element/doc:field[@name='value']">
				<dc:subject><xsl:value-of select="." /></dc:subject>
			</xsl:for-each>
			<!-- dc.subject.* -->
			<xsl:for-each select="doc:metadata/doc:element[@name='dc']/doc:element[@name='subject']/doc:element/doc:element/doc:field[@name='value']">
				<dc:subject><xsl:value-of select="." /></dc:subject>
			</xsl:for-each>
			<!-- dc.description.version -->
			<xsl:for-each select="doc:metadata/doc:element[@name='dc']/doc:element[@name='description']/doc:element[@name='version']/doc:element/doc:field[@name='value']">
				<dc:rights>
					<xsl:choose>
						<xsl:when test="contains(., 'OA')">
							<xsl:text>info:eu-repo/semantics/openAccess</xsl:text>
						</xsl:when>
						<xsl:when test="contains(., 'openAccess')">
							<xsl:text>info:eu-repo/semantics/openAccess</xsl:text>
						</xsl:when>
						<xsl:when test="contains(., 'restrictedAccess')">
							<xsl:text>info:eu-repo/semantics/restrictedAccess</xsl:text>
						</xsl:when>
						<xsl:when test="contains(., 'embargoedAccess')">
							<xsl:text>info:eu-repo/semantics/embargoedAccess</xsl:text>
						</xsl:when>
						<xsl:when test="contains(., 'Published Version')">
							<xsl:text>info:eu-repo/semantics/openAccess</xsl:text>
						</xsl:when>
						<xsl:otherwise>
							<xsl:text>info:eu-repo/semantics/restrictedAccess</xsl:text>
						</xsl:otherwise>
					</xsl:choose>
				</dc:rights>
			</xsl:for-each>
			<!-- dc.description -->
			<xsl:for-each select="doc:metadata/doc:element[@name='dc']/doc:element[@name='description']/doc:element/doc:field[@name='value']">
				<dc:description><xsl:value-of select="." /></dc:description>
			</xsl:for-each>
			<!-- dc.description.* (not provenance)-->
			<xsl:for-each select="doc:metadata/doc:element[@name='dc']/doc:element[@name='description']/doc:element[@name!='provenance']/doc:element/doc:field[@name='value']">
				<dc:description><xsl:value-of select="." /></dc:description>
			</xsl:for-each>
			<!-- dc.date.issued -->
			<xsl:for-each select="doc:metadata/doc:element[@name='dc']/doc:element[@name='date']/doc:element[@name='issued']/doc:element/doc:field[@name='value']">
				<dc:date><xsl:value-of select="." /></dc:date>
			</xsl:for-each>
			<!-- dc.type -->
			<xsl:for-each select="doc:metadata/doc:element[@name='dc']/doc:element[@name='type']/doc:element/doc:field[@name='value']">
				<dc:type><xsl:value-of select="." /></dc:type>
			</xsl:for-each>
			<!-- dc.type with `info:eu-repo/semantics/`-->
			<xsl:for-each select="doc:metadata/doc:element[@name='dc']/doc:element[@name='type']/doc:element/doc:field[@name='value']">
				<dc:type><xsl:value-of select="concat('info:eu-repo/semantics/', .)" /></dc:type>
			</xsl:for-each>
			<!-- dc.type.* -->
			<xsl:for-each select="doc:metadata/doc:element[@name='dc']/doc:element[@name='type']/doc:element/doc:element/doc:field[@name='value']">
				<dc:type><xsl:value-of select="." /></dc:type>
			</xsl:for-each>
			<!-- dc.identifier -->
			<xsl:for-each select="doc:metadata/doc:element[@name='dc']/doc:element[@name='identifier']/doc:element/doc:field[@name='value']">
				<dc:identifier><xsl:value-of select="." /></dc:identifier>
			</xsl:for-each>
			<!-- dc.identifier - úplná citácia v ČSN ISO 690 formáte (pre Citace PRO) -->
			<!-- Replikuje pôvodnú hodnotu dc.identifier.citation z DSpace 6 (Mirage2 itemSummaryView-DIM-cite-test) -->
			<xsl:variable name="citType">
				<xsl:choose>
					<xsl:when test="doc:metadata/doc:element[@name='dc']/doc:element[@name='type']/doc:element[@name='none']/doc:field[@name='value']">
						<xsl:value-of select="doc:metadata/doc:element[@name='dc']/doc:element[@name='type']/doc:element[@name='none']/doc:field[@name='value'][1]" />
					</xsl:when>
					<xsl:otherwise>
						<xsl:value-of select="doc:metadata/doc:element[@name='dc']/doc:element[@name='type']/doc:element/doc:field[@name='value'][1]" />
					</xsl:otherwise>
				</xsl:choose>
			</xsl:variable>
			<xsl:variable name="hasAuthors" select="boolean(doc:metadata/doc:element[@name='dc']/doc:element[@name='contributor']/doc:element[@name='author']/doc:element/doc:field[@name='value'])" />
			<xsl:variable name="hasTitle" select="boolean(doc:metadata/doc:element[@name='dc']/doc:element[@name='title']/doc:element/doc:field[@name='value'])" />
			<xsl:variable name="hasYear" select="boolean(doc:metadata/doc:element[@name='dc']/doc:element[@name='date']/doc:element[@name='issued']/doc:element/doc:field[@name='value'])" />
			<xsl:if test="$hasAuthors or $hasTitle or $hasYear">
				<dc:identifier>
					<!-- AUTHORS: "Priezvisko, Krstné; Priezvisko2, Krstné2. " -->
					<xsl:if test="$hasAuthors">
						<xsl:for-each select="doc:metadata/doc:element[@name='dc']/doc:element[@name='contributor']/doc:element[@name='author']/doc:element/doc:field[@name='value']">
							<xsl:value-of select="." />
							<xsl:choose>
								<xsl:when test="position() = last()"><xsl:text>. </xsl:text></xsl:when>
								<xsl:otherwise><xsl:text>; </xsl:text></xsl:otherwise>
							</xsl:choose>
						</xsl:for-each>
					</xsl:if>
					<!-- YEAR: "2024. " -->
					<xsl:if test="$hasYear">
						<xsl:value-of select="doc:metadata/doc:element[@name='dc']/doc:element[@name='date']/doc:element[@name='issued']/doc:element/doc:field[@name='value']" />
						<xsl:text>. </xsl:text>
					</xsl:if>
					<!-- TITLE: "Title. " -->
					<xsl:if test="$hasTitle">
						<xsl:value-of select="doc:metadata/doc:element[@name='dc']/doc:element[@name='title']/doc:element/doc:field[@name='value'][1]" />
						<xsl:text>. </xsl:text>
					</xsl:if>
					<!-- Source / journal / book name (dc.relation.ispartof) -->
					<xsl:variable name="ispartof" select="doc:metadata/doc:element[@name='dc']/doc:element[@name='relation']/doc:element[@name='ispartof']/doc:element/doc:field[@name='value']" />
					<xsl:if test="$ispartof != ''">
						<xsl:value-of select="$ispartof" />
						<xsl:text>. </xsl:text>
					</xsl:if>
					<xsl:choose>
						<!-- Article: "volume(issue), pages. " -->
						<xsl:when test="$citType = 'article' or $citType = 'conferenceObject' or contains($citType, 'J_')">
							<xsl:variable name="vol" select="doc:metadata/doc:element[@name='local']/doc:element[@name='volume']/doc:element/doc:field[@name='value']" />
							<xsl:variable name="num" select="doc:metadata/doc:element[@name='local']/doc:element[@name='number']/doc:element/doc:field[@name='value']" />
							<xsl:variable name="pages" select="doc:metadata/doc:element[@name='dc']/doc:element[@name='format']/doc:element[@name='none']/doc:field[@name='value'] | doc:metadata/doc:element[@name='dc']/doc:element[@name='format']/doc:element[not(@name='none')]/doc:field[@name='value']" />
							<xsl:if test="$vol != ''">
								<xsl:value-of select="$vol" />
								<xsl:if test="$num != ''">
									<xsl:text>(</xsl:text>
									<xsl:value-of select="$num" />
									<xsl:text>)</xsl:text>
								</xsl:if>
							</xsl:if>
							<xsl:if test="$pages[1] != ''">
								<xsl:if test="$vol != ''"><xsl:text>, </xsl:text></xsl:if>
								<xsl:value-of select="$pages[1]" />
							</xsl:if>
							<xsl:if test="$vol != '' or $pages[1] != ''"><xsl:text>. </xsl:text></xsl:if>
						</xsl:when>
						<!-- Book / bookPart / workingPaper / other: publisher, pages -->
						<xsl:otherwise>
							<xsl:variable name="publisher" select="doc:metadata/doc:element[@name='dc']/doc:element[@name='publisher']/doc:element/doc:field[@name='value']" />
							<xsl:variable name="pages2" select="doc:metadata/doc:element[@name='dc']/doc:element[@name='format']/doc:element/doc:field[@name='value']" />
							<xsl:if test="$publisher != ''">
								<xsl:value-of select="$publisher" />
								<xsl:text>. </xsl:text>
							</xsl:if>
							<xsl:if test="$pages2[1] != ''">
								<xsl:value-of select="$pages2[1]" />
								<xsl:text>. </xsl:text>
							</xsl:if>
						</xsl:otherwise>
					</xsl:choose>
					<!-- ISSN -->
					<xsl:if test="doc:metadata/doc:element[@name='dc']/doc:element[@name='identifier']/doc:element[@name='issn']/doc:element/doc:field[@name='value']">
						<xsl:text>ISSN </xsl:text>
						<xsl:value-of select="doc:metadata/doc:element[@name='dc']/doc:element[@name='identifier']/doc:element[@name='issn']/doc:element/doc:field[@name='value']" />
						<xsl:text>. </xsl:text>
					</xsl:if>
					<!-- ISBN -->
					<xsl:if test="doc:metadata/doc:element[@name='dc']/doc:element[@name='identifier']/doc:element[@name='isbn']/doc:element/doc:field[@name='value']">
						<xsl:text>ISBN </xsl:text>
						<xsl:value-of select="doc:metadata/doc:element[@name='dc']/doc:element[@name='identifier']/doc:element[@name='isbn']/doc:element/doc:field[@name='value']" />
						<xsl:text>. </xsl:text>
					</xsl:if>
					<!-- URL: priorita dc.relation.uri (zdrojová stránka článku), inak DOI, inak dc.identifier.uri (handle) -->
					<xsl:choose>
						<xsl:when test="doc:metadata/doc:element[@name='dc']/doc:element[@name='relation']/doc:element[@name='uri']/doc:element/doc:field[@name='value']">
							<xsl:value-of select="doc:metadata/doc:element[@name='dc']/doc:element[@name='relation']/doc:element[@name='uri']/doc:element/doc:field[@name='value']" />
						</xsl:when>
						<xsl:when test="doc:metadata/doc:element[@name='local']/doc:element[@name='identifier']/doc:element[@name='doi']/doc:element/doc:field[@name='value']">
							<xsl:text>https://doi.org/</xsl:text>
							<xsl:value-of select="doc:metadata/doc:element[@name='local']/doc:element[@name='identifier']/doc:element[@name='doi']/doc:element/doc:field[@name='value']" />
						</xsl:when>
						<xsl:when test="doc:metadata/doc:element[@name='dc']/doc:element[@name='identifier']/doc:element[@name='doi']/doc:element/doc:field[@name='value']">
							<xsl:text>https://doi.org/</xsl:text>
							<xsl:value-of select="doc:metadata/doc:element[@name='dc']/doc:element[@name='identifier']/doc:element[@name='doi']/doc:element/doc:field[@name='value']" />
						</xsl:when>
						<xsl:when test="doc:metadata/doc:element[@name='dc']/doc:element[@name='identifier']/doc:element[@name='uri']/doc:element/doc:field[@name='value']">
							<xsl:value-of select="doc:metadata/doc:element[@name='dc']/doc:element[@name='identifier']/doc:element[@name='uri']/doc:element/doc:field[@name='value']" />
						</xsl:when>
					</xsl:choose>
				</dc:identifier>
			</xsl:if>
			<!-- dc.identifier.* -->
			<xsl:for-each select="doc:metadata/doc:element[@name='dc']/doc:element[@name='identifier']/doc:element/doc:element/doc:field[@name='value']">
				<dc:identifier><xsl:value-of select="." /></dc:identifier>
			</xsl:for-each>
			<!-- local.identifier.doi -->
			<xsl:for-each select="doc:metadata/doc:element[@name='local']/doc:element[@name='identifier']/doc:element[@name='doi']/doc:element/doc:field[@name='value']">
				<dc:identifier><xsl:value-of select="." /></dc:identifier>
			</xsl:for-each>
			<!-- dc.language -->
			<xsl:for-each select="doc:metadata/doc:element[@name='dc']/doc:element[@name='language']/doc:element/doc:field[@name='value']">
				<dc:language><xsl:value-of select="." /></dc:language>
			</xsl:for-each>
			<!-- dc.language.* -->
			<xsl:for-each select="doc:metadata/doc:element[@name='dc']/doc:element[@name='language']/doc:element/doc:element/doc:field[@name='value']">
				<dc:language><xsl:value-of select="." /></dc:language>
			</xsl:for-each>
			<!-- dc.relation -->


			<xsl:for-each select="doc:metadata/doc:element[@name='dc']/doc:element[@name='relation']/doc:element/doc:field[@name='value']">
				<dc:relation>info:eu-repo/grantAgreement/<xsl:value-of select="." /></dc:relation>
			</xsl:for-each>
			<!-- dc.relation.ispartof -->
			<xsl:for-each select="doc:metadata/doc:element[@name='dc']/doc:element[@name='relation']/doc:element[@name='ispartof']/doc:element/doc:field[@name='value']">
				<dc:relation><xsl:value-of select="." /></dc:relation>
			</xsl:for-each>
			<!-- dc.rights -->
			<xsl:for-each select="doc:metadata/doc:element[@name='dc']/doc:element[@name='rights']/doc:element/doc:field[@name='value']">
				<dc:rights><xsl:value-of select="." /></dc:rights>
			</xsl:for-each>
			<!-- dc.rights.* -->
			<xsl:for-each select="doc:metadata/doc:element[@name='dc']/doc:element[@name='rights']/doc:element/doc:element/doc:field[@name='value']">
				<dc:rights><xsl:value-of select="." /></dc:rights>
			</xsl:for-each>
			<!-- dc.format -->
			<xsl:for-each select="doc:metadata/doc:element[@name='dc']/doc:element[@name='format']/doc:element/doc:field[@name='value']">
				<dc:format><xsl:value-of select="." /></dc:format>
			</xsl:for-each>
			<!-- dc.format.* -->
			<xsl:for-each select="doc:metadata/doc:element[@name='dc']/doc:element[@name='format']/doc:element/doc:element/doc:field[@name='value']">
				<dc:format><xsl:value-of select="." /></dc:format>
			</xsl:for-each>
			<!-- ? -->
			<xsl:for-each select="doc:metadata/doc:element[@name='bundles']/doc:element[@name='bundle']/doc:field[@name='name'][text()='ORIGINAL']/../doc:element[@name='bitstreams']/doc:element[@name='bitstream']/doc:field[@name='format']">
				<dc:format><xsl:value-of select="." /></dc:format>
			</xsl:for-each>
			<!-- dc.coverage -->
			<xsl:for-each select="doc:metadata/doc:element[@name='dc']/doc:element[@name='coverage']/doc:element/doc:field[@name='value']">
				<dc:coverage><xsl:value-of select="." /></dc:coverage>
			</xsl:for-each>
			<!-- dc.coverage.* -->
			<xsl:for-each select="doc:metadata/doc:element[@name='dc']/doc:element[@name='coverage']/doc:element/doc:element/doc:field[@name='value']">
				<dc:coverage><xsl:value-of select="." /></dc:coverage>
			</xsl:for-each>
			<!-- dc.publisher -->
			<xsl:for-each select="doc:metadata/doc:element[@name='dc']/doc:element[@name='publisher']/doc:element/doc:field[@name='value']">
				<dc:publisher><xsl:value-of select="." /></dc:publisher>
			</xsl:for-each>
			<!-- dc.publisher.* -->
			<xsl:for-each select="doc:metadata/doc:element[@name='dc']/doc:element[@name='publisher']/doc:element/doc:element/doc:field[@name='value']">
				<dc:publisher><xsl:value-of select="." /></dc:publisher>
			</xsl:for-each>
			<!-- dc.source -->
			<xsl:for-each select="doc:metadata/doc:element[@name='dc']/doc:element[@name='source']/doc:element/doc:field[@name='value']">
				<dc:source><xsl:value-of select="." /></dc:source>
			</xsl:for-each>
			<!-- dc.source.* -->
			<xsl:for-each select="doc:metadata/doc:element[@name='dc']/doc:element[@name='source']/doc:element/doc:element/doc:field[@name='value']">
				<dc:source><xsl:value-of select="." /></dc:source>
			</xsl:for-each>
		</oai_dc:dc>
	</xsl:template>
</xsl:stylesheet>
