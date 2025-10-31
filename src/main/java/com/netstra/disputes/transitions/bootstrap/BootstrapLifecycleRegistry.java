package com.netstra.disputes.transitions.bootstrap;

import com.netra.commons.enums.DisputeMode;
import com.netra.commons.enums.DisputeTransitionEvent;
import com.netstra.disputes.transitions.bootstrap.action.DisputeBootstrapAction;
import com.netstra.disputes.transitions.bootstrap.annotation.DisputeModeBootStrapComponent;
import com.netstra.disputes.transitions.bootstrap.guard.DisputeBootstrapGuard;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Collection;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class BootstrapLifecycleRegistry {

    private final List<DisputeBootstrapGuard> allBootstrapGuards;
    private final List<DisputeBootstrapAction> allBootstrapActions;

    // mode → (trigger → BootstrapTransition)
    private final Map<DisputeMode, Map<DisputeTransitionEvent, BootstrapTransition>> registry =
            new EnumMap<>(DisputeMode.class);

    @PostConstruct
    public void init() {
        for (DisputeBootstrapGuard guard : allBootstrapGuards) {


            DisputeModeBootStrapComponent annotation = guard.getClass().getAnnotation(DisputeModeBootStrapComponent.class);
            if (annotation != null) {
                DisputeMode mode = annotation.value();

                System.out.printf("[BootstrapGuardAutoRegistrar] Registered %s under %s%n",
                        guard.getClass().getSimpleName(), mode);

                if(mode.equals(guard.mode())){
                    registry
                            .computeIfAbsent(guard.mode(), m -> new EnumMap<>(DisputeTransitionEvent.class))
                            .put(guard.trigger(), new BootstrapTransition(guard, null));
                }
            }

        }

        for (DisputeBootstrapAction action : allBootstrapActions) {
            DisputeModeBootStrapComponent annotation = action.getClass().getAnnotation(DisputeModeBootStrapComponent.class);

            if (annotation != null) {
                DisputeMode mode = annotation.value();

                System.out.printf("[BootstrapActionAutoRegistrar] Registered %s under %s%n",
                        action.getClass().getSimpleName(), mode);
                if(mode.equals(action.mode())){
                    registry
                            .computeIfAbsent(action.mode(), m -> new EnumMap<>(DisputeTransitionEvent.class))
                            .computeIfPresent(action.trigger(), (t, existing) -> {
                                // attach action to an existing guard
                                existing.setAction(action);
                                return existing;
                            });
                }
            }
        }

        log.info("✅ BootstrapLifecycleRegistry initialized: {} modes registered", registry.size());
    }

    public Collection<BootstrapTransition> getTransitions(DisputeMode mode) {
        return registry.getOrDefault(mode, Map.of()).values();
    }
}

