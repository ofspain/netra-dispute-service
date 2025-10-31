package com.netstra.disputes.transitions.actions;

import com.netra.commons.enums.DisputeMode;
import com.netra.commons.enums.DisputeTransitionEvent;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class DisputeActionRegistry {

    private final List<DisputeStateMachineAction> allActions;

    private final Map<DisputeMode, Map<DisputeTransitionEvent, DisputeStateMachineAction>> registry = new EnumMap<>(DisputeMode.class);

    @PostConstruct
    public void init() {
        for (DisputeStateMachineAction action : allActions) {
            registry
                    .computeIfAbsent(action.mode(), m -> new EnumMap<>(DisputeTransitionEvent.class))
                    .put(action.trigger(), action);
            log.info("Registered [{}] action for event [{}]", action.mode(), action.trigger());
        }
    }

    public DisputeStateMachineAction getAction(DisputeMode mode, DisputeTransitionEvent event) {
        return Optional.ofNullable(registry.get(mode))
                .map(m -> m.get(event))
                .orElseThrow(() ->
                        new IllegalArgumentException("No action found for mode=" + mode + " and event=" + event));
    }
}

