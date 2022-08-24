/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.handle.dao.impl;

import org.dspace.core.AbstractHibernateDAO;
import org.dspace.core.Context;
import org.dspace.handle.Handle;
import org.dspace.handle.dao.HandleClarinDAO;
import org.dspace.handle.dao.HandleDAO;
import org.springframework.beans.factory.annotation.Autowired;

import javax.persistence.Query;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

public class HandleClarinDAOImpl extends AbstractHibernateDAO<Handle> implements HandleClarinDAO {

    @Autowired
    HandleDAO handleDAO;

    @Override
    public Handle findByHandle(Context context, String handle) throws SQLException {
        Query query = createQuery(context,
                "SELECT h " +
                        "FROM Handle h " +
                        "WHERE h.handle = :handle ");

        query.setParameter("handle", handle);

        query.setHint("org.hibernate.cacheable", Boolean.TRUE);
        return singleResult(query);
    }

//    @Override
//    public Handle create(Context context, Handle handle) throws SQLException {
//        return handleDAO.create(context, handle);
//    }
//
//    @Override
//    public void save(Context context, Handle handle) throws SQLException {
//        handleDAO.save(context, handle);
//    }
//
//    @Override
//    public void delete(Context context, Handle handle) throws SQLException {
//        handleDAO.delete(context, handle);
//    }
//
//    @Override
//    public List<Handle> findAll(Context context, Class<Handle> clazz) throws SQLException {
//        return handleDAO.findAll(context, clazz);
//    }
//
//    @Override
//    public List<Handle> findAll(Context context, Class<Handle> clazz, Integer limit, Integer offset) throws SQLException {
//        return handleDAO.findAll(context, clazz, limit, offset);
//    }
//
//    @Override
//    public Handle findUnique(Context context, String query) throws SQLException {
//        return handleDAO.findUnique(context, query);
//    }
//
//    @Override
//    public Handle findByID(Context context, Class clazz, int id) throws SQLException {
//        return handleDAO.findByID(context, clazz, id);
//    }
//
//    @Override
//    public Handle findByID(Context context, Class clazz, UUID id) throws SQLException {
//        return handleDAO.findByID(context, clazz, id);
//    }
//
//    @Override
//    public List<Handle> findMany(Context context, String query) throws SQLException {
//        return handleDAO.findMany(context, query);
//    }
}
