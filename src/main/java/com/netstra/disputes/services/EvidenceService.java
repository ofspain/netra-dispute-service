package com.netstra.disputes.services;

import com.netra.commons.models.Evidence;
import com.netstra.disputes.idempotency.IdempotencyContext;
import com.netstra.disputes.model.ProcessedEvidenceDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EvidenceService {


    //private final EvidenceRepository evidenceRepository;

    public List<Evidence> save(List<String> rawEvidences, Long disputeId) {
        //todo:upload to s3
        //todo:convert raw evidences to concrete evidence object
        //todo:return list


        return new ArrayList<>();
    }

    public Evidence updateEvidence(Evidence evidence, Long id){
        //todo:update by id here
        return null;
    }

    public Evidence findEvidenceById(Long id){
        //todo:find by id
        return null;
    }

    public ProcessedEvidenceDTO.ProcessedEvidenceStatus confirmProcessedImageStatus(ProcessedEvidenceDTO processedEvidence, IdempotencyContext idempotencyContext){
        return null;
    }
}
