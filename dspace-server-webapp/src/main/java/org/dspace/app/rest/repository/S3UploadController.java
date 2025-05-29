package org.dspace.app.rest.repository;

import com.amazonaws.HttpMethod;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.CompleteMultipartUploadRequest;
import com.amazonaws.services.s3.model.CompleteMultipartUploadResult;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.amazonaws.services.s3.model.InitiateMultipartUploadRequest;
import com.amazonaws.services.s3.model.InitiateMultipartUploadResult;
import com.amazonaws.services.s3.model.ListPartsRequest;
import com.amazonaws.services.s3.model.PartETag;
import com.amazonaws.services.s3.model.PartListing;
import org.apache.logging.log4j.Logger;
import org.dspace.app.rest.InitiateResponse;
import org.dspace.storage.bitstore.S3BitStoreService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;


import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/upload")
public class S3UploadController {
    private static Logger log = org.apache.logging.log4j.LogManager.getLogger(MetadataBitstreamRestRepository.class);

    @Autowired
    private S3BitStoreService s3BitStoreService;

    AmazonS3 amazonS3;

    private String bucketName = "testbucket";

    @PostMapping("/initiate")
    public InitiateResponse initiateMultipartUpload(@RequestParam String fileName) {
        this.amazonS3 = s3BitStoreService.getAmazonS3();
        // Create the multipart upload request
        String key = "eighty-eight/43/34/91/43349123994797340734389361345819157822"; // Use the file name as the S3 key
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
        key = "eighty-eight/43/34/91/43349123994797340734389361345819157822";
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

    @GetMapping("/list-parts")
    public List<PartETag> listUploadedParts(@RequestParam String uploadId, @RequestParam String key) {
        ListPartsRequest listPartsRequest = new ListPartsRequest(bucketName, key, uploadId);
        PartListing partListing = amazonS3.listParts(listPartsRequest);
        return partListing.getParts().stream()
                .map(partSummary -> new PartETag(partSummary.getPartNumber(), partSummary.getETag()))
                .collect(Collectors.toList());
    }

    @PostMapping("/complete")
    public CompleteResponse completeMultipartUpload(@RequestBody CompleteRequest req) {
        try {
            // Retrieve the list of uploaded parts
            List<PartETag> uploadedParts = listUploadedParts(req.uploadId, req.key);

            // Sort both lists to ensure correct order
            List<PartETag> requestedParts = req.parts.stream()
                    .sorted(Comparator.comparingInt(p -> p.partNumber))
                    .map(p -> new PartETag(p.partNumber, p.ETag.replace("\"", "")))
                    .collect(Collectors.toList());

            // Compare the lists
//            if (!uploadedParts.equals(requestedParts)) {
//                log.error("Uploaded parts (from S3): {}", uploadedParts);
//                log.error("Requested parts (from client): {}", requestedParts);
//                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mismatch between uploaded parts and requested parts.");
//            }

            // Proceed with completion
            CompleteMultipartUploadRequest compReq = new CompleteMultipartUploadRequest(
                    bucketName, req.key, req.uploadId, requestedParts);
            CompleteMultipartUploadResult compRes = amazonS3.completeMultipartUpload(compReq);

            CompleteResponse resp = new CompleteResponse();
            resp.location = compRes.getLocation();
            resp.eTag = compRes.getETag();
            return resp;

        } catch (AmazonS3Exception ex) {
            if ("InternalError".equals(ex.getErrorCode()) &&
                    ex.getMessage().contains("completion is already in progress")) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Multipart completion is already in progress or completed");
            }
            throw ex;
        }
    }
}
