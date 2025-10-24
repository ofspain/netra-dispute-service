package utilities;

import java.util.Base64;


public class FileUtils {

    public static byte[] decodeBase64(String base64) {
        if (base64 == null || base64.isEmpty()) {
            return new byte[0];
        }

        // Handle data URI format: data:[mime];base64,xxxxx
        if (base64.startsWith("data:")) {
            int commaIndex = base64.indexOf(",");
            if (commaIndex >= 0) {
                base64 = base64.substring(commaIndex + 1);
            }
        }

        // Decode, allowing for possible newlines/spaces in base64
        return Base64.getMimeDecoder().decode(base64.trim());
    }

    public static String detectMimeType(byte[] data) {
        if (data == null || data.length < 4) return "application/octet-stream";

        // PNG: 89 50 4E 47
        if (data[0] == (byte)0x89 && data[1] == 0x50 && data[2] == 0x4E && data[3] == 0x47) {
            return "image/png";
        }

        // JPG: FF D8 FF
        if (data[0] == (byte)0xFF && data[1] == (byte)0xD8 && data[2] == (byte)0xFF) {
            return "image/jpeg";
        }

        // PDF: 25 50 44 46
        if (data[0] == 0x25 && data[1] == 0x50 && data[2] == 0x44 && data[3] == 0x46) {
            return "application/pdf";
        }

        // GIF: 47 49 46 38
        if (data[0] == 0x47 && data[1] == 0x49 && data[2] == 0x46 && data[3] == 0x38) {
            return "image/gif";
        }

        // Default fallback
        return "application/octet-stream";
    }

    public static String extensionFromMime(String mime) {
        switch (mime) {
            case "image/png": return "png";
            case "image/jpeg": return "jpg";
            case "application/pdf": return "pdf";
            case "image/gif": return "gif";
            default: return "bin";
        }
    }

    public  static String detectExtension(byte[] data) {
        return extensionFromMime(detectMimeType(data));
    }


    public static String getMimeType(String dataUri) {
        String[] parts = dataUri.split(",");
        if (parts.length > 0 && parts[0].startsWith("data:")) {
            return parts[0].substring(5, parts[0].indexOf(";"));
        }
        return "application/octet-stream";
    }

    public static byte[] getBytes(String dataUri) {
        return Base64.getDecoder().decode(dataUri.split(",")[1]);
    }

    public static String getExtension(String dataUri) {
        switch (getMimeType(dataUri)) {
            case "image/png": return "png";
            case "image/jpeg": return "jpg";
            case "application/pdf": return "pdf";
            case "image/gif": return "gif";
            default: return "bin";
        }
    }

    public static String s3Signed(String key){
        return "";
    }
}