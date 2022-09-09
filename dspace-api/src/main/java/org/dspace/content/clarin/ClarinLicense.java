package org.dspace.content.clarin;

import org.dspace.core.ReloadableEntity;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "license_definition")
public class ClarinLicense implements ReloadableEntity<Integer> {

    @Id
    @Column(name="license_id")
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "license_definition_license_id_seq")
    @SequenceGenerator(name = "license_definition_license_id_seq", sequenceName = "license_definition_license_id_seq",
            allocationSize = 1)
    private Integer id;

    @ManyToMany(fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST})
    @JoinTable(
            name = "license_label_extended_mapping",
            joinColumns = @JoinColumn(name = "license_id"),
            inverseJoinColumns = @JoinColumn(name = "label_id"))
    Set<ClarinLicenseLabel> clarinLicenseLabels = new HashSet<>();;

//    @Column(name = "eperson_id")
//    private Integer epersonId;

    @Column(name = "name")
    private String name = null;

    @Column(name = "definition")
    private String definition = null;

    @Column(name = "confirmation")
    private Integer confirmation = 0;

    @Column(name = "required_info")
    private String requiredInfo = null;

    public ClarinLicense() {
    }

    public void setClarinLicenseLabels(Set<ClarinLicenseLabel> clarinLicenseLabels) {
        this.clarinLicenseLabels = clarinLicenseLabels;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getDefinition() {
        return definition;
    }

    public void setDefinition(String definition) {
        this.definition = definition;
    }

    public Integer getConfirmation() {
        return confirmation;
    }

    public void setConfirmation(Integer confirmation) {
        this.confirmation = confirmation;
    }

    public String getRequiredInfo() {
        return requiredInfo;
    }

    public void setRequiredInfo(String requiredInfo) {
        this.requiredInfo = requiredInfo;
    }

    public List<ClarinLicenseLabel> getLicenseLabels() {
        ClarinLicenseLabel[] output = clarinLicenseLabels.toArray(new ClarinLicenseLabel[] {});
        return Arrays.asList(output);
    }

    public void setLicenseLabels(Set<ClarinLicenseLabel> clarinLicenseLabels) {
        this.clarinLicenseLabels = clarinLicenseLabels;
    }

    @Override
    public Integer getID() {
        return id;
    }
}
