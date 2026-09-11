--
-- The contents of this file are subject to the license and copyright
-- detailed in the LICENSE and NOTICE files at the root of the source
-- tree and available online at
--
-- http://www.dspace.org/license/
--

-----------------------------------------------------------------------------------
-- Re-apply the CLARIN / preview object ownership without the hardcoded `public.`
-- schema qualifier.
--
-- V7.2_2022.07.28__Upgrade_to_Lindat_Clarin_schema.sql and
-- V7.6_2024.08.05__Added_Preview_Tables.sql issue `ALTER TABLE public.<name> OWNER TO
-- dspace`, binding the statement to the `public` schema instead of the connection's
-- search_path. Those files are already applied, so editing them would break their
-- Flyway checksum; this migration restates the same intent schema-agnostically.
--
-- Ownership is best-effort: the statement needs the current role to own the object and
-- to be a member of `dspace`, and `dspace` to hold CREATE on the object's schema. Any
-- install that does not satisfy that (different role name, tenant-owned schema) is left
-- untouched with a notice rather than failing the migration.
-----------------------------------------------------------------------------------
DO $$
DECLARE
    target_owner CONSTANT text := 'dspace';
    objects CONSTANT text[] := ARRAY[
        'license_definition',
        'license_definition_license_id_seq',
        'license_label',
        'license_label_label_id_seq',
        'license_label_extended_mapping',
        'license_label_extended_mapping_mapping_id_seq',
        'license_resource_mapping',
        'license_resource_mapping_mapping_id_seq',
        'license_resource_user_allowance',
        'license_resource_user_allowance_transaction_id_seq',
        'user_registration',
        'user_registration_user_registration_id_seq',
        'user_metadata',
        'user_metadata_user_metadata_id_seq',
        'previewcontent',
        'previewcontent_previewcontent_id_seq',
        'preview2preview'
    ];
    object_name text;
    object_oid oid;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = target_owner) THEN
        RAISE NOTICE 'Role % does not exist, leaving object ownership unchanged.', target_owner;
        RETURN;
    END IF;

    FOREACH object_name IN ARRAY objects LOOP
        -- Unqualified to_regclass() resolves against search_path, so the object is found
        -- in whichever schema this install actually uses.
        object_oid := to_regclass(object_name);
        CONTINUE WHEN object_oid IS NULL;

        CONTINUE WHEN EXISTS (
            SELECT 1 FROM pg_class c
            JOIN pg_roles r ON r.oid = c.relowner
            WHERE c.oid = object_oid AND r.rolname = target_owner
        );

        BEGIN
            EXECUTE format('ALTER TABLE %s OWNER TO %I', object_oid::regclass, target_owner);
        EXCEPTION WHEN insufficient_privilege THEN
            RAISE NOTICE 'Cannot reassign % to %, leaving its current owner in place: %',
                object_oid::regclass, target_owner, SQLERRM;
        END;
    END LOOP;
END $$;
