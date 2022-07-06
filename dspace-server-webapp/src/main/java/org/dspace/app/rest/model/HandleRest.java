package org.dspace.app.rest.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.dspace.app.rest.RestResourceController;
import org.dspace.content.DSpaceObject;
public class HandleRest extends BaseObjectRest<Integer> {
    public static final String NAME = "handle";
    public static final String NAME_PLURAL = "handles";
    public static final String CATEGORY = RestAddressableModel.CORE;

    private String handle;
    private DSpaceObject dso; //joinColumn resource_id

    private Integer resourceTypeID;
    //resource_legacy_id v V6.0_2015.03.07_DS-2701_Hibernate_migration.sql - to asi neriesis
    //neriesis asi ani resource_id - uuid
    //-vsetko suvisi s migrate handle table


    public String getHandle() {
        return handle;
    }

    public DSpaceObject getDso() {
        return dso;
    }

    public Integer getResourceTypeID() {
        return resourceTypeID;
    }

    public void setHandle(String handle) {
        this.handle = handle;
    }

    public void setDso(DSpaceObject dso) {
        this.dso = dso;
    }

    public void setResourceType(Integer resourceTypeID) {
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
