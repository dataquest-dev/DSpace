/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.scripts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.dspace.AbstractIntegrationTestWithDatabase;
import org.dspace.builder.ProcessBuilder;
import org.dspace.content.ProcessStatus;
import org.dspace.scripts.factory.ScriptServiceFactory;
import org.dspace.scripts.service.ProcessService;
import org.junit.Test;

/**
 * Integration tests for {@link ProcessServiceImpl}.
 */
public class ProcessServiceIT extends AbstractIntegrationTestWithDatabase {

    private final ProcessService processService = ScriptServiceFactory.getInstance().getProcessService();

    @Test
    public void failRunningProcessesAttachesLogWithoutCurrentUser() throws Exception {
        // The builder saves the process, which only its owner or an admin may do
        context.setCurrentUser(admin);
        Process process = ProcessBuilder.createProcess(context, admin, "test", List.of())
            .withProcessStatus(ProcessStatus.RUNNING)
            .withCreationTime(Instant.now().minus(1, ChronoUnit.HOURS))
            .build();
        // Same state as at startup: no user is logged in
        context.setCurrentUser(null);

        processService.failRunningProcesses(context);

        process = context.reloadEntity(process);
        assertEquals(ProcessStatus.FAILED, process.getProcessStatus());
        assertNotNull(processService.getBitstream(context, process, Process.OUTPUT_TYPE));
    }
}
