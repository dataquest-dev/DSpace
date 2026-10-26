/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.repository;

import static org.dspace.app.rest.repository.WorkspaceItemRestRepository.BIG_FILE_UPLOAD_ALLOWED_DIR;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeNoException;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import org.dspace.authorize.AuthorizeException;
import org.dspace.authorize.service.AuthorizeService;
import org.dspace.core.Context;
import org.dspace.services.ConfigurationService;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * A workspace item may get a server file attached only by an administrator,
 * and only from the configured directory.
 */
public class WorkspaceItemServerFileTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private final Context context = mock(Context.class);
    private final AuthorizeService authorizeService = mock(AuthorizeService.class);
    private final ConfigurationService configurationService = mock(ConfigurationService.class);
    private WorkspaceItemRestRepository repository;
    private File allowedDir;
    private File outsideFile;

    @Before
    public void setUp() throws Exception {
        repository = mock(WorkspaceItemRestRepository.class, CALLS_REAL_METHODS);
        repository.authorizeService = authorizeService;
        repository.configurationService = configurationService;

        allowedDir = folder.newFolder("allowed");
        outsideFile = folder.newFile("local.cfg");
        when(configurationService.getProperty(BIG_FILE_UPLOAD_ALLOWED_DIR)).thenReturn(allowedDir.getPath());
        when(authorizeService.isAdmin(context)).thenReturn(true);
    }

    @Test
    public void adminGetsFileInsideAllowedDir() throws Exception {
        File file = new File(allowedDir, "data.zip");
        Files.createFile(file.toPath());

        File result = repository.getAllowedServerFile(context, file.getAbsolutePath());

        assertEquals(file.toPath().toRealPath(), result.toPath());
    }

    @Test
    public void nonAdminIsRefused() throws Exception {
        File file = new File(allowedDir, "data.zip");
        Files.createFile(file.toPath());
        when(authorizeService.isAdmin(context)).thenReturn(false);

        assertThrows(AuthorizeException.class,
            () -> repository.getAllowedServerFile(context, file.getAbsolutePath()));
    }

    @Test
    public void refusedWhenAllowedDirIsNotSet() throws Exception {
        when(configurationService.getProperty(BIG_FILE_UPLOAD_ALLOWED_DIR)).thenReturn(null);

        IOException e = assertThrows(IOException.class,
            () -> repository.getAllowedServerFile(context, outsideFile.getAbsolutePath()));
        assertThat(e.getMessage(), containsString(BIG_FILE_UPLOAD_ALLOWED_DIR));
    }

    @Test
    public void fileOutsideAllowedDirIsRefused() {
        IOException e = assertThrows(IOException.class,
            () -> repository.getAllowedServerFile(context, outsideFile.getAbsolutePath()));
        assertThat(e.getMessage(), containsString("Illegal file path"));
    }

    @Test
    public void dotDotEscapeIsRefused() {
        String escape = allowedDir.getAbsolutePath() + File.separator + ".." + File.separator + "local.cfg";

        IOException e = assertThrows(IOException.class, () -> repository.getAllowedServerFile(context, escape));
        assertThat(e.getMessage(), containsString("Illegal file path"));
    }

    @Test
    public void symlinkLeadingOutsideIsRefused() throws Exception {
        File link = new File(allowedDir, "link.cfg");
        try {
            Files.createSymbolicLink(link.toPath(), outsideFile.toPath());
        } catch (IOException | UnsupportedOperationException e) {
            assumeNoException("Symbolic links are not supported here", e);
        }

        IOException e = assertThrows(IOException.class,
            () -> repository.getAllowedServerFile(context, link.getAbsolutePath()));
        assertThat(e.getMessage(), containsString("Illegal file path"));
    }
}
