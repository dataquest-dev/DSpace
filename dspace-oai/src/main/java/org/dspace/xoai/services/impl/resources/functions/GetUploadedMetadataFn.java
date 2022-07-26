package org.dspace.xoai.services.impl.resources.functions;

import org.dspace.app.configuration.OAIWebConfig;
import org.dspace.utils.SpecialItemService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;


public class GetUploadedMetadataFn extends StringXSLFunction{
    @Override
    protected String getFnName() {
        return "getUploadedMetadata";
    }

    @Autowired
    private SpecialItemService specialItemService;

//    @Override
//    protected String getStringResult(String param) {
//        if (specialItemService == null)
//            return new OAIWebConfig().specialItemService().getUploadedMetadata(param).toString();
//        return specialItemService.getUploadedMetadata(param).toString();
//    }
}
