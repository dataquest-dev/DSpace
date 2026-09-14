--
-- The contents of this file are subject to the license and copyright
-- detailed in the LICENSE and NOTICE files at the root of the source
-- tree and available online at
--
-- http://www.dspace.org/license/
--

-----------------------------------------------------------------------------------
-- Schema-agnostic restatement of the CLARIN schema created by
--   V7.2_2022.07.28__Upgrade_to_Lindat_Clarin_schema.sql
--   V7.6_2024.08.05__Added_Preview_Tables.sql
--
-- Those two migrations qualify their `ALTER TABLE ... OWNER TO dspace` statements with
-- `public.`, so they ignore the schema configured in `db.schema` (dspace.cfg). They are
-- already applied and cannot be edited without breaking their Flyway checksum, so this
-- migration carries the same DDL with every object name unqualified -- each one resolves
-- through the connection's search_path instead.
--
-- Everything here is idempotent: an install that already ran the originals gets no
-- changes at all. Sequence baselines are applied only while the owning table is still
-- empty, and ownership is skipped whenever the statement would fail.
-----------------------------------------------------------------------------------

-- HANDLE TABLE
ALTER TABLE handle ADD COLUMN IF NOT EXISTS url varchar;
ALTER TABLE handle ADD COLUMN IF NOT EXISTS dead BOOL;
ALTER TABLE handle ADD COLUMN IF NOT EXISTS dead_since TIMESTAMP WITH TIME ZONE;

-- MetadataField table
-- Because of metashareSchema
ALTER TABLE metadatafieldregistry ALTER COLUMN element TYPE character varying(128);

ALTER TABLE eperson ALTER COLUMN netid TYPE character varying(256);
ALTER TABLE eperson ALTER COLUMN email TYPE character varying(256);
ALTER TABLE eperson ADD COLUMN IF NOT EXISTS welcome_info varchar(30);
ALTER TABLE eperson ADD COLUMN IF NOT EXISTS can_edit_submission_metadata BOOL;

-- LICENSES
CREATE TABLE IF NOT EXISTS license_definition (
    license_id integer NOT NULL,
    name varchar(256),
    definition varchar(256),
    user_registration_id integer,
    label_id integer,
    created_on timestamp,
    confirmation integer DEFAULT 0,
    required_info varchar(256)
);

CREATE TABLE IF NOT EXISTS license_label (
    label_id integer NOT NULL,
    label varchar(5),
    title varchar(180),
    icon bytea,
    is_extended boolean DEFAULT false
);

CREATE TABLE IF NOT EXISTS license_label_extended_mapping (
    mapping_id integer NOT NULL,
    license_id integer,
    label_id integer
);

CREATE TABLE IF NOT EXISTS license_resource_mapping (
    mapping_id integer NOT NULL,
    bitstream_uuid uuid,
    license_id integer
);

CREATE TABLE IF NOT EXISTS license_resource_user_allowance (
    transaction_id integer NOT NULL,
    user_registration_id integer,
    mapping_id integer,
    created_on timestamp,
    token varchar(256)
);

CREATE TABLE IF NOT EXISTS user_registration (
    user_registration_id integer NOT NULL,
    eperson_id UUID,
    email character varying(256),
    organization character varying(256),
    confirmation boolean DEFAULT true
);

CREATE TABLE IF NOT EXISTS user_metadata (
    user_metadata_id integer NOT NULL,
    user_registration_id integer,
    metadata_key character varying(64),
    metadata_value character varying(256),
    transaction_id integer
);

CREATE TABLE IF NOT EXISTS verification_token (
    verification_token_id integer NOT NULL,
    eperson_netid varchar(256),
    shib_headers varchar(2048),
    token varchar(256),
    email varchar(256)
);

-- PREVIEW CONTENT
CREATE TABLE IF NOT EXISTS previewcontent (
   previewcontent_id integer NOT NULL,
   bitstream_id uuid NOT NULL,
   name varchar(2000),
   content varchar(2000),
   isDirectory boolean DEFAULT false,
   size varchar(256)
);

CREATE TABLE IF NOT EXISTS preview2preview (
    parent_id integer NOT NULL,
    child_id integer NOT NULL,
    name varchar(2000)
);

-- SEQUENCES
CREATE SEQUENCE IF NOT EXISTS license_definition_license_id_seq
    START WITH 1 INCREMENT BY 1 NO MAXVALUE NO MINVALUE CACHE 1;

CREATE SEQUENCE IF NOT EXISTS license_label_label_id_seq
    START WITH 1 INCREMENT BY 1 NO MAXVALUE NO MINVALUE CACHE 1;

CREATE SEQUENCE IF NOT EXISTS license_label_extended_mapping_mapping_id_seq
    START WITH 1 INCREMENT BY 1 NO MAXVALUE NO MINVALUE CACHE 1;

CREATE SEQUENCE IF NOT EXISTS license_resource_mapping_mapping_id_seq
    START WITH 1 INCREMENT BY 1 NO MAXVALUE NO MINVALUE CACHE 1;

