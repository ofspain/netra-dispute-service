package com.netstra.disputes.transitions.bootstrap.action;

import com.netra.commons.enums.DisputeMode;
import com.netra.commons.enums.DisputeState;
import com.netra.commons.enums.DisputeTransitionEvent;
import com.netstra.disputes.transitions.bootstrap.annotation.DisputeModeBootStrapComponent;
import lombok.RequiredArgsConstructor;
import org.springframework.statemachine.StateContext;
import org.springframework.stereotype.Component;


@Component
@RequiredArgsConstructor
@DisputeModeBootStrapComponent(DisputeMode.CHARGEBACK)
public class CHBBootstrapSomeAction implements DisputeBootstrapAction {
    @Override
    public DisputeMode mode() {
        return DisputeMode.CHARGEBACK;
    }

    @Override
    public DisputeTransitionEvent trigger() {
        return DisputeTransitionEvent.BOOTSTRAP_CONTEXT_USER;
    }

    @Override
    public void perform(StateContext<DisputeState, DisputeTransitionEvent> context) {
            //this is the initial state check
            //confirm it has uploaded evidence(this and duplicate check should be done before here)


    }
}
