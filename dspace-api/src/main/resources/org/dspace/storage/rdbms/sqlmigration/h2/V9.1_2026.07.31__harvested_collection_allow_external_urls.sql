--
-- The contents of this file are subject to the license and copyright
-- detailed in the LICENSE and NOTICE files at the root of the source
-- tree and available online at
--
-- http://www.dspace.org/license/
--

-----------------------------------------------------------------------------------
-- Alter harvested_collection table
-----------------------------------------------------------------------------------

ALTER TABLE harvested_collection ADD COLUMN allow_external_urls BOOLEAN DEFAULT FALSE NOT NULL;

-- Existing "metadata and bitstreams" harvests already fetch files cross-host; keep them working.
-- Internal/private addresses are blocked regardless of this flag.
UPDATE harvested_collection SET allow_external_urls = TRUE WHERE harvest_type = 3;
