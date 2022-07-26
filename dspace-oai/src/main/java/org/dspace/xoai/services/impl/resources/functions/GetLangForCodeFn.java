package org.dspace.xoai.services.impl.resources.functions;

import org.dspace.utils.IsoLangCodes;

public class GetLangForCodeFn extends StringXSLFunction {
    @Override
    protected String getFnName() {
        return "getLangForCode";
    }

    @Override
    protected String getStringResult(String param) {
        return uncertainString(IsoLangCodes.getLangForCode(param));
    }
}
