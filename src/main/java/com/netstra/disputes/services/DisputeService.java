package com.netstra.disputes.services;

import com.netra.commons.models.Dispute;
import com.netstra.disputes.dao.DisputeDao;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

package com.netra.dispute.service;

import com.netra.commons.enums.DisputeState;
import com.netra.commons.models.Dispute;
import com.netra.commons.models.DisputeJournal;
import com.netra.commons.models.Transaction;
import com.netra.commons.requests.CreateDisputeRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DisputeService {

    private final DisputeDao disputeRepository;
    private final TransactionRepository transactionRepository;


    @Transactional
    public Dispute createDispute(CreateDisputeRequest request) {
        // Validate the transaction exists or save it if new
        Transaction txn = request.getTransaction();
        if (txn.getId() == null) {
            txn = transactionRepository.save(txn);
        }

        // Initialize Dispute
        Dispute dispute = new Dispute();
        dispute.setTransaction(txn);
        dispute.setCreatedVia(request.getApplicationChannel());
        dispute.setCreatedBy(request.getInitiator());
        dispute.setIssuerCode(txn.getIssuer().getCode());
        dispute.setAcquirerCode(txn.getAcquirer().getCode());
        dispute.setDomainCode(txn.getIssuer().getCode());
        dispute.setBeneficiaryCode(txn.getBeneficiary().getCode());
        dispute.setDisputeMode(request.getMode());
        dispute.setNote(request.getNote());
        dispute.setLocked(false);
        dispute.setFinalized(false);
        dispute.setResolved(false);
        dispute.setResolvedInCustomerFavor(false);
        dispute.setLogCode(generateLogCode());
        dispute.setCurrentState(DisputeState.BOOTSTRAP_DISPUTE_CONTEXT);
        dispute.setPreviousState(null);
        dispute.setCreatedAt(LocalDateTime.now());
        dispute.setEvidences(request.getEvidences());

        // Create initial journal/log
        DisputeJournal journal = new DisputeJournal();
        journal.setDisputeId(dispute.getId());
        journal.setMessage("Dispute created by " + request.getInitiator());
        journal.setCreatedAt(LocalDateTime.now());
        dispute.getDisputeJournals().add(journal);

        // Persist dispute
        return disputeRepository.save(dispute);
    }

    private String generateLogCode() {
        return "DSP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}

