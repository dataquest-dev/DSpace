/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.converter;

import org.dspace.app.rest.model.DSpaceObjectRest;
import org.dspace.app.rest.model.HandleRest;
import org.dspace.app.rest.projection.Projection;
import org.dspace.handle.Handle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class HandleConverter implements DSpaceConverter<Handle, HandleRest> {

    @Autowired
    private ConverterService converter;

    @Override
    public HandleRest convert(Handle modelObject, Projection projection) {
        HandleRest handleRest = new HandleRest();
        handleRest.setProjection(projection);
        handleRest.setId(modelObject.getID());
        handleRest.setHandle(modelObject.getHandle());
        DSpaceObjectRest obj = converter.toRest(modelObject.getDSpaceObject(), projection);
        handleRest.setDso(obj);
        handleRest.setResourceTypeID(modelObject.getResourceTypeId());
        return handleRest;
    }

    @Override
    public Class<Handle> getModelClass() {
        return Handle.class;
    }
}
