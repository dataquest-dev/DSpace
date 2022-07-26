package org.dspace.xoai.services.impl.resources.functions;


public class GetContactFn extends StringXSLFunction {

    @Override
    protected String getFnName() {
        return "getContact";
    }

//    @Override
//    protected String getStringResult(String param) {
//        return uncertainString(ItemUtil.getContact(param));
//    }
}
