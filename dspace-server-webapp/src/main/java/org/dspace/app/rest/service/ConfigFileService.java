/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.service;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import org.dspace.app.rest.exception.ConfigFileNotAllowedException;
import org.dspace.app.rest.exception.ConfigFileNotFoundException;
import org.dspace.app.rest.exception.ConfigFileUpdateException;

/**
 * Service interface for managing DSpace configuration files.
 * Provides operations to read, write, and manage configuration files
 * based on administrative settings.
 *
 * @author Your Name (your.email@example.com)
 */
public interface ConfigFileService {

    /**
     * Metadata holder for configuration file information
     */
    class ConfigFileMetadata {
        private String name;
        private Path path;
        private Long size;
        private LocalDateTime lastModified;
        private Boolean readable;
        private Boolean writable;

        public ConfigFileMetadata(String name, Path path, Long size, LocalDateTime lastModified,
                                Boolean readable, Boolean writable) {
            this.name = name;
            this.path = path;
            this.size = size;
            this.lastModified = lastModified;
            this.readable = readable;
            this.writable = writable;
        }

        // Getters and setters
        public String getName() {
            return name;
        }
        public void setName(String name) {
            this.name = name;
        }

        public Path getPath() {
            return path;
        }
        public void setPath(Path path) {
            this.path = path;
        }

        public Long getSize() {
            return size;
        }
        public void setSize(Long size) {
            this.size = size;
        }

        public LocalDateTime getLastModified() {
            return lastModified;
        }
        public void setLastModified(LocalDateTime lastModified) {
            this.lastModified = lastModified;
        }

        public Boolean getReadable() {
            return readable;
        }
        public void setReadable(Boolean readable) {
            this.readable = readable;
        }

        public Boolean getWritable() {
            return writable;
        }
        public void setWritable(Boolean writable) {
            this.writable = writable;
        }
    }

    /**
     * Gets the list of configuration files that are allowed to be managed
     * based on the 'config.admin.updateable.files' configuration property.
     *
     * @return List of allowed configuration file names
     */
    List<String> getAllowedConfigFiles();

    /**
     * Validates if the specified file is allowed to be accessed based on
     * the configuration settings.
     *
     * @param fileName the name of the configuration file to validate
     * @throws ConfigFileNotAllowedException if the file is not in the allowed list
     */
    void validateFileAccess(String fileName) throws ConfigFileNotAllowedException;

    /**
     * Gets metadata information for a specific configuration file.
     *
     * @param fileName the name of the configuration file
     * @return ConfigFileMetadata object containing file information
     * @throws ConfigFileNotFoundException if the file doesn't exist
     * @throws ConfigFileNotAllowedException if the file is not allowed to be accessed
     */
    ConfigFileMetadata getFileMetadata(String fileName)
            throws ConfigFileNotFoundException, ConfigFileNotAllowedException;

    /**
     * Reads the content of a configuration file as text.
     *
     * @param fileName the name of the configuration file to read
     * @return the file content as a string
     * @throws ConfigFileNotFoundException if the file doesn't exist
     * @throws ConfigFileNotAllowedException if the file is not allowed to be accessed
     * @throws IOException if there's an error reading the file
     */
    String readConfigFile(String fileName)
            throws ConfigFileNotFoundException, ConfigFileNotAllowedException, IOException;

    /**
     * Writes content to a configuration file, creating a backup first.
     *
     * @param fileName the name of the configuration file to write
     * @param content the new content to write to the file
     * @throws ConfigFileNotFoundException if the file doesn't exist
     * @throws ConfigFileNotAllowedException if the file is not allowed to be modified
     * @throws ConfigFileUpdateException if there's an error writing the file
     * @throws IOException if there's an I/O error
     */
    void writeConfigFile(String fileName, String content)
            throws ConfigFileNotFoundException, ConfigFileNotAllowedException,
                   ConfigFileUpdateException, IOException;

    /**
     * Creates a backup of the specified configuration file.
     *
     * @param filePath the path to the file to backup
     * @return the path to the created backup file
     * @throws IOException if there's an error creating the backup
     */
    Path createBackup(Path filePath) throws IOException;
}