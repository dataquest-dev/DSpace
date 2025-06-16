/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.health;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.common.base.CaseFormat;
import org.dspace.content.Collection;
import org.dspace.content.DSpaceObject;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.CollectionService;
import org.dspace.core.Context;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.Group;
import org.dspace.eperson.factory.EPersonServiceFactory;
import org.dspace.eperson.service.EPersonService;
import org.dspace.eperson.service.GroupService;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * @author LINDAT/CLARIN dev team
 */
public class UserCheck extends Check {

    private static final EPersonService ePersonService = EPersonServiceFactory.getInstance().getEPersonService();
    private static final GroupService groupService = EPersonServiceFactory.getInstance().getGroupService();
    private static final CollectionService collectionService = ContentServiceFactory.getInstance()
                                                                                    .getCollectionService();

    @Override
    public String run(ReportInfo ri) {
        Context context = new Context();
        String ret = "";
        JSONObject root = new JSONObject();
        Map<String, Integer> info = new HashMap<String, Integer>();
        try {
            List<EPerson> epersons = ePersonService.findAll(context, EPerson.LASTNAME);
            info.put("Count", epersons.size());
            info.put("Can log in (password)", 0);
            info.put("Have email", 0);
            info.put("Have 1st name", 0);
            info.put("Have 2nd name", 0);
            info.put("Have lang", 0);
            info.put("Have netid", 0);
            info.put("Self registered", 0);

            for (EPerson e : epersons) {
                if (e.getEmail() != null && e.getEmail().length() > 0) {
                    info.put("Have email", info.get("Have email") + 1);
                }
                if (e.canLogIn()) {
                    info.put("Can log in (password)",
                             info.get("Can log in (password)") + 1);
                }
                if (e.getFirstName() != null && e.getFirstName().length() > 0) {
                    info.put("Have 1st name", info.get("Have 1st name") + 1);
                }
                if (e.getLastName() != null && e.getLastName().length() > 0) {
                    info.put("Have 2nd name", info.get("Have 2nd name") + 1);
                }
                if (e.getLanguage() != null && e.getLanguage().length() > 0) {
                    info.put("Have lang", info.get("Have lang") + 1);
                }
                if (e.getNetid() != null && e.getNetid().length() > 0) {
                    info.put("Have netid", info.get("Have netid") + 1);
                }
                if (e.getNetid() != null && e.getNetid().length() > 0) {
                    info.put("Self registered", info.get("Self registered") + 1);
                }
            }

        } catch (SQLException e) {
            error(e);
        }

        ret += String.format(
            "%-20s: %d\n", "Users", info.get("Count"));
        root.put("users", info.get("Count"));
        ret += String.format(
            "%-20s: %d\n", "Have email", info.get("Have email"));
        root.put("haveEmail", info.get("Have email"));
        for (Map.Entry<String, Integer> e : info.entrySet()) {
            if (!e.getKey().equals("Count") && !e.getKey().equals("Have email")) {
                String key = e.getKey();
                int value = e.getValue();
                ret += String.format("%-21s: %s\n", key,
                                     String.valueOf(value));

                key = caseFormat(key);
                root.put(key, value);
            }
        }

        ret += "\n";

        try {
            // empty group
            List<Group> emptyGroups = groupService.getEmptyGroups(context);
            ret += String.format("Empty groups: #%d\n    ", emptyGroups.size());
            JSONArray emptyGroupsArray = new JSONArray();
            for (Group group : emptyGroups) {
                JSONObject oneEmptyGroup = new JSONObject();
                ret += String.format("id=%s;name=%s,\n    ", group.getID(), group.getName());
                oneEmptyGroup.put("id", group.getID());
                oneEmptyGroup.put("name", group.getName());
                emptyGroupsArray.put(oneEmptyGroup);
            }
            root.put("emptyGroups", emptyGroupsArray);

            //subscribers
            List<EPerson> subscribers = ePersonService.findEPeopleWithSubscription(context);
            JSONArray subsIdsArray = new JSONArray();
            ret += String.format(
                "Subscribers: #%d [%s]\n",
                subscribers.size(), formatIds(subscribers, subsIdsArray));
            root.put("subscribers", subsIdsArray);

            //subscribed collections
            List<Collection> subscribedCols = collectionService.findCollectionsWithSubscribers(context);
            JSONArray subsColsArray = new JSONArray();
            ret += String.format(
                "Subscribed cols.: #%d [%s]\n",
                subscribedCols.size(), formatIds(subscribedCols, subsColsArray));
            root.put("subscribedCollections", subsColsArray);

            context.complete();

        } catch (SQLException e) {
            error(e);
        }

        this.setReportJson(root);
        return ret;
    }

    private String formatIds(List<? extends DSpaceObject> objects, JSONArray jsonArr) {
        StringBuilder ids = new StringBuilder();
        for (DSpaceObject o : objects) {
            JSONObject oneId = new JSONObject();
            ids.append(o.getID()).append(", ");
            oneId.put("id", o.getID());
            jsonArr.put(oneId);
        }
        return ids.toString();
    }

    String caseFormat(String str) {
        str = str.toLowerCase().replaceAll("\\s+", "_");
        str = CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, str);
        return str;
    }
}
