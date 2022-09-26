/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
//package org.dspace.content.clarin;
//
//import org.dspace.core.ReloadableEntity;
//
//import javax.persistence.*;
//import java.util.Date;
//
//@Entity
//@Table(name = "license_resource_user_allowance")
//public class ClarinLicenseResourceUserAllowance implements ReloadableEntity<Integer> {
//
//    @Id
//    @Column(name = "transaction_id")
//    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator =
//    "license_resource_user_allowance_transaction_id_seq")
//    @SequenceGenerator(name = "license_resource_user_allowance_transaction_id_seq", sequenceName =
//    "license_resource_user_allowance_transaction_id_seq",
//            allocationSize = 1)
//    private Integer id;
//
//    @ManyToOne(fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST})
//    @JoinColumn(name = "eperson_id")
//    private Integer epersonId;
//
//    @OneToMany(fetch = FetchType.LAZY, mappedBy = "licenseResourceUserAllowance", cascade = CascadeType.PERSIST)
//    private ClarinLicenseResourceMapping mapping;
//
//    @Temporal(TemporalType.TIMESTAMP)
//    @Column(name = "created_on")
//    private Date createdOn;
//
//    @Column(name = "token")
//    private String token;
//
//    @Override
//    public Integer getID() {
//        return id;
//    }
//}
