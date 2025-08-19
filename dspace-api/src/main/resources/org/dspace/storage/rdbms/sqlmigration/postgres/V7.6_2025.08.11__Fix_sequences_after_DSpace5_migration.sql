--
-- The contents of this file are subject to the license and copyright
-- detailed in the LICENSE and NOTICE files at the root of the source
-- tree and available online at
--
-- http://www.dspace.org/license/
--

-- ===============================================================
-- WARNING: This script is designed to fix sequence synchronization
-- issues that occur when migrating from DSpace v5 to DSpace v7.
-- It updates all sequences to start from the correct values based
-- on existing data in the database.
-- ===============================================================

-- This migration addresses the issue where sequences are not properly
-- updated after migration from DSpace v5, causing constraint violations
-- during the RegistryUpdater callback execution.

-- Fix all sequences to prevent constraint violations during registry updates
SELECT setval('alert_id_seq', COALESCE((SELECT max(alert_id) FROM systemwidealert), 1));
SELECT setval('bitstreamformatregistry_seq', COALESCE((SELECT max(bitstream_format_id) FROM bitstreamformatregistry), 1));
SELECT setval('checksum_history_check_id_seq', COALESCE((SELECT max(check_id) FROM checksum_history), 1));
SELECT setval('cwf_claimtask_seq', COALESCE((SELECT max(claimtask_id) FROM cwf_claimtask), 1));
SELECT setval('cwf_collectionrole_seq', COALESCE((SELECT max(collectionrole_id) FROM cwf_collectionrole), 1));
SELECT setval('cwf_in_progress_user_seq', COALESCE((SELECT max(in_progress_user_id) FROM cwf_in_progress_user), 1));
SELECT setval('cwf_pooltask_seq', COALESCE((SELECT max(pooltask_id) FROM cwf_pooltask), 1));
SELECT setval('cwf_workflowitem_seq', COALESCE((SELECT max(workflowitem_id) FROM cwf_workflowitem), 1));
SELECT setval('cwf_workflowitemrole_seq', COALESCE((SELECT max(workflowitemrole_id) FROM cwf_workflowitemrole), 1));
SELECT setval('doi_seq', COALESCE((SELECT max(doi_id) FROM doi), 1));
SELECT setval('entity_type_id_seq', COALESCE((SELECT max(id) FROM entity_type), 1));
SELECT setval('fileextension_seq', COALESCE((SELECT max(file_extension_id) FROM fileextension), 1));
SELECT setval('handle_id_seq', COALESCE((SELECT max(handle_id) FROM handle), 1));
SELECT setval('harvested_collection_seq', COALESCE((SELECT max(id) FROM harvested_collection), 1));
SELECT setval('harvested_item_seq', COALESCE((SELECT max(id) FROM harvested_item), 1));
SELECT setval('metadatafieldregistry_seq', COALESCE((SELECT max(metadata_field_id) FROM metadatafieldregistry), 1));
SELECT setval('metadataschemaregistry_seq', COALESCE((SELECT max(metadata_schema_id) FROM metadataschemaregistry), 1));
SELECT setval('metadatavalue_seq', COALESCE((SELECT max(metadata_value_id) FROM metadatavalue), 1));
SELECT setval('openurltracker_seq', COALESCE((SELECT max(tracker_id) FROM openurltracker), 1));
SELECT setval('orcid_history_id_seq', COALESCE((SELECT max(id) FROM orcid_history), 1));
SELECT setval('orcid_queue_id_seq', COALESCE((SELECT max(id) FROM orcid_queue), 1));
SELECT setval('orcid_token_id_seq', COALESCE((SELECT max(id) FROM orcid_token), 1));
SELECT setval('process_id_seq', COALESCE((SELECT max(process_id) FROM process), 1));
SELECT setval('registrationdata_seq', COALESCE((SELECT max(registrationdata_id) FROM registrationdata), 1));
SELECT setval('relationship_id_seq', COALESCE((SELECT max(id) FROM relationship), 1));
SELECT setval('relationship_type_id_seq', COALESCE((SELECT max(id) FROM relationship_type), 1));
SELECT setval('requestitem_seq', COALESCE((SELECT max(requestitem_id) FROM requestitem), 1));
SELECT setval('resourcepolicy_seq', COALESCE((SELECT max(policy_id) FROM resourcepolicy), 1));
SELECT setval('subscription_parameter_seq', COALESCE((SELECT max(subscription_id) FROM subscription_parameter), 1));
SELECT setval('subscription_seq', COALESCE((SELECT max(subscription_id) FROM subscription), 1));

-- Handle sequences that might not exist in all DSpace installations
DO $$
BEGIN
    -- Fix supervision_orders sequence if it exists
    IF EXISTS (SELECT 1 FROM information_schema.sequences WHERE sequence_name = 'supervision_orders_id_seq') THEN
        PERFORM setval('supervision_orders_id_seq', COALESCE((SELECT max(id) FROM supervision_orders), 1));
    END IF;

    -- Fix versionhistory sequence if it exists
    IF EXISTS (SELECT 1 FROM information_schema.sequences WHERE sequence_name = 'versionhistory_seq') THEN
        PERFORM setval('versionhistory_seq', COALESCE((SELECT max(versionhistory_id) FROM versionhistory), 1));
    END IF;

    -- Fix versionitem sequence if it exists
    IF EXISTS (SELECT 1 FROM information_schema.sequences WHERE sequence_name = 'versionitem_seq') THEN
        PERFORM setval('versionitem_seq', COALESCE((SELECT max(versionitem_id) FROM versionitem), 1));
    END IF;

    -- Fix workspaceitem sequence if it exists
    IF EXISTS (SELECT 1 FROM information_schema.sequences WHERE sequence_name = 'workspaceitem_seq') THEN
        PERFORM setval('workspaceitem_seq', COALESCE((SELECT max(workspace_item_id) FROM workspaceitem), 1));
    END IF;

    -- Fix most_recent_checksum sequence if it exists
    IF EXISTS (SELECT 1 FROM information_schema.sequences WHERE sequence_name = 'most_recent_checksum_seq') THEN
        PERFORM setval('most_recent_checksum_seq', COALESCE((SELECT max(result_id) FROM most_recent_checksum), 1));
    END IF;

    -- Fix checksum_history sequence if it exists
    IF EXISTS (SELECT 1 FROM information_schema.sequences WHERE sequence_name = 'checksum_history_seq') THEN
        PERFORM setval('checksum_history_seq', COALESCE((SELECT max(result_id) FROM checksum_history), 1));
    END IF;

    -- Fix preview sequences if they exist
    IF EXISTS (SELECT 1 FROM information_schema.sequences WHERE sequence_name = 'preview_content_seq') THEN
        PERFORM setval('preview_content_seq', COALESCE((SELECT max(preview_content_id) FROM preview_content), 1));
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.sequences WHERE sequence_name = 'preview_content_bitstream_seq') THEN
        PERFORM setval('preview_content_bitstream_seq', COALESCE((SELECT max(preview_content_bitstream_id) FROM preview_content_bitstream), 1));
    END IF;
END $$;
