/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.content.service;

import java.sql.SQLException;

import org.dspace.content.ReportResult;
import org.dspace.content.ReportResultServiceImpl;
import org.dspace.core.Context;

/**
 * Service interface for managing ReportResult objects.
 * This interface defines methods for creating, finding, deleting, and updating ReportResult instances.
 * It is intended to be used by the ReportResultServiceImpl class.
 * @see ReportResultServiceImpl
 *
 * @author Milan Majchrak (milan.majchrak at dataquest.sk)
 */
public interface ReportResultService {

    ReportResult create(Context context) throws SQLException;

    ReportResult create(Context context, ReportResult reportResult) throws SQLException;

    ReportResult find(Context context, int id) throws SQLException;

    void delete(Context context, ReportResult reportResult) throws SQLException;

    void update(Context context, ReportResult reportResult) throws SQLException;
}
