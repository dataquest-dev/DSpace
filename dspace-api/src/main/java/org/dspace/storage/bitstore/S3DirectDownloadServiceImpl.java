package org.dspace.storage.bitstore;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import org.dspace.services.ConfigurationService;
import org.dspace.storage.bitstore.service.S3DirectDownloadService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import javax.annotation.PostConstruct;

public class S3DirectDownloadServiceImpl implements S3DirectDownloadService {

    private static final Logger log = LoggerFactory.getLogger(S3DirectDownloadServiceImpl.class);

    @Autowired
    private ConfigurationService configurationService;

    private AmazonS3 s3Client;

    @PostConstruct
    private void init() {
        log.info("Creating S3DirectDownloadService");

        String accessKey = configurationService.getProperty("assetstore.s3.awsAccessKey");
        String secretKey = configurationService.getProperty("assetstore.s3.awsSecretKey");
        String endpoint = configurationService.getProperty("assetstore.s3.endpoint");
        String region = configurationService.getProperty("assetstore.s3.awsRegionName"); // Cesnet requires us-east-1

        log.info("Access key: " + accessKey);
        log.info("Secret key: " + secretKey);
        log.info("Endpoint: " + endpoint);
        log.info("Region: " + region);

        BasicAWSCredentials credentials = new BasicAWSCredentials(accessKey, secretKey);
        this.s3Client = AmazonS3ClientBuilder.standard()
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(endpoint, region))
                .withPathStyleAccessEnabled(true)
                .withCredentials(new AWSStaticCredentialsProvider(credentials))
                .build();
    }

    public String generatePresignedUrl(String bucket, String key, int expirationSeconds) {
        if (s3Client == null) {
            init();
        }
        java.util.Date expiration = new java.util.Date();
        long expTimeMillis = expiration.getTime() + expirationSeconds * 1000L;
        expiration.setTime(expTimeMillis);
        return s3Client.generatePresignedUrl(bucket, key, expiration).toString();
    }
}
