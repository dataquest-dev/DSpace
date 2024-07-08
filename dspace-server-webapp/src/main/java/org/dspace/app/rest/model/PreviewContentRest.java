package org.dspace.app.rest.model;

import org.dspace.app.rest.RestResourceController;

public class PreviewContentRest extends BaseObjectRest<Integer> {
    public static final String NAME = "clarinContentPreview";
    public static final String CATEGORY = RestAddressableModel.CORE;

    private BitstreamRest bitstream;
    private String path;
    private long sizeBytes;


    public PreviewContentRest() {
    }

    public BitstreamRest getBitstream() {
        return bitstream;
    }

    public void setBitstream(BitstreamRest bitstream) {
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

    public void setSizeBytes(long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    @Override
    public String getCategory() {
        return CATEGORY;
    }

    @Override
    public Class getController() {
        return RestResourceController.class;
    }

    @Override
    public String getType() {
        return NAME;
    }
}
