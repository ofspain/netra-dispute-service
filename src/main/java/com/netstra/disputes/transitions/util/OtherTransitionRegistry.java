package com.netstra.disputes.transitions.util;


import com.netra.commons.enums.DisputeMode;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class OtherTransitionRegistry {
    private final DisputeLifecycleLoader lifecycleLoader;

    Map<DisputeMode, DisputeLifecycleConfig> mappedTransitionConfig = new ConcurrentHashMap<>();

    @PostConstruct
    public void initTransitionDefs() {
        for(DisputeMode mode : DisputeMode.values()){
            var lifecycle = lifecycleLoader.getLifecycle(mode);
           if(null == lifecycle){
               lifecycle = new DisputeLifecycleConfig();
               lifecycle.setMode(mode);
               lifecycle.setTransitions(new ArrayList<>());
           }
           mappedTransitionConfig.put(mode, lifecycle);

        }
    }

    public DisputeLifecycleConfig getOtherTransitionByDisputeMode(DisputeMode disputeMode){
        return mappedTransitionConfig.get(disputeMode);
    }
}
