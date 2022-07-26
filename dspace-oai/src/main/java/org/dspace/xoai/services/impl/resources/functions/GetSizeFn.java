package org.dspace.xoai.services.impl.resources.functions;

public class GetSizeFn extends StringXSLFunction {
    @Override
    protected String getFnName() {
        return "getSize";
    }

//    @Override
//    protected String getStringResult(String param) {
//        return uncertainString(ItemUtil.getSize(param));
//    }
}
