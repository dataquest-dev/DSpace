/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */

package org.dspace.subscriptions;

<<<<<<< HEAD
import java.sql.SQLException;
import java.util.Objects;

import org.apache.commons.cli.Options;
import org.dspace.authorize.AuthorizeServiceImpl;
import org.dspace.core.Context;
import org.dspace.scripts.DSpaceRunnable;
import org.dspace.scripts.configuration.ScriptConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
=======
import java.util.Objects;

import org.apache.commons.cli.Options;
import org.dspace.scripts.DSpaceRunnable;
import org.dspace.scripts.configuration.ScriptConfiguration;
>>>>>>> dspace-7.6.1

/**
 * Implementation of {@link DSpaceRunnable} to find subscribed objects and send notification mails about them
 */
public class SubscriptionEmailNotificationConfiguration<T
        extends SubscriptionEmailNotification> extends ScriptConfiguration<T> {

    private Class<T> dspaceRunnableClass;

<<<<<<< HEAD
    @Autowired
    private AuthorizeServiceImpl authorizeService;

    @Override
    public boolean isAllowedToExecute(Context context) {
        try {
            return authorizeService.isAdmin(context);
        } catch (SQLException e) {
            throw new RuntimeException("SQLException occurred when checking if the current user is an admin", e);
        }
    }

=======
>>>>>>> dspace-7.6.1
    @Override
    public Options getOptions() {
        if (Objects.isNull(options)) {
            Options options = new Options();
            options.addOption("f", "frequency", true,
                              "Subscription frequency. Valid values include: D (Day), W (Week) and M (Month)");
            options.getOption("f").setRequired(true);
            super.options = options;
        }
        return options;
    }

    @Override
    public Class<T> getDspaceRunnableClass() {
        return dspaceRunnableClass;
    }

    @Override
    public void setDspaceRunnableClass(Class<T> dspaceRunnableClass) {
        this.dspaceRunnableClass = dspaceRunnableClass;
    }

}
