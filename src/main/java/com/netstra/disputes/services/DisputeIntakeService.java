package com.netstra.disputes.services;

import com.netra.commons.enums.DisputeState;
import com.netra.commons.enums.DisputeTransitionEvent;
import com.netra.commons.models.Dispute;
import com.netra.commons.requests.CreateDisputeRequest;
import com.netstra.disputes.idempotency.GenericIdempotencyService;
import com.netstra.disputes.idempotency.IdempotencyContext;
import com.netstra.disputes.idempotency.IdempotencyRequest;
import com.netstra.disputes.idempotency.IdempotencyResult;
import com.netstra.disputes.security.DomainAwarePrincipal;
import com.netstra.disputes.services.imaging.ImageValidationOrchestrator;
import com.netstra.disputes.services.imaging.ImageValidationResult;
import com.netstra.disputes.transitions.service.DisputeStateMachineService;
import com.netstra.disputes.transitions.service.StateTransitionResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DisputeIntakeService {

    private final ImageValidationOrchestrator imageValidationOrchestrator;
    private final GenericIdempotencyService idempotencyService;
    private final DisputeService disputeService; // ← ADD THIS
    private final DisputeStateMachineService disputeStateMachineService;

    public Dispute initiateUserDispute(CreateDisputeRequest request,
                                       DomainAwarePrincipal user,
                                       String idempotencyKey) {

        // ---------------------------------------------------------------------
        // 0) Construct idempotency context BEFORE doing any heavy work
        // ---------------------------------------------------------------------
        IdempotencyRequest idempotencyRequest = IdempotencyRequest.builder()
                .sourceType(IdempotencyRequest.IdempotencyRequestSourceType.HTTP)
                .operationName(IdempotencyRequest.IdempotencyOperation.CREATE_DISPUTE)
                .actorType(IdempotencyContext.ActorType.USER)
                .actor(user)
                .idempotencyKey(Optional.of(idempotencyKey))
                .build();

        // ---------------------------------------------------------------------
        // 2) Domain validation (heavy operations allowed)
        // ---------------------------------------------------------------------


        //todo CRITICAL: Create dispute FIRST and persist to db as state is BOOTSTRAP_DISPUTE_CONTEXT
        //Dispute dispute = createInitialDispute(request, user, validEvidences, idemCtx);

        IdempotencyResult<Dispute> resultIdem = idempotencyService.executeIdempotent(
                idempotencyRequest,
                request,
                () -> createInitialDispute(request, user),
                Dispute.class
        );

        return resultIdem.getResultOrThrow();

    }

    // 🎯 COMPLETED: Create initial dispute entity
    private Dispute createInitialDispute(CreateDisputeRequest request, DomainAwarePrincipal user) {

        if (Boolean.TRUE.equals(user.getDisabled())) {
            throw new IllegalStateException("User is disabled");
        }

        List<ImageValidationResult> validEvidences = request.getEvidences().stream()
                .map(imageValidationOrchestrator::validateEvidence)
                .toList();

        List<ImageValidationResult> acceptedEvidences = validEvidences.stream()
                .filter(imageValidationResult -> imageValidationResult.isValidAverageHash()).toList();

        if (acceptedEvidences.isEmpty()) {
            throw new IllegalStateException("No valid evidences provided");
        }

        Dispute dispute = disputeService.create(request);


        // 3) Send bootstrap event
        StateTransitionResult result = disputeStateMachineService.sendEvent(
                dispute,
                DisputeTransitionEvent.EVENT_BOOTSTRAP_CONTEXT_USER,
                Map.of(
                        "actorType", IdempotencyContext.ActorType.USER,
                        "actor", user,
                        "mode", request.getMode(),
                        "initiatorType", request.getInitiator().getDisputantType()
                ),
                DisputeState.AWAITING_EVIDENCE_VERIFICATION,
                user,
                validEvidences
        );

        //todo: weekly cleanup of failed, make sure at api level, failed state are reported as failed
        dispute.setCurrentState(result.isAccepted() ? result.getNewState() : DisputeState.BOOTSTRAP_FAILED);
        return disputeService.updateDisputeWithTransition(dispute, dispute.getId());

    }


    }