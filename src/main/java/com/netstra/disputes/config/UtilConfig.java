package com.netstra.disputes.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;

import java.util.Map;

public class UtilConfig {

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
       // mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
       // mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        return mapper;
    }

    public static <T> T convertObjectToClass(TypeReference<T> clazz, String jsonString) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            return mapper.readValue(jsonString, clazz);
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert JSON to " + clazz.getClass().getName(), e);
        }
    }
}
