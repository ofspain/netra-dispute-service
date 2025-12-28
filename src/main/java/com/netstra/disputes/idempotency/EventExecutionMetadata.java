package com.netstra.disputes.idempotency;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;

public class EventExecutionMetadata<T> {

    private String eventName;
    private String jobId;
    private LocalDateTime scheduledTime;
    private LocalDateTime actualStartTime;

    private Map<String, Object> parameters;


    public EventExecutionMetadata(Message<T> message){
        MessageHeaders headers = message.getHeaders();
        Set<String> keys = headers.keySet();
        for(String key : keys){
            parameters.put(key, headers.get(key));
        }
    }
}
