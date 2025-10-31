package com.netstra.disputes.transitions.bootstrap;

import com.netstra.disputes.transitions.bootstrap.action.DisputeBootstrapAction;
import com.netstra.disputes.transitions.bootstrap.guard.DisputeBootstrapGuard;
import lombok.AllArgsConstructor;
import lombok.Data;


@Data
@AllArgsConstructor
public class BootstrapTransition{

    private DisputeBootstrapGuard guard;
    private DisputeBootstrapAction action;
}
