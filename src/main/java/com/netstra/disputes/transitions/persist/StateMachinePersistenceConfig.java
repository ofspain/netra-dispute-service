package com.netstra.disputes.transitions.persist;

import com.netra.commons.enums.DisputeState;
import com.netra.commons.enums.DisputeTransitionEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.statemachine.StateMachineContext;
import org.springframework.statemachine.persist.DefaultStateMachinePersister;
import org.springframework.statemachine.persist.StateMachineRuntimePersister;
import org.springframework.statemachine.support.StateMachineInterceptor;

@Configuration
@Slf4j
public class StateMachinePersistenceConfig {
 //   private JpaPersistingStateMachineInterceptor jj;

    // Option 2: Runtime Persister (For automatic persistence)
    @Bean
    public StateMachineRuntimePersister<DisputeState, DisputeTransitionEvent, String> disputeRuntimePersister(
            DisputeStateMachinePersistService persistService, DisputeStateMachinePersistenceInterceptor interceptor) {

        return new StateMachineRuntimePersister<DisputeState, DisputeTransitionEvent, String>() {

            @Override
            public StateMachineInterceptor<DisputeState, DisputeTransitionEvent> getInterceptor() {
                log.info("Persister interceptor initialized: {}", interceptor);
                return interceptor;
            }

            @Override
            public StateMachineContext<DisputeState, DisputeTransitionEvent> read(String contextObj) throws Exception {
                log.info("reading sm context {}", contextObj);
                return persistService.read(contextObj);
            }

            @Override
            public void write(StateMachineContext<DisputeState, DisputeTransitionEvent> context, String contextObj) throws Exception {
                log.info("writing sm context {}, {}", context, contextObj);
                persistService.write(context, contextObj);
            }
        };
    }
}

