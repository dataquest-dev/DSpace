package org.dspace.xoai.services.impl.resources.functions;

public class StringReplaceFn extends StringXSLFunction {

    @Override
    protected String getFnName() {
        return "stringReplace";
    }

    @Override
    protected String getStringResult(String param) {
        return param.replaceFirst("http://", "https://");
    }
}
