package com.netstra.disputes.model;

import lombok.Data;

import java.util.List;

@Data
public class ProcessedEvidences {
    private Long disputeId;
    private List<ProcessedEvidenceDTO> processedEvidenceDTOs;

}
