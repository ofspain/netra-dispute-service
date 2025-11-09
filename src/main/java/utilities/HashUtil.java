package utilities;

import javax.imageio.ImageIO;
import java.awt.color.ColorSpace;
import java.awt.geom.AffineTransform;
import java.awt.image.*;
import java.awt.image.ColorConvertOp;
import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.util.Objects;

/**
 * Production-grade average hash (aHash) utility.
 * Provides deterministic, thread-safe image fingerprinting for duplicate detection.
 *
 * <p>Usage:
 * <pre>
 *     String hash = AverageHashUtil.computeAverageHash(file);
 *     int distance = AverageHashUtil.hammingDistance(hash1, hash2);
 * </pre>
 */
public final class HashUtil {

    private static final int HASH_SIZE = 8; // 8x8 = 64-bit hash
    private static final int HASH_LENGTH_HEX = (HASH_SIZE * HASH_SIZE) / 4; // 16 chars for 64 bits

    // prevent instantiation
    private HashUtil() {}

    /**
     * Computes the average hash (aHash) for a given image file.
     *
     * @param imageFile the input image file
     * @return zero-padded 16-char hex-encoded hash string
     * @throws IOException if the image cannot be read or processed
     */
    public static String computeAverageHash(File imageFile) throws IOException {
        Objects.requireNonNull(imageFile, "imageFile cannot be null");

        if (!imageFile.exists() || !imageFile.isFile()) {
            throw new IOException("Image file not found or invalid: " + imageFile.getAbsolutePath());
        }

        // Disable disk caching to prevent temp file bloat in high throughput systems
        ImageIO.setUseCache(false);

        BufferedImage original = ImageIO.read(imageFile);
        if (original == null) {
            throw new IOException("Unsupported or unreadable image format: " + imageFile.getName());
        }

        // Convert to grayscale
        BufferedImage gray = new BufferedImage(
                original.getWidth(),
                original.getHeight(),
                BufferedImage.TYPE_BYTE_GRAY);
        new ColorConvertOp(ColorSpace.getInstance(ColorSpace.CS_GRAY), null)
                .filter(original, gray);

        // Resize to HASH_SIZE × HASH_SIZE using high-quality bilinear interpolation
        BufferedImage scaled = new BufferedImage(HASH_SIZE, HASH_SIZE, BufferedImage.TYPE_BYTE_GRAY);
        AffineTransform transform = AffineTransform.getScaleInstance(
                (double) HASH_SIZE / gray.getWidth(),
                (double) HASH_SIZE / gray.getHeight());
        AffineTransformOp scaleOp = new AffineTransformOp(transform, AffineTransformOp.TYPE_BILINEAR);
        scaleOp.filter(gray, scaled);

        // Compute average hash
        return computeHashFromPixels(scaled);
    }

    /**
     * Converts pixel intensities to a binary hash and encodes as fixed-length hex.
     */
    private static String computeHashFromPixels(BufferedImage image) {
        byte[] pixels = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();

        long sum = 0L;
        int[] grayValues = new int[pixels.length];
        for (int i = 0; i < pixels.length; i++) {
            int v = pixels[i] & 0xFF;
            grayValues[i] = v;
            sum += v;
        }

        int avg = (int) (sum / grayValues.length);

        // Build binary string
        StringBuilder bits = new StringBuilder(grayValues.length);
        for (int g : grayValues) {
            bits.append(g >= avg ? '1' : '0');
        }

        BigInteger bi = new BigInteger(bits.toString(), 2);
        String hex = bi.toString(16);

        // zero-pad to fixed 16-char length
        if (hex.length() < HASH_LENGTH_HEX) {
            hex = "0".repeat(HASH_LENGTH_HEX - hex.length()) + hex;
        }

        return hex;
    }

    /**
     * Computes the Hamming distance between two average hashes.
     * Smaller values indicate more similar images.
     *
     * @param hash1 first hash (hex)
     * @param hash2 second hash (hex)
     * @return number of differing bits
     * @throws IllegalArgumentException if either hash is invalid
     */
    public static int hammingDistance(String hash1, String hash2) {
        if (hash1 == null || hash2 == null) {
            throw new IllegalArgumentException("Hashes cannot be null");
        }

        BigInteger b1 = new BigInteger(hash1, 16);
        BigInteger b2 = new BigInteger(hash2, 16);
        return b1.xor(b2).bitCount();
    }
}

