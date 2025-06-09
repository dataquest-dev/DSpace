-- ===================================================================
-- PERFORMANCE INDEXES
-- ===================================================================

--
-- Index to speed up queries filtering previewcontent by bitstream_id,
-- used in hasPreview() and getPreview() JOIN with bitstream table.
--
CREATE INDEX idx_previewcontent_bitstream_id
ON previewcontent (bitstream_id);

--
-- Index to optimize NOT EXISTS subquery in getPreview(),
-- checking for existence of child_id in preview2preview.
--
CREATE INDEX idx_preview2preview_child_id
ON preview2preview (child_id);