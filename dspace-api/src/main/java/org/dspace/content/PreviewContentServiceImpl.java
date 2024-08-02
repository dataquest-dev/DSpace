/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.content;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.dspace.authorize.AuthorizeException;
import org.dspace.authorize.service.AuthorizeService;
import org.dspace.content.dao.PreviewContentDAO;
import org.dspace.content.service.PreviewContentService;
import org.dspace.core.Context;
import org.dspace.core.LogHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class PreviewContentServiceImpl implements PreviewContentService {


    private static final Logger log = LoggerFactory.getLogger(PreviewContentServiceImpl.class);


    @Autowired
    PreviewContentDAO previewContentDAO;


    @Autowired(required = true)
    AuthorizeService authorizeService;

    //For now without the authorization!
    @Override
    public PreviewContent create(Context context, Bitstream bitstream, String name, String content,
                                 boolean isDirectory, String size, Map<String, PreviewContent> subPreviewContents)
            throws SQLException {
        // Create a table row
        PreviewContent previewContent = previewContentDAO.create(context, new PreviewContent(bitstream, name, content,
                isDirectory, size, subPreviewContents));
        log.info(LogHelper.getHeader(context, "create_clarin_content_preview", "clarin_content_preview_id="
                + previewContent.getID()));

        return previewContent;
    }

    //For now without the authorization!
    @Override
    public PreviewContent create(Context context, PreviewContent previewContent) throws SQLException {
        return previewContentDAO.create(context, previewContent);
    }

    @Override
    public void delete(Context context, PreviewContent previewContent) throws SQLException, AuthorizeException {
        if (!authorizeService.isAdmin(context)) {
            throw new AuthorizeException(
                    "You must be an admin to delete an CLARIN Content Preview");
        }
        previewContentDAO.delete(context, previewContent);
    }

    @Override
    public PreviewContent find(Context context, int valueId) throws SQLException {
        return previewContentDAO.findByID(context, PreviewContent.class, valueId);
    }

    @Override
    public List<PreviewContent> findByBitstream(Context context, UUID bitstreamId) throws SQLException {
        return previewContentDAO.findByBitstream(context, bitstreamId);
    }

    @Override
    public List<PreviewContent> findRootByBitstream(Context context, UUID bitstreamId) throws SQLException {
        return previewContentDAO.findRootByBitstream(context, bitstreamId);
    }

    @Override
    public List<PreviewContent> findAll(Context context) throws SQLException, AuthorizeException {
        return previewContentDAO.findAll(context, PreviewContent.class);
    }
}
