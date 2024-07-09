/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.content;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;

import org.dspace.core.Context;
import org.dspace.core.ReloadableEntity;

@Entity
@Table(name = "preview_content")
public class PreviewContent implements ReloadableEntity<Integer> {
    @Id
    @Column(name = "preview_content_id")
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "preview_content_preview_content_id_seq")
    @SequenceGenerator(name = "preview_content_preview_content_id_seq",
            sequenceName = "preview_content_preview_content_id_seq", allocationSize = 1)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST})
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
    protected PreviewContent(Bitstream bitstream) {
        this.bitstream = bitstream;
    }

    @Override
    public Integer getID() {
        return id;
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