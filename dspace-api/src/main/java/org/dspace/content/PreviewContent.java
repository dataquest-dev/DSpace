/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.content;

import javax.persistence.*;
import javax.persistence.Entity;

import org.dspace.core.Context;
import org.dspace.core.ReloadableEntity;

import java.util.HashSet;
import java.util.Set;

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

    @Column(name = "name")
    public String name;

    @Column(name = "content")
    public String content;

    @Column(name = "directory")
    public boolean isDirectory;

    @Column(name = "size")
    public String size;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "previewcontent2previewcontent",
            joinColumns = {@JoinColumn(name = "parent_prcont_id")},
            inverseJoinColumns = {@JoinColumn(name = "child_prcont_id")}
    )
    private Set<PreviewContent> subPreviewContents = new HashSet<>();

    /**
     * Protected constructor, create object using:
     * {@link org.dspace.handle.service.HandleService#createHandle(Context, DSpaceObject)}
     * or
     * {@link org.dspace.handle.service.HandleService#createHandle(Context, DSpaceObject, String)}
     * or
     * {@link org.dspace.handle.service.HandleService#createHandle(Context, DSpaceObject, String, boolean)}
     */
    protected PreviewContent(Bitstream bitstream, String name, String content, boolean isDirectory, String size, Set<PreviewContent> subPreviewContents) {
        this.bitstream = bitstream;
        this.name = name;
        this.content = content;
        this.isDirectory = isDirectory;
        this.size = size;
        this.subPreviewContents = subPreviewContents;
    }

    protected PreviewContent(Bitstream bitstream, String content, boolean isDirectory) {
        this.bitstream = bitstream;
        this.content = content;
        this.isDirectory = isDirectory;
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public boolean isDirectory() {
        return isDirectory;
    }

    public void setDirectory(boolean directory) {
        isDirectory = directory;
    }

    public String getSize() {
        return size;
    }

    public void setSize(String size) {
        this.size = size;
    }

    public Set<PreviewContent> getSubPreviewContents() {
        return subPreviewContents;
    }
}