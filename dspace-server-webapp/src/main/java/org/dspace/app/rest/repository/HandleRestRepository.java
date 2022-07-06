package org.dspace.app.rest.repository;

import org.apache.logging.log4j.Logger;
import org.dspace.app.rest.model.HandleRest;
import org.dspace.core.Context;
import org.dspace.handle.Handle;
import org.dspace.handle.HandleServiceImplClarin;
import org.dspace.handle.dao.HandleDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.util.List;

@Component(HandleRest.CATEGORY + "." + HandleRest.NAME)
public class HandleRestRepository extends  DSpaceRestRepository<HandleRest, Integer> {
    /**
     * log4j logger
     */
    private static Logger log = org.apache.logging.log4j.LogManager.getLogger(HandleRestRepository.class);

    HandleServiceImplClarin handleServiceImplClarin = new HandleServiceImplClarin();

    @Override
    public HandleRest findOne(Context context, Integer integer) {
        return null;
    }

    //pokus
    @Override
    public Page<HandleRest> findAll(Context context, Pageable pageable) {
        try {
            List<Handle> handles = handleServiceImplClarin.findAll(context);
            return converter.toRestPage(handles, pageable, utils.obtainProjection());
        } catch (SQLException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @Override
    public Class<HandleRest> getDomainClass() {
        return null;
    }
}
