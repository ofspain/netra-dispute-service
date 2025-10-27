package com.netstra.disputes.transitions.guards.bootstrap;

import com.netra.commons.enums.DisputeState;
import com.netra.commons.enums.DisputeTransitionEvent;
import org.springframework.statemachine.guard.Guard;

public interface DisputeBootstrapGuard extends Guard<DisputeState, DisputeTransitionEvent> {
    DisputeState getTargetState();
    DisputeTransitionEvent getTrigger();

}
