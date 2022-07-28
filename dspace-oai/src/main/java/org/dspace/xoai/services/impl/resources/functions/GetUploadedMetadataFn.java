package org.dspace.xoai.services.impl.resources.functions;

import org.dspace.app.configuration.OAIWebConfig;
import org.dspace.utils.SpecialItemService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.w3c.dom.Node;


public class GetUploadedMetadataFn extends NodeXslFunction {
    @Override
    protected String getFnName() {
        return "getUploadedMetadata";
    }

    @Override
    protected Node getNode(String param) {
        return new SpecialItemService().getUploadedMetadata(param);
    }
}
