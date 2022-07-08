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

public class HandleRest extends BaseObjectRest<Integer> {
    public static final String NAME = "handle";
    public static final String NAME_PLURAL = "handles";
    public static final String CATEGORY = RestAddressableModel.CORE;

    private String handle;

    @JsonIgnore
    private DSpaceObjectRest dso;
    private Integer resourceTypeID;

    public String getHandle() {
        return handle;
    }

    public DSpaceObjectRest getDspaceObject() {
        return dso;
    }

    public Integer getResourceTypeID() {
        return resourceTypeID;
    }

    public void setHandle(String handle) {
        this.handle = handle;
    }

    public void setDso(DSpaceObjectRest dso) {
        this.dso = dso;
    }

    public void setResourceTypeID(Integer resourceTypeID) {
        this.resourceTypeID = resourceTypeID;
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
