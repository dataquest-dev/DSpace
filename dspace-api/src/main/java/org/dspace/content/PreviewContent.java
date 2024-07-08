package org.dspace.content;

import org.apache.logging.log4j.Logger;
import org.dspace.core.Context;
import org.dspace.core.ReloadableEntity;

import javax.persistence.*;
import javax.persistence.Entity;


@Entity
@Table(name = "preview_content")
public class PreviewContent implements ReloadableEntity<Integer> {

    private Logger log = org.apache.logging.log4j.LogManager.getLogger(PreviewContent.class);

    @Id
    @Column(name = "preview_content_id")
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "preview_content_preview_content_id_seq")
    @SequenceGenerator(name = "preview_content_preview_content_id_seq", sequenceName = "preview_content_preview_content_id_seq", allocationSize = 1)
    private Integer id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "bitstream_id")
    private Bitstream bitstream;

    @Column(name = "path")
    private String path;

    @Column(name = "size_bytes")
    private long sizeBytes;


    /**
     * Protected constructor, create object using:
     * {@link org.dspace.handle.service.HandleService#createHandle(Context, DSpaceObject)}
     * or
     * {@link org.dspace.handle.service.HandleService#createHandle(Context, DSpaceObject, String)}
     * or
     * {@link org.dspace.handle.service.HandleService#createHandle(Context, DSpaceObject, String, boolean)}
     */
    protected PreviewContent() {
    }

    @Override
    public Integer getID() {
        return 0;
    }


    public Bitstream getBitstream() {
        return bitstream;
    }

    public void setBitstream(Bitstream bitstream) {
        this.bitstream = bitstream;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(long size_bytes) {
        this.sizeBytes = size_bytes;
    }
}