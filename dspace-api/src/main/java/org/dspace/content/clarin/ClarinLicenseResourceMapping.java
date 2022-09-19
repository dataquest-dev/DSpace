package org.dspace.content.clarin;

import org.dspace.core.ReloadableEntity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "license_resource_mapping")
public class ClarinLicenseResourceMapping implements ReloadableEntity<Integer> {

    @Id
    @Column(name="mapping_id")
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "license_resource_mapping_mapping_id_seq")
    @SequenceGenerator(name = "license_resource_mapping_mapping_id_seq", sequenceName = "license_resource_mapping_mapping_id_seq",
            allocationSize = 1)
    private Integer id;

    @Column(name = "bitstream_uuid")
    private UUID bitstreamUuid = null;

    @Column(name = "license_id")
    private Integer licenseId = 0;

    @Override
    public Integer getID() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public UUID getBitstreamId() {
        return bitstreamUuid;
    }

    public void setBitstreamId(UUID bitstreamId) {
        this.bitstreamUuid = bitstreamId;
    }

    public Integer getLicenseId() {
        return licenseId;
    }

    public void setLicenseId(Integer licenseId) {
        this.licenseId = licenseId;
    }
}
