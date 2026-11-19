--
-- The contents of this file are subject to the license and copyright
-- detailed in the LICENSE and NOTICE files at the root of the source
-- tree and available online at
--
-- http://www.dspace.org/license/
--

--
-- The V7.2_2022.07.28 and V7.6_2024.08.05 migrations referenced their objects with a
-- hardcoded "public." prefix. Where DSpace runs in a different schema, move any of those
-- objects left in "public" into the DSpace schema (current_schema()). No-op when DSpace
-- itself lives in "public" or the objects are already in place.
--
DO $$
DECLARE
    target_schema text := current_schema();
    obj record;
BEGIN
    IF target_schema IS NULL OR target_schema = 'public' THEN
        RETURN;
    END IF;

    FOR obj IN
        SELECT name, kind FROM (VALUES
            ('license_definition', 'TABLE'),
            ('license_label', 'TABLE'),
            ('license_label_extended_mapping', 'TABLE'),
            ('license_resource_mapping', 'TABLE'),
            ('license_resource_user_allowance', 'TABLE'),
            ('user_registration', 'TABLE'),
            ('user_metadata', 'TABLE'),
            ('verification_token', 'TABLE'),
            ('previewcontent', 'TABLE'),
            ('preview2preview', 'TABLE'),
            ('license_definition_license_id_seq', 'SEQUENCE'),
            ('license_label_label_id_seq', 'SEQUENCE'),
            ('license_label_extended_mapping_mapping_id_seq', 'SEQUENCE'),
            ('license_resource_mapping_mapping_id_seq', 'SEQUENCE'),
            ('license_resource_user_allowance_transaction_id_seq', 'SEQUENCE'),
            ('user_registration_user_registration_id_seq', 'SEQUENCE'),
            ('user_metadata_user_metadata_id_seq', 'SEQUENCE'),
            ('verification_token_verification_token_id_seq', 'SEQUENCE'),
            ('previewcontent_previewcontent_id_seq', 'SEQUENCE')
        ) AS t(name, kind)
    LOOP
        CONTINUE WHEN to_regclass(format('public.%I', obj.name)) IS NULL;

        -- An OWNED BY sequence cannot be moved on its own; ALTER TABLE ... SET SCHEMA
        -- carries it along with its table (or it stays with a table left untouched).
        CONTINUE WHEN obj.kind = 'SEQUENCE' AND EXISTS (
            SELECT 1 FROM pg_depend d
            WHERE d.classid = 'pg_class'::regclass
              AND d.objid = to_regclass(format('public.%I', obj.name))
              AND d.refclassid = 'pg_class'::regclass
              AND d.deptype = 'a');

        IF to_regclass(format('%I.%I', target_schema, obj.name)) IS NOT NULL THEN
            RAISE WARNING '% exists in both "public" and "%"; left untouched', obj.name, target_schema;
            CONTINUE;
        END IF;

        EXECUTE format('ALTER %s public.%I SET SCHEMA %I', obj.kind, obj.name, target_schema);
    END LOOP;
END $$;
