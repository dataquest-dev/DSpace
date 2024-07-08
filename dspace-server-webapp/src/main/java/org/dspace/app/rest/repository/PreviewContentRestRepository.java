package org.dspace.app.rest.repository;

import org.dspace.app.rest.model.PreviewContentRest;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.PreviewContent;
import org.dspace.content.service.PreviewContentService;
import org.dspace.core.Context;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

@Component(PreviewContentRest.CATEGORY + "." + PreviewContentRest.NAME)
public class PreviewContentRestRepository extends DSpaceRestRepository<PreviewContentRest, Integer> {


    @Autowired
    PreviewContentService previewContentService;

    @Override
    public PreviewContentRest findOne(Context context, Integer integer) {
        PreviewContent previewContent;
        try {
            previewContent = previewContentService.find(context, integer);
        } catch (SQLException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
        if (Objects.isNull(previewContent)) {
            return null;
        }
        return converter.toRest(previewContent, utils.obtainProjection());
    }

    @Override
    public Page<PreviewContentRest> findAll(Context context, Pageable pageable) {
        try {
            List<PreviewContent> previewContentList = previewContentService.findAll(context);
            return converter.toRestPage(previewContentList, pageable, utils.obtainProjection());
        } catch (SQLException | AuthorizeException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @Override
    public Class<PreviewContentRest> getDomainClass() {
        return PreviewContentRest.class;
    }
}
