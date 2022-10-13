/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.content.clarin;

import org.apache.log4j.Logger;
import org.dspace.content.logic.condition.MetadataValueMatchCondition;
import org.dspace.core.ReloadableEntity;

import javax.persistence.*;

@Entity
@Table(name = "user_registration")
public class ClarinUserRegistration implements ReloadableEntity<Integer> {

    private static Logger log = Logger.getLogger(ClarinUserRegistration.class);
    @Id
    @Column(name = "eperson_id")
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "user_registration_eperson_id_seq")
    @SequenceGenerator(name = "user_registration_eperson_id_seq", sequenceName = "user_registration_eperson_id_seq",
            allocationSize = 1)
    private Integer id;

    @Column(name = "email")
    private String email= null;

    @Column(name = "organization")
    private String organization = null;

    @Column(name = "confirmation")
    private boolean confirmation = false;

    public ClarinUserRegistration() {
    }
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    @Override
    public Integer getID() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getOrganization() {
        return organization;
    }

    public void setOrganization(String organization) {
        this.organization = organization;
    }

    public boolean isConfirmation() {
        return confirmation;
    }

    public void setConfirmation(boolean confirmation) {
        this.confirmation = confirmation;
    }
}