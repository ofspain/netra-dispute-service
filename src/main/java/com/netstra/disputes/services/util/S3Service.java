package com.netstra.disputes.services.util;

import com.netra.commons.util.FileUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.UUID;



@Service
@RequiredArgsConstructor
public class S3Service {


    @Value("${aws-s3.bucket}")
    private String bucket;

    private final S3Client s3;


    public String uploadBase64(String base64, String folder) {
        byte[] data = FileUtils.decodeBase64(base64);
        System.out.println("uploading data lenght "+data.length);
        if (data.length == 0) {
            throw new IllegalArgumentException("No file data provided.");
        }

        String mime = FileUtils.detectMimeType(data);
        String extension = FileUtils.extensionFromMime(mime);

        String key = folder + "/" + UUID.randomUUID() + "." + extension;

        System.out.println("path.... "+key);

        s3.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(mime)
                        .build(),
                RequestBody.fromBytes(data)
        );

        return key;
    }

    public String uploadFile(File file, String folder) {
        if (file == null || !file.exists() || file.length() == 0) {
            throw new IllegalArgumentException("Invalid or empty file.");
        }

        try {
            byte[] data = Files.readAllBytes(file.toPath());

            String mime = FileUtils.detectMimeType(data);
            String extension = FileUtils.extensionFromMime(mime);

            String key = folder + "/" + UUID.randomUUID() + "." + extension;

            s3.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType(mime)
                            .build(),
                    RequestBody.fromBytes(data)
            );

            return key;

        } catch (IOException e) {
            throw new RuntimeException("Failed to upload file to S3", e);

        } finally {
            // Always delete the temp file after upload or failure
            try {
                if (file != null && file.exists()) {
                    Files.delete(file.toPath());
                }
            } catch (IOException ignore) {
                // nothing to do — temp file cleanup best-effort
            }
        }
    }


    public byte[] getObject(String key) {
        try {
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();

            ResponseBytes<GetObjectResponse> objectBytes = s3.getObjectAsBytes(request);
            return objectBytes.asByteArray();

        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch S3 object with key: " + key, e);
        }
    }
}
