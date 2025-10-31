package com.netstra.disputes.transitions.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netra.commons.enums.DisputeMode;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternUtils;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class DisputeLifecycleLoader {

    private final ObjectMapper jsonMapper = new ObjectMapper();

    private final Map<DisputeMode, DisputeLifecycleConfig> lifecycles = new ConcurrentHashMap<>();

    @PostConstruct
    public void loadLifecycles() {
        try {
            // Load all .json files in classpath: lifecycles/*.json
            Resource[] resources = ResourcePatternUtils
                    .getResourcePatternResolver(null)
                    .getResources("classpath*:transitions/*.json");

            for (Resource resource : resources) {
                DisputeLifecycleConfig config = jsonMapper.readValue(resource.getInputStream(), DisputeLifecycleConfig.class);
                lifecycles.put(config.getMode(), config);
                log.info("✅ Loaded dispute lifecycle for mode: {}", config.getMode());
            }

        } catch (IOException e) {
            log.error("❌ Failed to load dispute lifecycles", e);
        }
    }

    public DisputeLifecycleConfig getLifecycle(DisputeMode mode) {
        return lifecycles.get(mode);
    }
}
