/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.content.service;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Bitstream;
import org.dspace.content.PreviewContent;
import org.dspace.core.Context;

public interface PreviewContentService {

    /**
     * Find the
     * @throws SQLException if database error
     */
    PreviewContent create(Context context, Bitstream bitstream) throws SQLException;
    PreviewContent create(Context context, PreviewContent previewContent) throws SQLException;

    void delete(Context context, PreviewContent previewContent) throws SQLException, AuthorizeException;

    PreviewContent find(Context context, int valueId) throws SQLException;

    /**
     * Find the
     * @throws SQLException
     */
    PreviewContent find(Context context, UUID bitstream_id, String path, long size_bytes) throws SQLException;

    List<PreviewContent> findAll(Context context) throws SQLException, AuthorizeException;

}
