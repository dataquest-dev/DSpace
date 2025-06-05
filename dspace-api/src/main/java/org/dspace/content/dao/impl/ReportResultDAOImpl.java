/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.content.dao.impl;

import org.dspace.content.ReportResult;
import org.dspace.content.dao.ReportResultDAO;
import org.dspace.core.AbstractHibernateDAO;

/**
 * Database Access Object implementation class for the ReportResult object.
 * This class is responsible for all database calls for the ReportResult object
 * and is autowired by Spring. It extends AbstractHibernateDAO to provide basic CRUD operations.
 *
 * @author Milan Majchrak (milan.majchrak at dataquest.sk)
 */
public class ReportResultDAOImpl extends AbstractHibernateDAO<ReportResult> implements ReportResultDAO {

}
