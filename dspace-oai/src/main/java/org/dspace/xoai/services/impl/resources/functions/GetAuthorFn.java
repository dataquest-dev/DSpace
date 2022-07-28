package org.dspace.xoai.services.impl.resources.functions;

import org.dspace.utils.SpecialItemService;
import org.w3c.dom.Node;

public class GetAuthorFn extends NodeXslFunction {
    @Override
    protected String getFnName() {
        return "getAuthor";
    }

    @Override
    protected Node getNode(String param) {
        return new SpecialItemService().getAuthor(param);
    }
}
