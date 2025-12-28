package com.netstra.disputes.services.imaging;

import lombok.Data;

@Data
public class ImageValidationResult {

    private ForensicHints forensicHints;

    private String averageHash;

    private boolean validAverageHash;

    private String averageHashRejectionReason;

    private String fileURL;

    private String fileKey;
}
