package org.dspace.xoai.services.impl.resources.functions;

public class GetFundingFn extends StringXSLFunction {
    @Override
    protected String getFnName() {
        return "getFunding";
    }

//    @Override
//    protected String getStringResult(String param) {
//        return uncertainString(ItemUtil.getFunding(param));
//    }
}
