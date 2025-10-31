package com.netstra.disputes.transitions.bootstrap;

import com.netra.commons.enums.DisputeMode;
import com.netra.commons.enums.DisputeTransitionEvent;

public interface DisputeBootstrapComponent {
    DisputeMode mode();
    DisputeTransitionEvent trigger();
}
