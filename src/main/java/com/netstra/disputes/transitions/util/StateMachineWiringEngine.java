package com.netstra.disputes.transitions.util;

import com.netra.commons.enums.DisputeState;
import com.netra.commons.enums.DisputeTransitionEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.statemachine.action.Action;
import org.springframework.statemachine.config.builders.StateMachineTransitionConfigurer;
import org.springframework.statemachine.guard.Guard;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class StateMachineWiringEngine {

    private final ApplicationContext context;

    public void wireTransitions(StateMachineTransitionConfigurer<DisputeState, DisputeTransitionEvent> transitions,
                                List<DisputeTransitionConfig> transitionConfigs) throws Exception {

        for (DisputeTransitionConfig config : transitionConfigs) {
            var builder = transitions
                    .withExternal()
                    .source(config.getSource())
                    .target(config.getTarget())
                    .event(config.getEvent());


            if (config.getGuardBeanName() != null) {
                builder.guard(context.getBean(config.getGuardBeanName(), Guard.class));
            }

            if (config.getActionBeanName() != null) {
                builder.action(context.getBean(config.getActionBeanName(), Action.class));
            }

            builder.and();
        }
    }
}

