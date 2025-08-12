BEGIN;

-- Savepoint before creating the safe_to_date function
SAVEPOINT sp_create_safe_to_date;
-- 1. Safe to_date function (unchanged)
CREATE OR REPLACE FUNCTION safe_to_date(text, text) RETURNS DATE AS $$
DECLARE
    result DATE;
BEGIN
    BEGIN
        result := to_date($1, $2);
    EXCEPTION WHEN OTHERS THEN
        RETURN NULL;
    END;
    RETURN result;
END;
$$ LANGUAGE plpgsql;

-- Savepoint before normalizing year-only and year-month values
SAVEPOINT sp_normalize_year_month;
-- 2a & 2b. Normalize year-only and year-month values
UPDATE public.metadatavalue mv
SET text_value = CASE
    WHEN text_value ~ '^\\d{4}$' THEN text_value || '-01-01T00:00:00Z'
    WHEN text_value ~ '^\\d{4}-\\d{2}$' AND safe_to_date(text_value || '-01', 'YYYY-MM-DD') IS NOT NULL THEN text_value || '-01T00:00:00Z'
    ELSE text_value
END
WHERE mv.metadata_field_id IN (
    SELECT metadata_field_id FROM public.metadatafieldregistry
    WHERE element = 'date' AND qualifier = 'available'
);

-- Savepoint before normalizing full dates and fixing DD-MM-YYYY variants
SAVEPOINT sp_normalize_full_dates;
-- 2c & 3. Normalize full dates (YYYY-MM-DD) and fix DD-MM-YYYY variants with safe_to_date
WITH candidates AS (
    SELECT
        mv.metadata_value_id,
        mv.text_value,
        CASE WHEN text_value ~ '^\\d{4}-\\d{2}-\\d{2}$' THEN text_value ELSE NULL END AS iso_date,
        CASE WHEN text_value ~ '^\\d{2}-\\d{2}-\\d{4}' THEN text_value ELSE NULL END AS dmy_date
    FROM public.metadatavalue mv
    WHERE mv.metadata_field_id IN (
        SELECT metadata_field_id FROM public.metadatafieldregistry
        WHERE element = 'date' AND qualifier = 'available'
    )
),
parsed AS (
    SELECT
        metadata_value_id,
        text_value,
        safe_to_date(iso_date, 'YYYY-MM-DD') AS iso_parsed,
        safe_to_date(substr(dmy_date,7,4) || '-' || substr(dmy_date,4,2) || '-' || substr(dmy_date,1,2), 'YYYY-MM-DD') AS dmy_parsed,
        safe_to_date(substr(dmy_date,7,4) || '-' || substr(dmy_date,1,2) || '-' || substr(dmy_date,4,2), 'YYYY-MM-DD') AS swapped_parsed
    FROM candidates
),
final AS (
    SELECT
        metadata_value_id,
        text_value,
        COALESCE(
            iso_parsed,
            dmy_parsed,
            swapped_parsed
        ) AS final_date
    FROM parsed
    WHERE iso_parsed IS NOT NULL OR dmy_parsed IS NOT NULL OR swapped_parsed IS NOT NULL
)
UPDATE public.metadatavalue mv
SET text_value = to_char(f.final_date, 'YYYY-MM-DD"T00:00:00Z"')
FROM final f
WHERE mv.metadata_value_id = f.metadata_value_id
AND mv.text_value <> to_char(f.final_date, 'YYYY-MM-DD"T00:00:00Z"');

-- Savepoint before handling special date/time formats
SAVEPOINT sp_handle_special_formats;
-- 4. Handle dates like "20. 09. 2019 10:01:31" using to_timestamp and regex filter
WITH candidates AS (
    SELECT
        metadata_value_id,
        text_value,
        to_timestamp(text_value, 'DD. MM. YYYY HH24:MI:SS') AS ts
    FROM public.metadatavalue mv
    WHERE mv.metadata_field_id IN (
        SELECT metadata_field_id FROM public.metadatafieldregistry
        WHERE element = 'date' AND qualifier = 'available'
    )
    AND text_value ~ '^\\d{1,2}\\.\\s*\\d{1,2}\\.\\s*\\d{4}\\s+\\d{2}:\\d{2}:\\d{2}'
),
updates AS (
    SELECT
        metadata_value_id,
        to_char(ts, 'YYYY-MM-DD"T"HH24:MI:SS"Z"') AS new_text_value
    FROM candidates
    WHERE ts IS NOT NULL
)
UPDATE public.metadatavalue mv
SET text_value = u.new_text_value
FROM updates u
WHERE mv.metadata_value_id = u.metadata_value_id
AND mv.text_value <> u.new_text_value;

COMMIT;
