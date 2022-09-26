/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.content.clarin;

import org.dspace.core.ReloadableEntity;

import javax.persistence.Entity;
import javax.persistence.Table;

@Entity
@Table(name = "user_registration")
public class ClarinUserRegistration implements ReloadableEntity<Integer> {

    private Integer id;

    private
    @Override
    public Integer getID() {
        return id;
    }
}
