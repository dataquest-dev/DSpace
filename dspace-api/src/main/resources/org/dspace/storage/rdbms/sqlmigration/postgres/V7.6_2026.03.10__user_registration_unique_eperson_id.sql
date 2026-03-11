--
-- The contents of this file are subject to the license and copyright
-- detailed in the LICENSE and NOTICE files at the root of the source
-- tree and available online at
--
-- http://www.dspace.org/license/
--
-- Add a unique partial index on eperson_id (only for non-null values).
-- This allows multiple rows with eperson_id = NULL (e.g., anonymous registrations)
-- but enforces that each non-null eperson_id appears at most once.
CREATE UNIQUE INDEX IF NOT EXISTS user_registration_eperson_id_unique
    ON user_registration (eperson_id)
    WHERE eperson_id IS NOT NULL;
