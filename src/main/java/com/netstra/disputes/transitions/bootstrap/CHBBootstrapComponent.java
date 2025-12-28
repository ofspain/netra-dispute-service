package com.netstra.disputes.transitions.bootstrap;

import com.netra.commons.enums.DisputeMode;
import com.netra.commons.enums.DisputeState;
import com.netra.commons.enums.DisputeTransitionEvent;
import com.netra.commons.models.BaseUser;
import com.netstra.disputes.transitions.bootstrap.DisputeBootstrapComponent;
import com.netstra.disputes.transitions.bootstrap.action.CHBBootstrapAction;
import com.netstra.disputes.transitions.bootstrap.annotation.DisputeModeBootStrapComponent;
import com.netstra.disputes.transitions.bootstrap.guard.CHBBootstrapGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.action.Action;
import org.springframework.statemachine.guard.Guard;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.netra.commons.enums.DisputeState.*;

@Component
@RequiredArgsConstructor
@DisputeModeBootStrapComponent(DisputeMode.CHARGEBACK)
public class CHBBootstrapComponent implements DisputeBootstrapComponent {

    private final CHBBootstrapAction action;
    private final CHBBootstrapGuard guard;
    @Override
    public DisputeMode mode() {
        return DisputeMode.CHARGEBACK;
    }

    @Override
    public DisputeTransitionEvent trigger() {
        return DisputeTransitionEvent.EVENT_BOOTSTRAP_CONTEXT_USER;
    }

    @Override
    public List<DisputeState> targetStates() {
        return List.of(AWAITING_EVIDENCE_VERIFICATION,REQUEST_RESPONDER_VERIFICATION );
    }

    @Override
    public Action<DisputeState, DisputeTransitionEvent> action() {
        return action;
    }

    @Override
    public Guard<DisputeState, DisputeTransitionEvent> guard() {
        return guard;
    }


}
