package com.netstra.disputes.transitions.util;

import com.netra.commons.enums.DisputeState;
import com.netra.commons.enums.DisputeTransitionEvent;
import lombok.Data;

@Data
public class DisputeTransitionConfig {
    private DisputeState source;
    private DisputeState target;
    private DisputeTransitionEvent event;
    private String guardBeanName;  // optional
    private String actionBeanName; // optional
}
