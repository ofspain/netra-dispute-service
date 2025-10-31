package com.netstra.disputes.transitions.util;

import com.netra.commons.enums.DisputeMode;
import com.netra.commons.enums.DisputeState;
import lombok.Data;

import java.util.List;

@Data
public class DisputeLifecycleConfig {
    private DisputeMode mode;
    private List<DisputeTransitionConfig> transitions;
}