CREATE SEQUENCE IF NOT EXISTS license_resource_user_allowance_transaction_id_seq
    START WITH 1 INCREMENT BY 1 NO MAXVALUE NO MINVALUE CACHE 1;

CREATE SEQUENCE IF NOT EXISTS user_registration_user_registration_id_seq
    START WITH 1 INCREMENT BY 1 NO MAXVALUE NO MINVALUE CACHE 1;

CREATE SEQUENCE IF NOT EXISTS user_metadata_user_metadata_id_seq
    START WITH 1 INCREMENT BY 1 NO MAXVALUE NO MINVALUE CACHE 1;

CREATE SEQUENCE IF NOT EXISTS verification_token_verification_token_id_seq
    START WITH 1 INCREMENT BY 1 NO MAXVALUE NO MINVALUE CACHE 1;

CREATE SEQUENCE IF NOT EXISTS previewcontent_previewcontent_id_seq
    START WITH 1 INCREMENT BY 1 NO MAXVALUE NO MINVALUE CACHE 1;

ALTER SEQUENCE license_definition_license_id_seq OWNED BY license_definition.license_id;
ALTER SEQUENCE license_label_label_id_seq OWNED BY license_label.label_id;
ALTER SEQUENCE license_label_extended_mapping_mapping_id_seq OWNED BY license_label_extended_mapping.mapping_id;
ALTER SEQUENCE license_resource_mapping_mapping_id_seq OWNED BY license_resource_mapping.mapping_id;
ALTER SEQUENCE license_resource_user_allowance_transaction_id_seq OWNED BY license_resource_user_allowance.transaction_id;
ALTER SEQUENCE user_metadata_user_metadata_id_seq OWNED BY user_metadata.user_metadata_id;
ALTER SEQUENCE previewcontent_previewcontent_id_seq OWNED BY previewcontent.previewcontent_id;

-- The original left this one commented out; it stays that way.
--ALTER SEQUENCE user_registration_user_registration_id_seq OWNED BY user_registration.eperson_id;

-- COLUMN DEFAULTS
ALTER TABLE ONLY license_definition ALTER COLUMN license_id SET DEFAULT nextval('license_definition_license_id_seq'::regclass);
ALTER TABLE ONLY license_label ALTER COLUMN label_id SET DEFAULT nextval('license_label_label_id_seq'::regclass);
ALTER TABLE ONLY license_label_extended_mapping ALTER COLUMN mapping_id SET DEFAULT nextval('license_label_extended_mapping_mapping_id_seq'::regclass);
ALTER TABLE ONLY license_resource_mapping ALTER COLUMN mapping_id SET DEFAULT nextval('license_resource_mapping_mapping_id_seq'::regclass);
ALTER TABLE ONLY license_resource_user_allowance ALTER COLUMN transaction_id SET DEFAULT nextval('license_resource_user_allowance_transaction_id_seq'::regclass);
ALTER TABLE ONLY user_metadata ALTER COLUMN user_metadata_id SET DEFAULT nextval('user_metadata_user_metadata_id_seq'::regclass);
ALTER TABLE ONLY verification_token ALTER COLUMN verification_token_id SET DEFAULT nextval('verification_token_verification_token_id_seq'::regclass);

-- The original left this one commented out; it stays that way.
--ALTER TABLE ONLY user_registration ALTER COLUMN eperson_id SET DEFAULT nextval('user_registration_user_registration_id_seq'::regclass);

-- SEQUENCE BASELINES
-- The originals set these to the values carried over from the CLARIN 5 migration. Applying
-- them to a populated table would rewind a live sequence, so each one runs only while its
-- owning table is still empty.
DO $$
DECLARE
    baseline record;
    table_has_rows boolean;
BEGIN
    FOR baseline IN
        SELECT * FROM (VALUES
            ('license_label_extended_mapping_mapping_id_seq',      'license_label_extended_mapping',  991137),
            ('license_label_label_id_seq',                         'license_label',                       19),
            ('license_resource_mapping_mapping_id_seq',            'license_resource_mapping',          1382),
            ('license_resource_user_allowance_transaction_id_seq', 'license_resource_user_allowance',    241),
            ('user_metadata_user_metadata_id_seq',                 'user_metadata',                       68)
        ) AS t(sequence_name, table_name, last_value)
    LOOP
        CONTINUE WHEN to_regclass(baseline.sequence_name) IS NULL;
        CONTINUE WHEN to_regclass(baseline.table_name) IS NULL;

        EXECUTE format('SELECT EXISTS (SELECT 1 FROM %s)', to_regclass(baseline.table_name))
            INTO table_has_rows;
        CONTINUE WHEN table_has_rows;

        PERFORM setval(to_regclass(baseline.sequence_name), baseline.last_value, true);
    END LOOP;
END $$;

-- CONSTRAINTS
DO $$
DECLARE
    c record;
