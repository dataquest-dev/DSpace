package org.dspace.util;

import java.util.Hashtable;
/**
 * This class is used to store the information about a file or a directory
 *
 * @author longtv
 */
public class FileInfo {

    public String name;
    public String content;
    public String size;
    public boolean isDirectory;

    public Hashtable<String, FileInfo> sub = null;

    public FileInfo(String name) {
        this.name = name;
        sub = new Hashtable<String, FileInfo>();
        isDirectory = true;
    }
    public FileInfo(String content, boolean isDirectory) {
        this.content = content;
        this.isDirectory = isDirectory;
    }

    public FileInfo(String name, String size) {
        this.name = name;
        this.size = size;
        isDirectory = false;
    }
}