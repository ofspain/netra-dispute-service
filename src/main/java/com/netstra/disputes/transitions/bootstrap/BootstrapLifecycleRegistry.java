package com.netstra.disputes.transitions.bootstrap;

import com.netra.commons.enums.DisputeMode;
import com.netstra.disputes.transitions.bootstrap.annotation.DisputeModeBootStrapComponent;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class BootstrapLifecycleRegistry {

    private final List<DisputeBootstrapComponent> bootstrapComponents;

    // mode → (trigger → BootstrapTransition)
    private final Map<DisputeMode, BootstrapTransition> registry =
            new EnumMap<>(DisputeMode.class);

    @PostConstruct
    public void init() {
        for (DisputeBootstrapComponent bootstrapComponent : bootstrapComponents) {


            DisputeModeBootStrapComponent annotation = bootstrapComponent.getClass().getAnnotation(DisputeModeBootStrapComponent.class);
            if (annotation != null) {
                DisputeMode mode = annotation.value();

                System.out.printf("[BootstrapComponentAutoRegistrar] Registered %s under %s%n",
                        bootstrapComponent.getClass().getSimpleName(), mode);

                if(mode.equals(bootstrapComponent.mode())){
                    registry.putIfAbsent(bootstrapComponent.mode(), new BootstrapTransition(
                            bootstrapComponent.action(),
                            bootstrapComponent.guard(),
                            bootstrapComponent.trigger(),
                            bootstrapComponent.targetStates()
                    ));
                }
            }

        }

        log.info("✅ BootstrapLifecycleRegistry initialized: {} modes registered", registry.size());
    }

    public BootstrapTransition getTransitions(DisputeMode mode) {
        BootstrapTransition transition = registry.get(mode);
//        if (transition == null) {
//            throw new IllegalArgumentException("No transitions registered for mode: " + mode);
//        }
        return transition;
    }
}

