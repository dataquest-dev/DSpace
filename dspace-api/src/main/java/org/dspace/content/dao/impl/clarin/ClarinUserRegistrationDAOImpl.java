/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.content.dao.impl.clarin;

import org.dspace.content.clarin.ClarinUserRegistration;
import org.dspace.content.dao.clarin.ClarinUserRegistrationDAO;
import org.dspace.core.AbstractHibernateDAO;

public class ClarinUserRegistrationDAOImpl extends AbstractHibernateDAO<ClarinUserRegistration>
        implements ClarinUserRegistrationDAO {

    protected ClarinUserRegistrationDAOImpl() {
        super();
    }
}