BEGIN
    FOR c IN
        SELECT * FROM (VALUES
            ('license_definition',              'license_definition_pkey',              'PRIMARY KEY (license_id)'),
            ('license_label',                   'license_label_pkey',                   'PRIMARY KEY (label_id)'),
            ('license_label_extended_mapping',  'license_label_extended_mapping_pkey',  'PRIMARY KEY (mapping_id)'),
            ('license_resource_mapping',        'license_resource_mapping_pkey',        'PRIMARY KEY (mapping_id)'),
            ('license_resource_user_allowance', 'license_resource_user_allowance_pkey', 'PRIMARY KEY (transaction_id)'),
            ('user_registration',               'user_registration_pkey',               'PRIMARY KEY (user_registration_id)'),
            ('user_metadata',                   'user_metadata_pkey',                   'PRIMARY KEY (user_metadata_id)'),
            ('verification_token',              'verification_token_pkey',              'PRIMARY KEY (verification_token_id)'),
            ('previewcontent',                  'previewcontent_pkey',                  'PRIMARY KEY (previewcontent_id)'),
            ('preview2preview',                 'preview2preview_pkey',                 'PRIMARY KEY (parent_id, child_id)'),
            ('license_label_extended_mapping',  'license_definition_license_label_extended_mapping_fk',
                'FOREIGN KEY (license_id) REFERENCES license_definition(license_id) ON DELETE CASCADE'),
            ('license_label_extended_mapping',  'license_label_license_label_extended_mapping_fk',
                'FOREIGN KEY (label_id) REFERENCES license_label(label_id) ON DELETE CASCADE'),
            ('license_resource_mapping',        'license_definition_license_resource_mapping_fk',
                'FOREIGN KEY (license_id) REFERENCES license_definition(license_id) ON DELETE CASCADE'),
            ('license_resource_mapping',        'bitstream_license_resource_mapping_fk',
                'FOREIGN KEY (bitstream_uuid) REFERENCES bitstream(uuid) ON DELETE CASCADE'),
            ('license_resource_user_allowance', 'license_resource_mapping_license_resource_user_allowance_fk',
                'FOREIGN KEY (mapping_id) REFERENCES license_resource_mapping(mapping_id) ON UPDATE CASCADE ON DELETE CASCADE'),
            ('license_resource_user_allowance', 'user_registration_license_resource_user_allowance_fk',
                'FOREIGN KEY (user_registration_id) REFERENCES user_registration(user_registration_id)'),
            ('license_definition',              'user_registration_license_definition_fk',
                'FOREIGN KEY (user_registration_id) REFERENCES user_registration(user_registration_id)'),
            ('user_metadata',                   'license_resource_user_allowance_user_metadata_fk',
                'FOREIGN KEY (transaction_id) REFERENCES license_resource_user_allowance(transaction_id) ON UPDATE CASCADE ON DELETE CASCADE'),
            ('user_metadata',                   'user_registration_user_metadata_fk',
                'FOREIGN KEY (user_registration_id) REFERENCES user_registration(user_registration_id)'),
            ('previewcontent',                  'previewcontent_bitstream_fk',
                'FOREIGN KEY (bitstream_id) REFERENCES bitstream(uuid) ON DELETE CASCADE'),
            ('preview2preview',                 'preview2preview_parent_fk',
                'FOREIGN KEY (parent_id) REFERENCES previewcontent(previewcontent_id) ON DELETE CASCADE'),
            ('preview2preview',                 'preview2preview_child_fk',
                'FOREIGN KEY (child_id) REFERENCES previewcontent(previewcontent_id) ON DELETE CASCADE')
        ) AS t(table_name, constraint_name, definition)
    LOOP
        CONTINUE WHEN to_regclass(c.table_name) IS NULL;
        CONTINUE WHEN EXISTS (
            SELECT 1 FROM pg_constraint
            WHERE conname = c.constraint_name AND conrelid = to_regclass(c.table_name)
        );

        EXECUTE format('ALTER TABLE %s ADD CONSTRAINT %I %s',
                       to_regclass(c.table_name), c.constraint_name, c.definition);
    END LOOP;
END $$;

-- The original left this one commented out; it stays that way.
--ALTER TABLE ONLY license_definition
--    ADD CONSTRAINT license_label_license_definition_fk FOREIGN KEY (label_id) REFERENCES license_label(label_id);

CREATE UNIQUE INDEX IF NOT EXISTS license_definition_license_id_key ON license_definition USING btree (name);

-- OWNERSHIP
-- The statements this migration exists for. Unqualified, so they apply to the schema the
-- connection actually uses. Skipped when the role is missing, when the object already
-- belongs to it, or when the current role may not reassign it -- an install whose objects
-- belong to a tenant role keeps them.
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
        object_oid := to_regclass(object_name);
        CONTINUE WHEN object_oid IS NULL;
        CONTINUE WHEN EXISTS (
            SELECT 1 FROM pg_class cls
            JOIN pg_roles r ON r.oid = cls.relowner
            WHERE cls.oid = object_oid AND r.rolname = target_owner
        );

        BEGIN
            EXECUTE format('ALTER TABLE %s OWNER TO %I', object_oid::regclass, target_owner);
        EXCEPTION WHEN insufficient_privilege THEN
            RAISE NOTICE 'Cannot reassign % to %, leaving its current owner in place: %',
                object_oid::regclass, target_owner, SQLERRM;
        END;
    END LOOP;
END $$;
