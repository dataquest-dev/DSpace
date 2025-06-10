/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.content.dao;

import org.dspace.content.ReportResult;
import org.dspace.core.Context;
import org.dspace.core.GenericDAO;

import java.sql.SQLException;
import java.util.Date;

/**
 * Database Access Object interface class for the ReportResult object.
 * The implementation of this class is responsible for all database calls for the ReportResult object
 *
 * @author Milan Majchrak (milan.majchrak at dataquest.sk)
 */
public interface ReportResultDAO extends GenericDAO<ReportResult> {
    ReportResult findByLastModified(Context context, Date lastModified) throws SQLException;

    ReportResult findByLastModifiedAndCheckType(Context context, Date lastModified, int checkType) throws SQLException;
}

