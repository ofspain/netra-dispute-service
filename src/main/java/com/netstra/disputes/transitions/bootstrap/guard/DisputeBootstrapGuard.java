package com.netstra.disputes.transitions.bootstrap.guard;

import com.netra.commons.enums.DisputeState;
import com.netra.commons.enums.DisputeTransitionEvent;
import com.netstra.disputes.transitions.bootstrap.DisputeBootstrapComponent;
import org.springframework.statemachine.guard.Guard;

public interface DisputeBootstrapGuard
        extends Guard<DisputeState, DisputeTransitionEvent> , DisputeBootstrapComponent {
    DisputeState getTargetState();

}
