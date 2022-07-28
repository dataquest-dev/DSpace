package org.dspace.xoai.services.impl.resources.functions;

import org.dspace.utils.SpecialItemService;
import org.w3c.dom.Node;

public class GetSizeFn extends NodeXslFunction {
    @Override
    protected String getFnName() {
        return "getSize";
    }

    @Override
    protected Node getNode(String param) {
        return new SpecialItemService().getSize(param);
    }
}
