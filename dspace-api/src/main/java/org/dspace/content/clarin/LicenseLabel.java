package org.dspace.content.clarin;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.ManyToMany;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "license_label")
public class LicenseLabel {

    @Id
    @Column(name="label_id")
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "license_label_label_id_seq")
    @SequenceGenerator(name = "license_label_label_id_seq", sequenceName = "license_label_label_id_seq",
            allocationSize = 1)
    private Integer id;

    @Column(name = "label")
    private String definition = null;

    @Column(name = "title")
    private String title = null;

    @Column(name = "is_extended")
    private boolean isExtended = false;

    @ManyToMany(mappedBy = "licenseLabels")
    List<License> licens = new ArrayList<>();

    public LicenseLabel() {
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

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public boolean isExtended() {
        return isExtended;
    }

    public void setExtended(boolean extended) {
        isExtended = extended;
    }

    public List<License> getLicenses() {
        return licens;
    }

    public void setLicenses(List<License> licens) {
        this.licens = licens;
    }
}
