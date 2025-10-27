// file: com.netstra.disputes.transitions.guards.bootstrap.BootstrapGuardAutoRegistrar.java
package com.netstra.disputes.transitions.guards.bootstrap;

import com.netra.commons.enums.DisputeMode;
import com.netstra.disputes.transitions.guards.bootstrap.annotation.DisputeModeGuard;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class BootstrapGuardAutoRegistrar {

    private final List<DisputeBootstrapGuard> allGuards;
    private final BootstrapGuardRegistry registry;

    @PostConstruct
    public void init() {
        for (DisputeBootstrapGuard guard : allGuards) {
            DisputeModeGuard annotation = guard.getClass().getAnnotation(DisputeModeGuard.class);
            if (annotation != null) {
                DisputeMode mode = annotation.value();
                registry.register(mode, guard);
                System.out.printf("[BootstrapGuardAutoRegistrar] Registered %s under %s%n",
                        guard.getClass().getSimpleName(), mode);
            }
        }
    }
}
