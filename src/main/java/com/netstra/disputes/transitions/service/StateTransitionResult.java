package com.netstra.disputes.transitions.service;

import com.netra.commons.enums.DisputeState;
import com.netra.commons.enums.DisputeTransitionEvent;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StateTransitionResult {

    private boolean accepted;
    private boolean stateChanged;
    private DisputeState previousState;
    private DisputeState newState;
    private DisputeTransitionEvent event;
    private Object dispute;
}

