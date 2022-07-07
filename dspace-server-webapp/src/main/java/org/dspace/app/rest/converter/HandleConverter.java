package org.dspace.app.rest.converter;

import org.dspace.app.rest.model.HandleRest;
import org.dspace.app.rest.projection.Projection;
import org.dspace.handle.Handle;
import org.springframework.stereotype.Component;

@Component
public class HandleConverter implements DSpaceConverter<Handle, HandleRest> {

    @Override
    public HandleRest convert(Handle modelObject, Projection projection) {
        HandleRest handleRest = new HandleRest();
        handleRest.setProjection(projection);
        handleRest.setId(modelObject.getID());
        handleRest.setHandle(modelObject.getHandle());
        handleRest.setDso(modelObject.getDSpaceObject());
        handleRest.setResourceTypeID(modelObject.getResourceTypeId());
        return handleRest;
    }

    @Override
    public Class<Handle> getModelClass() {
        return Handle.class;
    }
}
