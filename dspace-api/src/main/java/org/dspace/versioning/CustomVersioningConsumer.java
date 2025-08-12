/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.versioning;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dspace.content.Item;
import org.dspace.core.Context;

public class CustomVersioningConsumer extends VersioningConsumer {

    private static final Logger log = LogManager.getLogger(CustomVersioningConsumer.class);

    @Override
    protected void unarchiveItem(Context ctx, Item item) {
        log.info("This method is empty.");

    }
}
