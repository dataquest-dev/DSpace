/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest;

import static org.junit.Assert.assertSame;

import org.dspace.discovery.DiscoverResult;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class DiscoverResultExampleTest {

    @InjectMocks
    private DiscoverResult discoverResult;

    @Test
    public void testBuildCommunity() throws Exception {
        discoverResult.setSearchTime(120);
        assertSame(120, discoverResult.getSearchTime());
    }
}
