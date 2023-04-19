/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.submit.step;

import org.atteo.evo.inflector.English;
import org.dspace.app.rest.exception.UnprocessableEntityException;
import org.dspace.app.rest.model.BitstreamRest;
import org.dspace.app.rest.model.patch.Operation;
import org.dspace.app.rest.model.step.DataLicense;
import org.dspace.app.rest.submit.AbstractProcessingStep;
import org.dspace.app.rest.submit.SubmissionService;
import org.dspace.app.rest.submit.factory.PatchOperationFactory;
import org.dspace.app.rest.submit.factory.impl.PatchOperation;
import org.dspace.app.util.SubmissionStepConfig;
import org.dspace.content.Bitstream;
import org.dspace.content.InProgressSubmission;
import org.dspace.core.Constants;
import org.dspace.core.Context;

import javax.servlet.http.HttpServletRequest;
import java.io.Serializable;

/**
 * Clarin Notice step for DSpace Spring Rest. This step will show addition information about the current collection
 * where the item is creating.
 *
 * @author Milan Majchrak (milan.majchrak at dataquest.sk)
 */
public class ClarinNoticeStep extends AbstractProcessingStep {

    private static final String DCTERMS_RIGHTSDATE = "dcterms.accessRights";

    @Override
    public <T extends Serializable> T getData(SubmissionService submissionService, InProgressSubmission obj, SubmissionStepConfig config) throws Exception {
        System.out.println("I am here2");
        return null;
    }

    @Override
    public void doPatchProcessing(Context context, HttpServletRequest currentRequest, InProgressSubmission source,
            Operation op, SubmissionStepConfig stepConf) throws Exception {
        System.out.println("I am here");
    }
}
