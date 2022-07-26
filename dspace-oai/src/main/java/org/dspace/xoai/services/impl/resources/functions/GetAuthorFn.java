package org.dspace.xoai.services.impl.resources.functions;

public class GetAuthorFn extends StringXSLFunction {
    @Override
    protected String getFnName() {
        return "getAuthor";
    }

//    @Override
//    protected String getStringResult(String param) {
//        return uncertainString(ItemUtil.getAuthor(param));
//    }
}
