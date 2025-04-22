/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.event;

import java.sql.SQLException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;

import org.apache.log4j.Logger;
import org.dspace.content.Bitstream;
import org.dspace.content.Bundle;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.DSpaceObject;
import org.dspace.content.Item;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.ItemService;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.xoai.app.BasicConfiguration;
import org.dspace.xoai.app.XOAI;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * The OAIIndexEventConsumer determining which items need to be indexed or updated based on the event type and subject.
 * It listens for changes to items, collections, communities,
 * bundles, and bitstreams, and updates the OAI index accordingly.
 * The indexing is done using the XOAI indexer after all relevant items are collected.
 *
 * Class is copied from UFAL/CLARIN-DSPACE (https://github.com/ufal/clarin-dspace) and modified by
 * @author Michaela Paurikova (dspace at dataquest.sk)
 */
public class OAIIndexEventConsumer implements Consumer {
    /**
     * log4j logger
     */
    private static final Logger log = Logger.getLogger(OAIIndexEventConsumer.class);

    ItemService itemService = ContentServiceFactory.getInstance().getItemService();

    // Collect Items, Collections, Communities that need indexing.
    private Set<Item> itemsToUpdate = null;

    @Override
    public void initialize() throws Exception {
        // No-op.
    }

    /**
     * Consume a content event -- just build the sets of objects to add (new) to
     * the index, update, and delete.
     *
     * @param ctx   DSpace context
     * @param event Content event
     */
    public void consume(Context ctx, Event event) throws Exception {

        if (Objects.isNull(itemsToUpdate)) {
            itemsToUpdate = new HashSet<Item>();
        }

        int st = event.getSubjectType();
        if (!(st == Constants.ITEM || st == Constants.BUNDLE
                || st == Constants.COLLECTION || st == Constants.COMMUNITY || st == Constants.BITSTREAM)) {
            log
                    .warn("IndexConsumer should not have been given this kind of Subject in an event, skipping: "
                            + event.toString());
            return;
        }

        DSpaceObject subject = event.getSubject(ctx);
        DSpaceObject object = event.getObject(ctx);

        int et = event.getEventType();

        if (Objects.nonNull(object) && event.getObjectType() == Constants.ITEM) {
            // Just update the object.
            itemsToUpdate.add((Item)object);
            return;
        }

        if (Objects.isNull(subject)) {
            return;
        }

        if (event.getSubjectType() == Constants.COLLECTION || event.getSubjectType() == Constants.COMMUNITY) {
            if (et == Event.MODIFY || et == Event.MODIFY_METADATA || et == Event.REMOVE || et == Event.DELETE) {
                // Must update all the items.
                if (subject.getType() == Constants.COMMUNITY) {
                    for (Collection col : ((Community)subject).getCollections()) {
                        addAll(ctx, col);
                    }
                } else {
                    addAll(ctx, (Collection)subject);
                }
            }
        } else if (event.getSubjectType() == Constants.BITSTREAM || event.getSubjectType() == Constants.BUNDLE) {
            // Must update owning items regardless the event.
            if (subject.getType() == Constants.BITSTREAM) {
                for (Bundle bun : ((Bitstream)subject).getBundles()) {
                    itemsToUpdate.addAll(bun.getItems());
                }
            } else {
                itemsToUpdate.addAll(((Bundle)subject).getItems());
            }
        } else if (event.getSubjectType() == Constants.ITEM) {
            // Any event reindex this item.
            itemsToUpdate.add((Item)subject);
        }
    }

    private void addAll(Context context, Collection col) throws SQLException {
        Iterator<Item> i = itemService.findByCollection(context, col);
        while (i.hasNext()) {
            itemsToUpdate.add(i.next());
        }
    }

    /**
     * Process sets of objects to add, update, and delete in index. Correct for
     * interactions between the sets -- e.g. objects which were deleted do not
     * need to be added or updated, new objects don't also need an update, etc.
     */
    public void end(Context ctx) throws Exception {

        Context anonymousContext = null;
        try {
            if (Objects.isNull(itemsToUpdate)) {
                return;
            }

            Set<Item> filtered = new HashSet<Item>(itemsToUpdate.size());
            for (Item item : itemsToUpdate) {
                if (Objects.isNull(item.getHandle())) {
                    // Probably submission item, skip.
                    continue;
                }
                filtered.add(item);
            }

            // "Free" the resources.
            itemsToUpdate = null;

            anonymousContext = new Context();
            XOAI indexer = new XOAI(anonymousContext, false, false);
            AnnotationConfigApplicationContext applicationContext = new AnnotationConfigApplicationContext(
                    new Class[] { BasicConfiguration.class });
            applicationContext.getAutowireCapableBeanFactory()
                    .autowireBean(indexer);
            indexer.indexItems(filtered);
            applicationContext.close();
        } catch (Exception e) {
            itemsToUpdate = null;
            throw e;
        } finally {
            if (Objects.nonNull(anonymousContext)) {
                anonymousContext.complete();
            }
        }
    }

    public void finish(Context ctx) throws Exception {
        // No-op
    }
}
