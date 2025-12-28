package com.netstra.disputes.services.imaging;

import com.netra.commons.util.FileUtils;
import com.netstra.disputes.services.util.S3Service;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import utilities.ForensicMetadata;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;

@Service
@RequiredArgsConstructor
public class ImageValidationOrchestrator {
    private ImageDuplicateValidator duplicateValidator = new ImageDuplicateValidator();
    private ImageForensicValidator forensicValidator = new ImageForensicValidator();

    @Qualifier("bannedContentRedisTemplate")
    private final RedisTemplate<String, Object> bannedContentCache;

    private final S3Service s3Service;

    private static final String BANNED_KEY_PREFIX = "banned_aHash:";


    public ImageValidationResult validateEvidence(String imageBase64Str){
        ImageValidationResult result = new ImageValidationResult();
        File tempFile = createTempFile(imageBase64Str);
        duplicateValidator.validateImage(tempFile);
        String aHash = duplicateValidator.getAhash();
        result.setAverageHash(aHash);

        // 2️⃣ Hard invalidity check: exact banned content
        if (isBanned(aHash)) {
            result.setValidAverageHash(false);
            result.setAverageHashRejectionReason("Image contain content not allowed");
        }

        forensicValidator.validateImage(tempFile);
        ForensicMetadata metadata = forensicValidator.getForensicMetadata();
        ForensicHints forensicHints = new ForensicHints(metadata);
        result.setForensicHints(forensicHints);




        //TODO   s3Service.uploadFile(tempFile, "define a folder");
        // Upload image to s3
        // Use presigned URLs or short-lived tokens for secure access.
        // set file infor here
        // Keep fail-fast checks in your service (aHash, EXIF) before uploading to storage.


        deleteTempFile(tempFile);

        return result;
    }

    //{
    //  "eventType": "EvidenceSubmitted",
    //  "evidenceId": "uuid",
    //  "disputeId": "uuid",
    //  "media": {
    //    "type": "IMAGE",
    //    "contentBase64": "..."
    //  },
    //  "precheck": {
    //    "ahash": "string",
    //    "forensicHints": {...},
    //    "suspectedDuplicate": false
    //  },
    //  "submittedAt": "timestamp"
    //}




    private File createTempFile(String base64) {
        byte[] data = FileUtils.decodeBase64(base64);

        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("No file data provided.");
        }

        String mime = FileUtils.detectMimeType(data);
        String extension = FileUtils.extensionFromMime(mime);

        try {
            File tempFile = File.createTempFile("upload_", "." + extension);

            // Write bytes into the temp file
            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                fos.write(data);
            }

            return tempFile;

        } catch (IOException e) {
            throw new RuntimeException("Failed to create or write temporary file", e);
        }
    }

    private void deleteTempFile(File file) {
        if (file != null && file.exists()) {
            try {
                Files.delete(file.toPath());
            } catch (IOException e) {
                // Best effort: log but don't propagate
                System.err.println("Failed to delete temp file: " + file.getAbsolutePath());
            }
        }
    }


    /**
     * Check if the given average hash matches a banned content entry.
     * Deterministic, fail-fast, idempotency-safe.
     */
    public boolean isBanned(String aHash) {
        if (aHash == null || aHash.isEmpty()) {
            return false; // empty hash cannot be banned
        }

        String key = BANNED_KEY_PREFIX + aHash;

        Boolean exists = bannedContentCache.hasKey(key);
        // RedisTemplate.hasKey returns null on error, treat as false
        return Boolean.TRUE.equals(exists);
    }

    /**
     * Optional: add a banned hash to Redis.
     */
    public void addBannedHash(String aHash) {
        if (aHash == null || aHash.isEmpty()) return;

        String key = BANNED_KEY_PREFIX + aHash;
        bannedContentCache.opsForValue().set(key, "1"); // value is dummy
    }

    /**
     * Optional: remove a banned hash from Redis.
     */
    public void removeBannedHash(String aHash) {
        if (aHash == null || aHash.isEmpty()) return;

        String key = BANNED_KEY_PREFIX + aHash;
        bannedContentCache.delete(key);
    }



}
