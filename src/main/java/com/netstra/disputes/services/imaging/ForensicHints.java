package com.netstra.disputes.services.imaging;

import utilities.ForensicMetadata;

import java.time.Instant;
import java.util.Map;

public record ForensicHints(
        String fileHashMd5,
        String fileHashSha256,
        long fileSize,
        double metadataCompleteness,
        double confidenceScore,
        Map<String, Boolean> tamperingIndicators,
        Map<String, Object> exif,
        Map<String, Instant> fileTimestamps
) {

    /**
     * Construct ForensicHints from existing ForensicMetadata
     */
    public ForensicHints(ForensicMetadata metadata) {
        this(
                metadata.fileHashMd5(),
                metadata.fileHashSha256(),
                metadata.fileSize(),
                metadata.metadataCompleteness(),
                metadata.getConfidenceScore(),
                Map.of(
                        "redFlagTimestampMismatch", metadata.redFlagTimestampMismatch(),
                        "redFlagEditingSoftware", metadata.redFlagEditingSoftware(),
                        "redFlagMissingMetadata", metadata.redFlagMissingMetadata()
                ),
                Map.of(
                        "make", metadata.make(),
                        "model", metadata.model(),
                        "software", metadata.software(),
                        "orientation", metadata.orientation(),
                        "exifDateTimeOriginal", metadata.exifDateTimeOriginal(),
                        "exifDateTimeDigitized", metadata.exifDateTimeDigitized(),
                        "gpsLatitude", metadata.latitude(),
                        "gpsLongitude", metadata.longitude(),
                        "gpsAltitude", metadata.altitude(),
                        "gpsTimestamp", metadata.gpsTimestamp()
                ),
                Map.of(
                        "fileCreated", metadata.fileCreated(),
                        "fileModified", metadata.fileModified(),
                        "extractionTimestamp", Instant.ofEpochMilli(metadata.extractionTimestamp())
                )
        );
    }
}

