package com.netstra.disputes.services.validation.image;

import com.netstra.disputes.services.util.S3Service;
import org.springframework.stereotype.Service;
import utilities.ForensicMetadata;

import java.io.File;

@Service
public class ImageValidationOrchestrator {
    private ImageDuplicateValidator duplicateValidator;
    private ImageForensicValidator forensicValidator;

    private S3Service s3Service;

    public void validateEvidence(String imageBase64Str){
        //todo: convert base64 to local file for processing here
        File x = null;
        duplicateValidator.validateImage(x);
        String pHash = duplicateValidator.getPHash();
        String aHash = duplicateValidator.getAhash();

        forensicValidator.validateImage(x);
        ForensicMetadata metadata = forensicValidator.getForensicMetadata();

        
    }


}
