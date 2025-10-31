package com.netstra.disputes.transitions.util;

import com.netra.commons.enums.DisputeMode;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ChargebackTransitionRegistry {

    private final DisputeLifecycleLoader lifecycleLoader;
    private List<DisputeTransitionConfig> transitionConfigs;

    @PostConstruct
    public void initTransitionDefs() {
        var lifecycle = lifecycleLoader.getLifecycle(DisputeMode.CHARGEBACK);
        if (lifecycle == null) {
            throw new IllegalStateException("No lifecycle configuration found for CHARGEBACK");
        }

        this.transitionConfigs = lifecycle.getTransitions();
    }

    public List<DisputeTransitionConfig> getTransitions() {
        return transitionConfigs != null ? transitionConfigs : List.of();
    }
}


