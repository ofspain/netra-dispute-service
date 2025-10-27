package com.netstra.disputes.transitions.guards.bootstrap;

import com.netra.commons.enums.DisputeMode;
import lombok.Getter;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class BootstrapGuardRegistry {

    // Map of DisputeMode → list of guards
    @Getter
    private final Map<DisputeMode, List<DisputeBootstrapGuard>> registry = new EnumMap<>(DisputeMode.class);

    public void register(DisputeMode mode, DisputeBootstrapGuard guard) {
        registry.computeIfAbsent(mode, m -> new ArrayList<>()).add(guard);
    }

    public List<DisputeBootstrapGuard> getGuardsFor(DisputeMode mode) {
        return registry.getOrDefault(mode, Collections.emptyList());
    }
}
