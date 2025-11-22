package com.netstra.disputes.services;

import com.netra.commons.enums.DisputeState;
import com.netra.commons.enums.DisputeTransitionEvent;
import com.netra.commons.models.Dispute;
import com.netra.commons.requests.CreateDisputeRequest;
import com.netstra.disputes.model.IdempotencyContext;
import com.netstra.disputes.security.DomainAwarePrincipal;
import com.netstra.disputes.services.validation.image.ImageValidationOrchestrator;
import com.netstra.disputes.transitions.service.DisputeStateMachineService;
import com.netstra.disputes.transitions.service.StateTransitionResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DisputeIntakeService {

    private final ImageValidationOrchestrator imageValidationOrchestrator;
    private final IdempotencyTokenService idempotencyService;
    private final DisputeService disputeService; // ← ADD THIS
    private final DisputeStateMachineService disputeStateMachineService;

    public Dispute initiateUserDispute(CreateDisputeRequest request,
                                       DomainAwarePrincipal user,
                                       String idempotencyKey) {

        // ---------------------------------------------------------------------
        // 0) Construct idempotency context BEFORE doing any heavy work
        // ---------------------------------------------------------------------
        IdempotencyContext idemCtx = IdempotencyContext.builder()
                .key(idempotencyKey)
                .actor(user.getDomainCode() + ":" + user.getIDonHostDB())
                .operation(IdempotencyContext.IdemOperation.CREATE_DISPIUTE)
                .fingerprint(IdempotencyContext.generateCanonicalFingerPrint(request))
                .createdAt(LocalDateTime.now())
                .build();

        // ---------------------------------------------------------------------
        // 1) Check idempotency token BEFORE doing expensive validation
        // ---------------------------------------------------------------------

        IdempotencyContext idempotencyContext = idempotencyService.validateAndRecord(idemCtx);

//        boolean firstTime = idempotencyService.checkAndRecord(idemCtx, "DISPUTE_CREATE");

        if (idempotencyContext.isReplay()) {
            // Idempotent response (return the previously created dispute)
            return loadExistingDisputeByIdempotencyKey(idempotencyKey);
        }

        // ---------------------------------------------------------------------
        // 2) Domain validation (heavy operations allowed)
        // ---------------------------------------------------------------------
        if (Boolean.TRUE.equals(user.getDisabled())) {
            throw new IllegalStateException("User is disabled");
        }

        List<String> validEvidences = request.getEvidences().stream()
                .filter(imageValidationOrchestrator::validateEvidence)
                .toList();

        if (validEvidences.isEmpty()) {
            throw new IllegalStateException("No valid evidences provided");
        }


        //todo CRITICAL: Create dispute FIRST and persist to db as state is BOOTSTRAP_DISPUTE_CONTEXT
        Dispute dispute = createInitialDispute(request, user, validEvidences, idemCtx);

//        // 2) Get state machine for CREATION flow
//        StateMachine<DisputeState, DisputeTransitionEvent> sm =
//                disputeStateMachineService.createAndBootstrapStateMachine(
//                        user, idemCtx, validEvidences, request);

        // 3) Send bootstrap event
        StateTransitionResult result = disputeStateMachineService.sendEvent(
                dispute,
                DisputeTransitionEvent.EVENT_BOOTSTRAP_CONTEXT_USER,
                Map.of(
                        "actor", idemCtx.getActor(),
                        "mode", request.getMode(),
                        "initiatorType", request.getInitiator().getDisputantType()
                ),
                DisputeState.AWAITING_EVIDENCE_VERIFICATION,
                user,
                idempotencyContext,
                validEvidences
        );

        if (!result.isAccepted()) {
            throw new IllegalStateException("Dispute creation rejected by state machine");
        }

        // 5) Update dispute with final state from state machine
        dispute.setCurrentState(result.getNewState());
        return disputeService.updateDisputeWithTransition(dispute, dispute.getId(), idempotencyContext);



    }

    // 🎯 COMPLETED: Create initial dispute entity
    private Dispute createInitialDispute(CreateDisputeRequest request,
                                         DomainAwarePrincipal user,
                                         List<String> validEvidences,
                                         IdempotencyContext idemCtx) {
//        Dispute dispute = Dispute.builder()
//                .transactionId(request.getTransactionId())
//                .amount(request.getAmount())
//                .currency(request.getCurrency())
//                .reason(request.getReason())
//                .mode(request.getMode())
//                .status(DisputeState.INITIAL) // Initial state
//                .createdBy(idemCtx.getActor())
//                .evidences(validEvidences)
//                .idempotencyKey(idemCtx.getKey()) // Store for replay lookups
//                .createdAt(LocalDateTime.now())
//                .build();

        return null;
    }

    // 🎯 COMPLETED: Load existing dispute for replay
    private Dispute loadExistingDisputeByIdempotencyKey(String idempotencyKey) {
        return disputeRepository.findByidempotencyKey(idempotencyKey)
                .orElseThrow(() -> new IllegalStateException(
                        "Replay detected but no existing dispute found for key: " + idempotencyKey));
    }
}




// 4️⃣ Fire bootstrap event
//        boolean eventAccepted = stateMachine.sendEvent(
//                MessageBuilder.withPayload(DisputeTransitionEvent.EVENT_BOOTSTRAP_CONTEXT_USER)
//                        .setHeader("disputeId", "temporary-id") // or generate one
//                        .setHeader("idempotencyContext", idemCtx)
//                        .setHeader("actor", actor)
//                        .setHeader("mode", mode)
//                        .build()
//        );
