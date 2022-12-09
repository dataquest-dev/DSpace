package org.dspace.content.clarin;

import org.dspace.core.ReloadableEntity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;

/**
 * TODO
 *
 * @author Milan Majchrak (milan.majchrak at dataquest.sk)
 */
@Entity
@Table(name = "verification_token")
public class ClarinVerificationToken implements ReloadableEntity<Integer> {

    @Id
    @Column(name = "verification_token_id")
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "verification_token_verification_token_id_seq")
    @SequenceGenerator(name = "verification_token_verification_token_id_seq",
            sequenceName = "verification_token_verification_token_id_seq",
            allocationSize = 1)
    private Integer id;

    @Column(name = "eperson_netid")
    private String ePersonNetID = null;

    @Column(name = "email")
    private String email = null;

    @Column(name = "shib_headers")
    private String shibHeaders = null;

    @Column(name = "token")
    private String token = null;

    public ClarinVerificationToken() {
    }

    public String getShibHeaders() {
        return shibHeaders;
    }

    public void setShibHeaders(String shibHeaders) {
        this.shibHeaders = shibHeaders;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getePersonNetID() {
        return ePersonNetID;
    }

    public void setePersonNetID(String ePersonNetID) {
        this.ePersonNetID = ePersonNetID;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    @Override
    public Integer getID() {
        return id;
    }
}
