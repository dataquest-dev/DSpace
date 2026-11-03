--
-- The contents of this file are subject to the license and copyright
-- detailed in the LICENSE and NOTICE files at the root of the source
-- tree and available online at
--
-- http://www.dspace.org/license/
--

CREATE UNIQUE INDEX IF NOT EXISTS user_registration_eperson_id_unique
    ON user_registration (eperson_id);

ALTER TABLE user_registration DROP COLUMN IF EXISTS email;
