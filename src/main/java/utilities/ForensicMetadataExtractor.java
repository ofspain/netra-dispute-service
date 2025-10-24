package utilities;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.*;
import com.drew.metadata.exif.*;
import com.drew.metadata.iptc.IptcDirectory;
import com.drew.metadata.xmp.XmpDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Extracts EXIF/IPTC/XMP and file-system metadata for forensic validation.
 * Designed to fail gracefully and flag potential manipulation indicators.
 * Thread-safe and production-ready with comprehensive error handling.
 */
public final class ForensicMetadataExtractor {

    private static final Logger log = LoggerFactory.getLogger(ForensicMetadataExtractor.class);

    // Compile patterns once for efficiency
    private static final Pattern EDITING_SOFTWARE_PATTERN =
            Pattern.compile("(?i).*photoshop|snapseed|pixlr|gimp|lightroom|affinity|coreldraw.*");
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "tiff", "tif", "heic", "webp", "bmp", "gif"
    );

    // Cache for MessageDigest instances (thread-local for thread safety)
    private static final ThreadLocal<MessageDigest> MD5_DIGEST = ThreadLocal.withInitial(() -> {
        try {
            return MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 algorithm not available", e);
        }
    });

    private static final ThreadLocal<MessageDigest> SHA256_DIGEST = ThreadLocal.withInitial(() -> {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    });

    private ForensicMetadataExtractor() {}

    /**
     * Extracts forensic metadata from an image file with comprehensive validation
     */
    public static ForensicMetadata extract(File imageFile) {
        return extract(imageFile, ExtractionOptions.defaultOptions());
    }

    /**
     * Extracts forensic metadata with customizable options
     */
    public static ForensicMetadata extract(File imageFile, ExtractionOptions options) {
        Objects.requireNonNull(imageFile, "Image file cannot be null");
        Objects.requireNonNull(options, "Extraction options cannot be null");

        // Pre-validation
        validateFile(imageFile, options);

        String fileHashMd5 = null;
        String fileHashSha256 = null;
        Instant fileCreated = null;
        Instant fileModified = null;
        long fileSize = imageFile.length();

        try {
            // Calculate file hashes for integrity verification
            if (options.calculateHashes()) {
                Map<String, String> hashes = calculateFileHashes(imageFile);
                fileHashMd5 = hashes.get("MD5");
                fileHashSha256 = hashes.get("SHA-256");
            }

            // Extract file system attributes
            BasicFileAttributes attr = Files.readAttributes(imageFile.toPath(), BasicFileAttributes.class);
            fileCreated = attr.creationTime().toInstant();
            fileModified = attr.lastModifiedTime().toInstant();

        } catch (IOException e) {
            log.warn("Failed to read file attributes for: {}", imageFile.getName(), e);
        }

        // Extract image metadata
        ImageMetadataResult metadataResult = extractImageMetadata(imageFile, options);

        return buildForensicMetadata(
                metadataResult, fileCreated, fileModified, fileSize, fileHashMd5, fileHashSha256
        );
    }

    private static void validateFile(File imageFile, ExtractionOptions options) {
        if (!imageFile.exists()) {
            throw new IllegalArgumentException("Image file does not exist: " + imageFile.getAbsolutePath());
        }
        if (!imageFile.isFile()) {
            throw new IllegalArgumentException("Path is not a file: " + imageFile.getAbsolutePath());
        }
        if (!imageFile.canRead()) {
            throw new IllegalArgumentException("Cannot read image file: " + imageFile.getAbsolutePath());
        }

        String fileName = imageFile.getName().toLowerCase();
        boolean hasSupportedExtension = SUPPORTED_EXTENSIONS.stream()
                .anyMatch(ext -> fileName.endsWith("." + ext));

        if (!hasSupportedExtension && options.strictFileTypeValidation()) {
            throw new IllegalArgumentException("Unsupported image format: " + imageFile.getName());
        }
    }

    private static ImageMetadataResult extractImageMetadata(File imageFile, ExtractionOptions options) {
        ImageMetadataResult result = new ImageMetadataResult();

        try {
            Metadata metadata = ImageMetadataReader.readMetadata(imageFile);
            result.metadataReadSuccess = true;

            // Extract EXIF metadata
            extractExifMetadata(metadata, result);

            // Extract GPS data if available
            extractGpsMetadata(metadata, result);

            // Extract software information from multiple sources
            extractSoftwareMetadata(metadata, result);

            // Calculate metadata completeness score
            result.metadataCompleteness = calculateMetadataCompleteness(result);

        } catch (ImageProcessingException e) {
            log.warn("Image processing error for {}: {}", imageFile.getName(), e.getMessage());
            result.metadataReadSuccess = false;
            result.redFlagMissingMetadata = true;
        } catch (Exception e) {
            log.error("Unexpected error processing image metadata for {}: {}",
                    imageFile.getName(), e.getMessage(), e);
            result.metadataReadSuccess = false;
            result.redFlagMissingMetadata = true;
        }

        return result;
    }

    private static void extractExifMetadata(Metadata metadata, ImageMetadataResult result) {
        ExifSubIFDDirectory exif = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
        if (exif != null) {
            result.exifDate = safeGetDate(exif::getDateOriginal);
            result.exifDateTimeDigitized = safeGetDate(exif::getDateDigitized);
        }

        ExifIFD0Directory exifIFD0 = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
        if (exifIFD0 != null) {
            result.make = safeGetString(() -> exifIFD0.getString(ExifIFD0Directory.TAG_MAKE));
            result.model = safeGetString(() -> exifIFD0.getString(ExifIFD0Directory.TAG_MODEL));
            result.orientation = safeGetString(() -> exifIFD0.getDescription(ExifIFD0Directory.TAG_ORIENTATION));
            result.software = safeGetString(() -> exifIFD0.getString(ExifIFD0Directory.TAG_SOFTWARE));
        }
    }

    private static void extractGpsMetadata(Metadata metadata, ImageMetadataResult result) {
        GpsDirectory gps = metadata.getFirstDirectoryOfType(GpsDirectory.class);
        if (gps != null) {
            try {
                // Get the GeoLocation object which contains both latitude and longitude
                com.drew.lang.GeoLocation geoLocation = gps.getGeoLocation();
                if (geoLocation != null) {
                    result.lat = geoLocation.getLatitude();
                    result.lon = geoLocation.getLongitude();
                }
            } catch (Exception e) {
                log.trace("Failed to extract GPS coordinates: {}", e.getMessage());
                result.lat = null;
                result.lon = null;
            }

            // Altitude and timestamp can still be extracted directly
            result.alt = safeGetDouble(() -> gps.getDoubleObject(GpsDirectory.TAG_ALTITUDE));

            // Extract GPS timestamp if available
            result.gpsTimestamp = safeGetDate(() -> gps.getGpsDate());
        }
    }

    private static void extractSoftwareMetadata(Metadata metadata, ImageMetadataResult result) {
        // Prefer existing software info, fall back to other sources
        if (result.software == null || result.software.trim().isEmpty()) {
            IptcDirectory iptc = metadata.getFirstDirectoryOfType(IptcDirectory.class);
            if (iptc != null) {
                result.software = safeGetString(() ->
                        iptc.getString(IptcDirectory.TAG_ORIGINAL_TRANSMISSION_REFERENCE));
            }
        }

        if (result.software == null || result.software.trim().isEmpty()) {
            XmpDirectory xmp = metadata.getFirstDirectoryOfType(XmpDirectory.class);
            if (xmp != null) {
                result.software = xmp.getXmpProperties().get("Software");
            }
        }
    }

    private static double calculateMetadataCompleteness(ImageMetadataResult result) {
        int totalFields = 0;
        int populatedFields = 0;

        if (result.make != null) populatedFields++; totalFields++;
        if (result.model != null) populatedFields++; totalFields++;
        if (result.software != null) populatedFields++; totalFields++;
        if (result.exifDate != null) populatedFields++; totalFields++;
        if (result.lat != null) populatedFields++; totalFields++;
        if (result.lon != null) populatedFields++; totalFields++;

        return totalFields > 0 ? (double) populatedFields / totalFields : 0.0;
    }

    private static ForensicMetadata buildForensicMetadata(
            ImageMetadataResult metadataResult,
            Instant fileCreated, Instant fileModified, long fileSize,
            String fileHashMd5, String fileHashSha256) {

        // Calculate red flags
        boolean redFlagTimestampMismatch = isTimestampMismatch(metadataResult.exifDate, fileModified);
        boolean redFlagEditingSoftware = isEditingSoftwareDetected(metadataResult.software);
        boolean redFlagMissingMetadata = isMetadataMissing(metadataResult);

        return new ForensicMetadata(
                metadataResult.make,
                metadataResult.model,
                metadataResult.software,
                metadataResult.exifDate,
                metadataResult.exifDateTimeDigitized,
                metadataResult.gpsTimestamp,
                metadataResult.lat,
                metadataResult.lon,
                metadataResult.alt,
                metadataResult.orientation,
                fileCreated,
                fileModified,
                fileSize,
                fileHashMd5,
                fileHashSha256,
                metadataResult.metadataCompleteness,
                redFlagTimestampMismatch,
                redFlagEditingSoftware,
                redFlagMissingMetadata,
                metadataResult.metadataReadSuccess,
                System.currentTimeMillis() // extraction timestamp
        );
    }

    private static boolean isTimestampMismatch(Instant exifDate, Instant fileModified) {
        return exifDate != null && fileModified != null && fileModified.isBefore(exifDate);
    }

    private static boolean isEditingSoftwareDetected(String software) {
        return software != null && EDITING_SOFTWARE_PATTERN.matcher(software).matches();
    }

    private static boolean isMetadataMissing(ImageMetadataResult result) {
        return !result.metadataReadSuccess ||
                (result.exifDate == null && result.make == null && result.model == null);
    }

    private static Map<String, String> calculateFileHashes(File file) throws IOException {
        Map<String, String> hashes = new HashMap<>();
        byte[] fileBytes = Files.readAllBytes(file.toPath());

        MessageDigest md5 = MD5_DIGEST.get();
        MessageDigest sha256 = SHA256_DIGEST.get();

        md5.reset();
        sha256.reset();

        hashes.put("MD5", bytesToHex(md5.digest(fileBytes)));
        hashes.put("SHA-256", bytesToHex(sha256.digest(fileBytes)));

        return hashes;
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    // Safe extraction methods
    private static Instant safeGetDate(SupplierWithException<java.util.Date> supplier) {
        try {
            java.util.Date date = supplier.get();
            return date != null ? date.toInstant() : null;
        } catch (Exception e) {
            log.trace("Failed to extract date: {}", e.getMessage());
            return null;
        }
    }

    private static String safeGetString(SupplierWithException<String> supplier) {
        try {
            return supplier.get();
        } catch (Exception e) {
            log.trace("Failed to extract string: {}", e.getMessage());
            return null;
        }
    }

    private static Double safeGetDouble(SupplierWithException<Double> supplier) {
        try {
            return supplier.get();
        } catch (Exception e) {
            log.trace("Failed to extract double: {}", e.getMessage());
            return null;
        }
    }

    @FunctionalInterface
    private interface SupplierWithException<T> {
        T get() throws Exception;
    }

    /**
     * Configuration options for metadata extraction
     */
    public static class ExtractionOptions {
        private final boolean calculateHashes;
        private final boolean strictFileTypeValidation;

        private ExtractionOptions(boolean calculateHashes, boolean strictFileTypeValidation) {
            this.calculateHashes = calculateHashes;
            this.strictFileTypeValidation = strictFileTypeValidation;
        }

        public static ExtractionOptions defaultOptions() {
            return new ExtractionOptions(true, true);
        }

        public static ExtractionOptions lenientOptions() {
            return new ExtractionOptions(false, false);
        }

        public boolean calculateHashes() { return calculateHashes; }
        public boolean strictFileTypeValidation() { return strictFileTypeValidation; }

        public ExtractionOptions withHashes(boolean calculateHashes) {
            return new ExtractionOptions(calculateHashes, this.strictFileTypeValidation);
        }

        public ExtractionOptions withStrictValidation(boolean strictValidation) {
            return new ExtractionOptions(this.calculateHashes, strictValidation);
        }
    }

    /**
     * Intermediate result container for image metadata
     */
    private static class ImageMetadataResult {
        String make;
        String model;
        String software;
        String orientation;
        Instant exifDate;
        Instant exifDateTimeDigitized;
        Instant gpsTimestamp;
        Double lat;
        Double lon;
        Double alt;
        boolean metadataReadSuccess = false;
        boolean redFlagMissingMetadata = false;
        double metadataCompleteness = 0.0;
    }
}
