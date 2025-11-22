package com.netstra.disputes.transitions.bootstrap;

import com.netra.commons.enums.DisputeState;
import com.netra.commons.enums.DisputeTransitionEvent;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.statemachine.action.Action;
import org.springframework.statemachine.guard.Guard;

import java.util.List;


@Data
@AllArgsConstructor
public class BootstrapTransition{

    private Action<DisputeState, DisputeTransitionEvent> action;
    private Guard<DisputeState, DisputeTransitionEvent> guard;

    private DisputeTransitionEvent triggerEvent;
    private List<DisputeState> targetStates;

}
