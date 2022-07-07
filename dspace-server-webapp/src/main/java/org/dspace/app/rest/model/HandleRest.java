/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.dspace.app.rest.RestResourceController;
import org.dspace.content.DSpaceObject;

public class HandleRest  extends BaseObjectRest<Integer> {
    public static final String NAME = "handle";
    public static final String NAME_PLURAL = "handles";
    public static final String CATEGORY = RestAddressableModel.CORE;

    private String handle;
    @JsonIgnore
    private DSpaceObject dso;
    private Integer resourceTypeId;

    public String getHandle() {
        return handle;
    }

    public DSpaceObject getDso() {
        return dso;
    }

    public Integer getResourceTypeId() {
        return resourceTypeId;
    }

    public void setHandle(String handle) {
        this.handle = handle;
    }

    public void setDso(DSpaceObject dso) {
        this.dso = dso;
    }

    public void setResourceTypeId(Integer resourceTypeId) {
        this.resourceTypeId = resourceTypeId;
    }


    @Override
    public String getCategory() {
        return CATEGORY;
    }

    @Override
    public Class getController() {
        return RestResourceController.class;
    }

    @Override
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public String getType() {
        return NAME;
    }
}
