package com.netstra.disputes.services.validation.image;

import com.netra.commons.util.FileUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import utilities.ForensicMetadata;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;

@Service
@RequiredArgsConstructor
public class ImageValidationOrchestrator {
    private ImageDuplicateValidator duplicateValidator;
    private ImageForensicValidator forensicValidator;

    public Boolean validateEvidence(String imageBase64Str){
        File tempFile = createTempFile(imageBase64Str);
        duplicateValidator.validateImage(tempFile);
        String aHash = duplicateValidator.getAhash();

        //todo: check aHash duplicate from db here

        forensicValidator.validateImage(tempFile);
        ForensicMetadata metadata = forensicValidator.getForensicMetadata();
        //todo: run forensic validity check on metadata here

        deleteTempFile(tempFile);

        //use outcome to determine return value here

        return false;
    }




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



}
