package org.dspace.storage.bitstore.service;

public interface S3DirectDownloadService {
    String generatePresignedUrl(String bucket, String key, int expirationSeconds);
}
