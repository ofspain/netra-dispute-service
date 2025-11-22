package com.netstra.disputes.transitions.bootstrap;

import com.netra.commons.enums.DisputeMode;
import com.netra.commons.enums.DisputeState;
import com.netra.commons.enums.DisputeTransitionEvent;
import org.springframework.statemachine.action.Action;
import org.springframework.statemachine.guard.Guard;

import java.util.List;

public interface DisputeBootstrapComponent {
    DisputeMode mode();
    DisputeTransitionEvent trigger();

    List<DisputeState> targetStates();

    Action<DisputeState, DisputeTransitionEvent> action();
    Guard<DisputeState, DisputeTransitionEvent> guard();
}
