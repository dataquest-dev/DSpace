package org.dspace.app.rest.repository;

import com.amazonaws.HttpMethod;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.CompleteMultipartUploadRequest;
import com.amazonaws.services.s3.model.CompleteMultipartUploadResult;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.amazonaws.services.s3.model.InitiateMultipartUploadRequest;
import com.amazonaws.services.s3.model.InitiateMultipartUploadResult;
import com.amazonaws.services.s3.model.PartETag;
import org.dspace.app.rest.InitiateResponse;
import org.dspace.storage.bitstore.S3BitStoreService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/upload")
public class S3UploadController {
    @Autowired
    private S3BitStoreService s3BitStoreService;

    AmazonS3 amazonS3;

    private String bucketName = "testbucket";

    @PostMapping("/initiate")
    public InitiateResponse initiateMultipartUpload(@RequestParam String fileName) {
        this.amazonS3 = s3BitStoreService.getAmazonS3();
        // Create the multipart upload request
        String key = "eighty-eight/43/34/91/43349123994797340734389361345819157824"; // Use the file name as the S3 key
        InitiateMultipartUploadRequest req = new InitiateMultipartUploadRequest(bucketName, key);
        // (Optional: set storage class, ACL, metadata here)
        InitiateMultipartUploadResult result = amazonS3.initiateMultipartUpload(req);
        String uploadId = result.getUploadId();
        return new InitiateResponse(uploadId, fileName);
    }

    @GetMapping("/{uploadId}/presign")
    public URL getPresignedUrl(
            @PathVariable String uploadId,
            @RequestParam int partNumber,
            @RequestParam String key) {
        // Build a presigned URL for this part
        key = "eighty-eight/43/34/91/43349123994797340734389361345819157824";
        GeneratePresignedUrlRequest presignedReq =
                new GeneratePresignedUrlRequest(bucketName, key)
                        .withMethod(HttpMethod.PUT)
                        .withExpiration(Date.from(Instant.now().plus(Duration.ofMinutes(15))));
        // Required parameters for multipart upload
        presignedReq.addRequestParameter("uploadId", uploadId);
        presignedReq.addRequestParameter("partNumber", Integer.toString(partNumber));
        // (Optional: add CONTENT_MD5 header for data integrity)
        URL url = amazonS3.generatePresignedUrl(presignedReq);
        return url;
    }

    public static class PartDetail {
        public int partNumber;
        public String ETag;
    }
    public static class CompleteRequest {
        public String uploadId;
        public String key;
        public List<PartDetail> parts;
    }
    public static class CompleteResponse {
        public String location;
        public String eTag;
    }

    @PostMapping("/complete")
    public CompleteResponse completeMultipartUpload(@RequestBody CompleteRequest req) {
        // Convert to SDK PartETag list
        List<PartETag> partETags = req.parts.stream()
                .map(p -> new PartETag(p.partNumber, p.ETag))
                .collect(Collectors.toList());

        CompleteMultipartUploadRequest compReq = new CompleteMultipartUploadRequest(
                bucketName, req.key, req.uploadId, partETags);
        CompleteMultipartUploadResult compRes = amazonS3.completeMultipartUpload(compReq);

        CompleteResponse resp = new CompleteResponse();
        resp.location = compRes.getLocation(); // S3 object URL (if needed)
        resp.eTag = compRes.getETag();         // Final object ETag
        return resp;
    }
}
