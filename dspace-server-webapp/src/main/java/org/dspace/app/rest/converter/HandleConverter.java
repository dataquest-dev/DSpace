package org.dspace.app.rest.converter;

import org.dspace.app.rest.model.HandleRest;
import org.dspace.app.rest.projection.Projection;
import org.dspace.handle.Handle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class HandleConverter implements DSpaceConverter<Handle, HandleRest> {

    @Override
    public HandleRest convert(Handle modelObject, Projection projection) {
        HandleRest handle = new HandleRest();
        handle.setProjection(projection);
        handle.setId(modelObject.getID());
        handle.setHandle(modelObject.getHandle());
        handle.setDso(modelObject.getDSpaceObject());
        handle.setResourceType(modelObject.getResourceTypeId());
        return handle;
    }

    @Override
    public Class<Handle> getModelClass() {
        return Handle.class;
    }
}
