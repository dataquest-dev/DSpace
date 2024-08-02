/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.content.dao;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import org.dspace.content.PreviewContent;
import org.dspace.core.Context;
import org.dspace.core.GenericDAO;

public interface PreviewContentDAO extends GenericDAO<PreviewContent> {
    List<PreviewContent> findByBitstream(Context context, UUID bitstream_id) throws SQLException;
    List<PreviewContent> findRootByBitstream(Context context, UUID bitstream_id) throws SQLException;
}
