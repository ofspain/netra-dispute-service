package com.netstra.disputes.transitions.guards.bootstrap;

import com.netra.commons.enums.DisputeMode;
import com.netra.commons.enums.DisputeState;
import com.netra.commons.enums.DisputeTransitionEvent;
import com.netstra.disputes.services.DisputeService;
import com.netstra.disputes.transitions.guards.bootstrap.annotation.DisputeModeGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.statemachine.StateContext;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@DisputeModeGuard(DisputeMode.GOODFAITH)
public class GFBootstrapIssAwaiting implements DisputeBootstrapGuard {

    private final DisputeService disputeService;

    @Override
    public DisputeState getTargetState() {
        return DisputeState.AWAITING_ISSUER_VERIFICATION;
    }

    @Override
    public DisputeTransitionEvent getTrigger() {
        return DisputeTransitionEvent.BOOTSTRAP_CONTEXT;
    }

    @Override
    public boolean evaluate(StateContext<DisputeState, DisputeTransitionEvent> context) {

        //get who logs in
        //get who is the disputant
        //when merchant(or any other subdomain) disputant, ensure transactions is associated with a merchant(or the sub institution)
        //do other validation as may be necessary on a per disputant type and who logs in
        return false;
    }
}
