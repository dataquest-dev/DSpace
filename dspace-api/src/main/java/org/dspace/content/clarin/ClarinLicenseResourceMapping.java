package org.dspace.content.clarin;

import org.dspace.content.Bitstream;
import org.dspace.core.ReloadableEntity;

import javax.persistence.*;
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

    @ManyToOne(fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST})
    @JoinColumn(name = "license_id")
    private ClarinLicense license;

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "bitstream_uuid", referencedColumnName = "uuid")
    private Bitstream bitstream;

    @Override
    public Integer getID() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Bitstream getBitstream() {
        return bitstream;
    }

    public void setBitstream(Bitstream bitstream) {
        this.bitstream = bitstream;
    }

    public ClarinLicense getLicense() {
        return license;
    }

    public void setLicense(ClarinLicense license) {
        this.license = license;
    }
}
