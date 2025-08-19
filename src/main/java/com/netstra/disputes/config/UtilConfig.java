package com.netstra.disputes.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;

public class UtilConfig {

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
       // mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
       // mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        return mapper;
    }
}
