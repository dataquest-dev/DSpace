package org.dspace.app.rest.model;

public class BitstreamChecksum {
    CheckSumRest activeStore = null;
    CheckSumRest synchronizedStore = null;
    CheckSumRest databaseChecksum = null;

    public BitstreamChecksum() {
    }

    public CheckSumRest getActiveStore() {
        return activeStore;
    }

    public void setActiveStore(CheckSumRest activeStore) {
        this.activeStore = activeStore;
    }

    public CheckSumRest getSynchronizedStore() {
        return synchronizedStore;
    }

    public void setSynchronizedStore(CheckSumRest synchronizedStore) {
        this.synchronizedStore = synchronizedStore;
    }

    public CheckSumRest getDatabaseChecksum() {
        return databaseChecksum;
    }

    public void setDatabaseChecksum(CheckSumRest databaseChecksum) {
        this.databaseChecksum = databaseChecksum;
    }
}
