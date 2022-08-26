package org.dspace.handle.dao.impl;

import org.dspace.core.AbstractHibernateDAO;
import org.dspace.core.Context;
import org.dspace.handle.Handle;
import org.dspace.handle.Handle_;
import org.dspace.handle.dao.HandleClarinDAO;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;
import java.sql.SQLException;
import java.util.List;

public class HandleClarinDAOImpl extends AbstractHibernateDAO<Handle> implements HandleClarinDAO {
    @Override
    public List<Handle> findAll(Context context, String sortingColumn, boolean direction, int maxResult, int offset)
            throws SQLException {

        CriteriaBuilder criteriaBuilder = getCriteriaBuilder(context);
        CriteriaQuery criteriaQuery = getCriteriaQuery(criteriaBuilder, Handle.class);
        Root<Handle> handleRoot = criteriaQuery.from(Handle.class);
        criteriaQuery.select(handleRoot);
        criteriaQuery.where(criteriaBuilder.like(handleRoot.get(Handle_.handle), sortingColumn + "%"));
        return list(context, criteriaQuery, false, Handle.class, maxResult, offset);
    }
}
