package com.netstra.disputes.model;

import lombok.Data;

@Data
public class ProcessedEvidenceDTO {

    private Long evidenceId;
    private String pHash;

    private Object data; //todo: define an data type to capture full response from vision service

    public enum ProcessedEvidenceStatus{
        EVIDENCE_VERIFIED,
        EVIDENCE_REJECTED,

        EVIDENCE_INDETERMINATE;
    }
}
