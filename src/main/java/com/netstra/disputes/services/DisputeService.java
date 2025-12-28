package com.netstra.disputes.services;

import com.netra.commons.enums.DisputeMode;
import com.netra.commons.enums.DisputeState;
import com.netra.commons.enums.DisputeTransitionEvent;
import com.netra.commons.models.*;
import com.netra.commons.requests.CreateDisputeRequest;
import com.netra.commons.requests.util.TransactionRailDTO;
import com.netstra.disputes.dao.DisputeDao;
import com.netstra.disputes.idempotency.GenericIdempotencyService;
import com.netstra.disputes.idempotency.IdempotencyContext;
import com.netstra.disputes.model.ProcessedEvidenceDTO;
import com.netstra.disputes.model.ProcessedEvidences;
import com.netstra.disputes.security.DomainAwarePrincipal;
import com.netstra.disputes.transitions.config.StateMachineRegistry;
import com.netstra.disputes.transitions.service.DisputeStateMachineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.StateMachineEventResult;
import org.springframework.stereotype.Service;
import com.netra.commons.models.Dispute;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DisputeService {

    private final DisputeDao disputeRepository;

    private final DisputeJourneyTraceService disputeJourneyTraceService;

    private final GenericIdempotencyService idempotencyService;
    private final StateMachineRegistry stateMachineRegistry;
    private final EvidenceService evidenceService;
    private final DisputeStateMachineService disputeStateMachineService;

    //TODO: IDEM IN DISPUTE CREATION: stan, rrn


    @Transactional
    public Dispute create(CreateDisputeRequest request) {


//
//        // Initialize Dispute
        Dispute dispute = new Dispute();
        OtherTransactionInfo transactionInfo = new OtherTransactionInfo();

        dispute.setTransactionDate(request.getTransactionDate());
        dispute.setTransactionAmount(request.getTransactionAmount());
        dispute.setTransactionAction(request.getTransactionAction());
        TransactionRailDTO railDTO = request.getTransactionRail();
        if(null != railDTO){
            dispute.setTransactionPaymentRail(railDTO.getPaymentRail());
            transactionInfo.setTransactionRail(railDTO);
            dispute.setTransactionInstrument(railDTO.getInstrument());
            dispute.setTransactionPaymentRail(railDTO.getPaymentRail());
        }

        if(null != request.getError()){
            transactionInfo.setError(request.getError());
        }

        transactionInfo.setAuthorizationCode(request.getAuthCode());

        transactionInfo.setRrn(request.getRrn());
        transactionInfo.setStan(request.getStan());
        dispute.setTransactionAction(request.getTransactionAction());
        dispute.setCreatedVia(request.getApplicationChannel());
        dispute.setCreatedBy(request.getInitiator());
        dispute.setNote(request.getNote());

        //assumed affected account is saved on the platform
        AccountDetail affected = request.getAffectedAccount();
        if(null != affected){
            dispute.setIssuerCode(affected.getIssuingInstitution().getDomainCode());
            transactionInfo.setCard(affected.getCard());
        }

        AccountDetail beneficiaryAccount = request.getBeneficiaryAccount();
        if(null != beneficiaryAccount){
            dispute.setBeneficiaryCode(beneficiaryAccount.getIssuingInstitution().getDomainCode());
        }

        dispute.setDisputeMode(request.getMode());
        dispute.setNote(request.getNote());
        dispute.setLocked(false);
        dispute.setFinalized(false);
        dispute.setResolved(false);
        dispute.setResolvedInCustomerFavor(false);
        dispute.setCurrentState(DisputeState.BOOTSTRAP_DISPUTE_CONTEXT);
        dispute.setCreatedAt(LocalDateTime.now());
        dispute.setDisputeMode(request.getDisputeMode());

        //todo: save info too: default currency to naira for now

        // Persist dispute
        dispute =  disputeRepository.save(dispute);


        BaseUser user = request.getInitiator();
        Identity identity = user.getIdentity();

        DisputeJourneyTrace disputeJourneyTrace = new DisputeJourneyTrace();
        DisputeJourneyTrace journeyTrace = new DisputeJourneyTrace();
        journeyTrace.setDisputeId(String.valueOf(dispute.getId()));
        journeyTrace.setFromState(null);
        journeyTrace.setToState(dispute.getCurrentState());
        journeyTrace.setTransitionTime(LocalDateTime.now());
        journeyTrace.setInitiatedBy(identity.getUsername());
        journeyTrace.setInitiatedByDomainCode(identity.getDomainCode());
        journeyTrace.setInitiatedByUUID(identity.getIdentityUuid());
        journeyTrace.setInitiatedByDomainType(null != identity.getDomainType() ? identity.getDomainType().name() : null);
        journeyTrace.setApplicationChannel(request.getApplicationChannel().name());


        disputeJourneyTraceService.recordTransition(disputeJourneyTrace);

        return dispute;

    }

    public Optional<Dispute> findById(Long id){
        return Optional.empty();
    }

    private String generateLogCode() {
        return "DSP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }


    //todo: most likely i move to a component that specifically handles kafka consumption
    public void handleEvidenceProcessedByVisionService(){
            //consumes messsage from kakfa serialized as ProcessedEvidences
        ProcessedEvidences processedEvidences = new ProcessedEvidences();//fake
        //compute idem context too
        IdempotencyContext idempotencyContext = null; //new IdempotencyContext();//fake

        Long disputeId = processedEvidences.getDisputeId();

//        if (idempotencyContext.isReplay()) {
//            // do something and return fast
//
//        }

        List<ProcessedEvidenceDTO> processedEvidenceDTOs = processedEvidences.getProcessedEvidenceDTOs();

        List<ProcessedEvidenceDTO> verifiedEvidences = new ArrayList<>();
        List<ProcessedEvidenceDTO> manualReviewEvidences = new ArrayList<>();
        List<ProcessedEvidenceDTO> rejectedEvidences = new ArrayList<>();

        for(ProcessedEvidenceDTO processedEvidence : processedEvidenceDTOs){
            //todo: get if this dto is acceptable and add to the verifiedEvidences, manualReviewEvidences, rejectedEvidences
            //todo: update the evidence with the pHash
            ProcessedEvidenceDTO.ProcessedEvidenceStatus status = evidenceService.confirmProcessedImageStatus(processedEvidence, idempotencyContext);
            switch (status){
                case EVIDENCE_REJECTED -> {
                    rejectedEvidences.add(processedEvidence);
                }
                case EVIDENCE_VERIFIED -> {
                    verifiedEvidences.add(processedEvidence);
                }
                case EVIDENCE_INDETERMINATE -> {
                    manualReviewEvidences.add(processedEvidence);
                }
            }

        }

        Dispute dispute = findById(disputeId).orElseThrow(
                () -> new IllegalStateException("Dispute not found: " + disputeId)
        );

        DisputeMode mode = null; //request.getMode();
        StateMachine<DisputeState, DisputeTransitionEvent> sm =
                stateMachineRegistry.getStateMachineFactory(dispute.getDisputeMode()).getStateMachine();


        sm.getExtendedState().getVariables().put("dispute", dispute); // ← ADD THIS
        sm.getExtendedState().getVariables().put("rejectedEvidences", rejectedEvidences);
        sm.getExtendedState().getVariables().put("manualReviewEvidences", manualReviewEvidences);
        sm.getExtendedState().getVariables().put("verifiedEvidences", verifiedEvidences);

        sm.getExtendedState().getVariables().put("idempotencyContext", idempotencyContext);

        //calculate intended state here based on the evidences types, eg

        DisputeState intendedState = determineTargetState(verifiedEvidences,manualReviewEvidences,rejectedEvidences);


        sm.getExtendedState().getVariables().put("intendedState", intendedState);


        // ---------------------------------------------------------------------
        // 4) Fire bootstrap event
        // ---------------------------------------------------------------------
        sm.startReactively()
                .then(
                        sm.sendEventCollect(Mono.just(
                                MessageBuilder.withPayload(DisputeTransitionEvent.EVENT_BOOTSTRAP_CONTEXT_USER)
                                        .setHeader("disputeId", dispute.getId()) // ← ADD THIS
                                        .setHeader("idempotencyContext", idempotencyContext)
                                        .setHeader("actorID", idempotencyContext.getActorId())
                                        .setHeader("actorType", idempotencyContext.getActorType())
                                        .setHeader("mode", mode)
                                        .build()
                        ))
                )
                .handle((results, sink) -> {
                    boolean accepted = results.stream().anyMatch(r ->
                            r.getResultType() == StateMachineEventResult.ResultType.ACCEPTED);

                    if (!accepted) {
                        sink.error(new IllegalStateException("Dispute creation rejected by state machine"));
                        return;
                    }

                    // Reload to get any updates from state machine actions: todo: use app specific exception here too
                    sink.next(disputeRepository.findByIdOrLogCode(dispute.getId(), null)
                            .orElseThrow(() -> new IllegalStateException("Dispute not found after state machine processing")));
                })
                .onErrorResume(error -> {
                    // 🚨 Ensure dispute is marked as failed on any error
//                    dispute.setStatus(DisputeState.FAILED);
//                    disputeRepository.save(dispute);
                    return Mono.error(error);
                })
                .block();


    }

    public Dispute updateDispute(Dispute dispute, Long id){
        dispute = disputeRepository.update(dispute, id);
        return dispute;
    }

    //todo: consider returning dispute with all its journey traces
    @Transactional
    public Dispute updateDisputeWithTransition(Dispute dispute, Long id){

        DisputeJourneyTrace journeyTrace = new DisputeJourneyTrace();


        //todo: construct journeyTrace before this
        journeyTrace = disputeJourneyTraceService.recordTransition(journeyTrace);

        return updateDispute(dispute, id);
    }

    private DisputeState determineTargetState(List<ProcessedEvidenceDTO> verifiedEvidences,
                                             List<ProcessedEvidenceDTO> manualReviewEvidences,
                                             List<ProcessedEvidenceDTO> rejectedEvidences) {

        int totalEvidences = verifiedEvidences.size() + manualReviewEvidences.size() + rejectedEvidences.size();

        // 🚨 CRITICAL: No evidences at all
        if (totalEvidences == 0) {
            log.warn("No evidences provided for verification");
            return DisputeState.EVIDENCE_REJECTED; // Or FAILED if you have that state
        }

        // 🎯 RULE 1: ANY rejected evidence → Overall rejection
        if (!rejectedEvidences.isEmpty()) {
            log.info("{} rejected evidence(s) found, moving to EVIDENCE_REJECTED", rejectedEvidences.size());
            return DisputeState.EVIDENCE_REJECTED;
        }

        // 🎯 RULE 2: ANY evidence needs manual review → Manual review
        if (!manualReviewEvidences.isEmpty()) {
            log.info("{} evidence(s) need manual review, moving to AWAITING_MANUAL_EVIDENCE_REVIEW",
                    manualReviewEvidences.size());
            return DisputeState.AWAITING_MANUAL_EVIDENCE_REVIEW;
        }

        // 🎯 RULE 3: ALL evidences verified → Success!
        if (!verifiedEvidences.isEmpty() && manualReviewEvidences.isEmpty() && rejectedEvidences.isEmpty()) {
            log.info("All {} evidence(s) verified, moving to EVIDENCE_VERIFIED", verifiedEvidences.size());
            return DisputeState.EVIDENCE_VERIFIED;
        }

        // 🚨 Fallback - should not happen with above logic
        log.error("Unexpected evidence state - verified: {}, manual: {}, rejected: {}",
                verifiedEvidences.size(), manualReviewEvidences.size(), rejectedEvidences.size());
        return DisputeState.EVIDENCE_REJECTED;
    }
}

