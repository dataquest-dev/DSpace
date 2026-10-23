/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.itemupdate;

import static org.junit.Assert.assertEquals;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.dspace.AbstractIntegrationTestWithDatabase;
import org.dspace.builder.BitstreamBuilder;
import org.dspace.builder.CollectionBuilder;
import org.dspace.builder.CommunityBuilder;
import org.dspace.builder.ItemBuilder;
import org.dspace.content.Bitstream;
import org.dspace.content.Collection;
import org.dspace.content.Item;
import org.dspace.content.MetadataValue;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.ItemService;
import org.dspace.core.Context;
import org.junit.Before;
import org.junit.Test;

/**
 * Integration tests for {@link ItemFilesMetadataRepair}.
 */
public class ItemFilesMetadataRepairIT extends AbstractIntegrationTestWithDatabase {

    private final ItemService itemService = ContentServiceFactory.getInstance().getItemService();

    private Collection collection;
    private Item item;
    private long firstFileSize;
    private long totalFilesSize;

    @Before
    public void setUpItemWithStaleFilesSize() throws Exception {
        context.turnOffAuthorisationSystem();
        parentCommunity = CommunityBuilder.createCommunity(context).build();
        collection = CollectionBuilder.createCollection(context, parentCommunity).build();
        item = ItemBuilder.createItem(context, collection).withTitle("Item with two files").build();
        Bitstream first = createBitstream("first file");
        Bitstream second = createBitstream("second, longer file");
        firstFileSize = first.getSizeBytes();
        totalFilesSize = firstFileSize + second.getSizeBytes();

        // Simulate the state left by the CLARIN import: the size of the last file is not counted.
        itemService.clearMetadata(context, item, "local", "files", "size", Item.ANY);
        itemService.addMetadata(context, item, "local", "files", "size", null, String.valueOf(firstFileSize));
        itemService.update(context, item);
        context.restoreAuthSystemState();
        context.commit();
    }

    @Test
    public void dryRunDoesNotChangeStaleFilesSize() throws Exception {
        runRepair("-d");

        assertFilesMetadata("2", String.valueOf(firstFileSize), "yes");
    }

    @Test
    public void repairRecalculatesNonZeroStaleFilesSize() throws Exception {
        runRepair();

        assertFilesMetadata("2", String.valueOf(totalFilesSize), "yes");
    }

    @Test
    public void collectionRepairRecalculatesNonZeroStaleFilesSize() throws Exception {
        ItemFilesMetadataRepair.main(new String[] {"-e", admin.getEmail(), "-c", collection.getID().toString()});

        assertFilesMetadata("2", String.valueOf(totalFilesSize), "yes");
    }

    private Bitstream createBitstream(String content) throws Exception {
        try (InputStream is = IOUtils.toInputStream(content, StandardCharsets.UTF_8)) {
            return BitstreamBuilder.createBitstream(context, item, is).build();
        }
    }

    private void runRepair(String... extraArgs) throws Exception {
        String[] args = {"-e", admin.getEmail(), "-i", item.getID().toString()};
        ItemFilesMetadataRepair.main(ArrayUtils.addAll(args, extraArgs));
    }

    private void assertFilesMetadata(String count, String size, String hasFiles) throws Exception {
        try (Context verifyContext = new Context()) {
            Item reloaded = itemService.find(verifyContext, item.getID());
            assertEquals(List.of(count), values(reloaded, "files", "count"));
            assertEquals(List.of(size), values(reloaded, "files", "size"));
            assertEquals(List.of(hasFiles), values(reloaded, "has", "files"));
        }
    }

    private List<String> values(Item reloaded, String element, String qualifier) {
        return itemService.getMetadata(reloaded, "local", element, qualifier, Item.ANY).stream()
                .map(MetadataValue::getValue)
                .collect(Collectors.toList());
    }
}
