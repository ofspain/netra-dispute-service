package com.netstra.disputes.services.imaging;

import lombok.Data;
import utilities.ForensicMetadata;
import utilities.ForensicMetadataExtractor;

import java.io.File;

@Data
public class ImageForensicValidator implements ImageValidator {
    private ForensicMetadata forensicMetadata;

    @Override
    public void validateImage(File image){
        forensicMetadata = ForensicMetadataExtractor.extract(image);

    }
}
