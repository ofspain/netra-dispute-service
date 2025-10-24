package utilities;

import java.time.Instant;

/**
 * Comprehensive forensic metadata container with validation flags
 */
public record ForensicMetadata(
        String make,
        String model,
        String software,
        Instant exifDateTimeOriginal,
        Instant exifDateTimeDigitized,
        Instant gpsTimestamp,
        Double latitude,
        Double longitude,
        Double altitude,
        String orientation,
        Instant fileCreated,
        Instant fileModified,
        long fileSize,
        String fileHashMd5,
        String fileHashSha256,
        double metadataCompleteness, // 0.0 to 1.0
        boolean redFlagTimestampMismatch,
        boolean redFlagEditingSoftware,
        boolean redFlagMissingMetadata,
        boolean metadataReadSuccess,
        long extractionTimestamp
) {
    public ForensicMetadata {
        // Validation
        if (fileSize < 0) {
            throw new IllegalArgumentException("File size cannot be negative");
        }
        if (metadataCompleteness < 0 || metadataCompleteness > 1) {
            throw new IllegalArgumentException("Metadata completeness must be between 0 and 1");
        }
    }

    /**
     * Returns overall confidence score based on metadata completeness and red flags
     */
    public double getConfidenceScore() {
        double score = metadataCompleteness * 0.7; // 70% weight to completeness

        // Deduct for red flags
        if (redFlagTimestampMismatch) score -= 0.2;
        if (redFlagEditingSoftware) score -= 0.15;
        if (redFlagMissingMetadata) score -= 0.25;

        return Math.max(0.0, score);
    }

    /**
     * Checks if metadata indicates potential tampering
     */
    public boolean hasTamperingIndicators() {
        return redFlagTimestampMismatch || redFlagEditingSoftware || redFlagMissingMetadata;
    }
}
