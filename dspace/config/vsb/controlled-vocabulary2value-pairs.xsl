<?xml version="1.0"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

<!--
<![CDATA[

USAGE:
xsltproc - -stringparam value_pairs_name evyuka_programme - -stringparam dc_term programme controlled-vocabulary2value-pairs.xsl program-directory.xml

INPUT:
<?xml version="1.0" encoding="UTF-8"?>
<isComposedBy>
  <node id="B3502" label="B3502 (Architektura a stavitelství)"></node>
  ...
</isComposedBy>

OUTPUT:
  <value-pairs value-pairs-name="evyuka_subject" dc-term="subject">
    <pair>
      <displayed-value>Neuvedeno</displayed-value>
      <stored-value></stored-value>
    </pair>

    <pair>
      <displayed-value>618-2048/04</displayed-value>
      <stored-value>618-2048/04</stored-value>
    </pair>

    ...

  </value-pairs>

]]>
-->

  <xsl:param name="value_pairs_name" select="VALUE_PAIRS_NAME"/>
  <xsl:param name="dc_term" select="DC_TERM"/>

  <xsl:output omit-xml-declaration="yes" indent="yes"/>

  <xsl:template match="/node/isComposedBy">
    <value-pairs>
      <xsl:attribute name="value-pairs-name"><xsl:value-of select="$value_pairs_name"/></xsl:attribute>
      <xsl:attribute name="dc-term"><xsl:value-of select="$dc_term"/></xsl:attribute>
      <pair>
        <displayed-value>Neuvedeno</displayed-value>
        <stored-value></stored-value>
      </pair>
      <xsl:for-each select="node">
        <pair>
          <displayed-value><xsl:value-of select="@label"/></displayed-value>
          <stored-value><xsl:value-of select="@id"/></stored-value>
        </pair>
      </xsl:for-each>
    </value-pairs>
  </xsl:template>
</xsl:stylesheet>

