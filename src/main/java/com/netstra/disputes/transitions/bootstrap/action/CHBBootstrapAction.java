package com.netstra.disputes.transitions.bootstrap.action;

import com.netra.commons.enums.DisputeMode;
import com.netra.commons.enums.DisputeState;
import com.netra.commons.enums.DisputeTransitionEvent;
import com.netra.commons.models.BaseUser;
import com.netra.commons.models.Dispute;
import com.netra.commons.models.Evidence;
import com.netra.commons.requests.CreateDisputeRequest;
import com.netstra.disputes.services.DisputeJourneyTraceService;
import com.netstra.disputes.services.EvidenceService;
import com.netstra.disputes.transitions.bootstrap.annotation.DisputeModeBootStrapComponent;
import lombok.RequiredArgsConstructor;
import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.action.Action;
import org.springframework.stereotype.Component;

import java.util.List;


@Component
@RequiredArgsConstructor
@DisputeModeBootStrapComponent(DisputeMode.CHARGEBACK)
public class CHBBootstrapAction implements Action<DisputeState, DisputeTransitionEvent> {

    private final EvidenceService evidenceService;
    //todo: beware, this introduces a circular dependency
  //  private final DisputeService disputeService;
    private final DisputeJourneyTraceService disputeJourneyTraceService;

    @Override
    public void execute(StateContext<DisputeState, DisputeTransitionEvent> context) {


        //sm.getExtendedState().getVariables().put("dispute", dispute); // ← ADD THIS
        //        sm.getExtendedState().getVariables().put("validEvidences", validEvidences);
        //        sm.getExtendedState().getVariables().put("user", user);
        //        sm.getExtendedState().getVariables().put("idempotencyContext", idemCtx);
        //        sm.getExtendedState().getVariables().put("createDisputeRequest", request);

        //todo: use the two below to orchestrate the next line of action
        BaseUser user = context.getExtendedState().get("user", BaseUser.class);
        DisputeState intendedState = context.getExtendedState().get("intendedState", DisputeState.class);

        Dispute dispute = context.getExtendedState().get("dispute", Dispute.class);
        CreateDisputeRequest request = context.getExtendedState().get("request", CreateDisputeRequest.class);

        List<String> validateEvidences = context.getExtendedState().get("validEvidences", List.class);



        List<Evidence> evidences = evidenceService.save(validateEvidences, dispute.getId());

        context.getExtendedState().getVariables().put("evidences", evidences);

    }

}
