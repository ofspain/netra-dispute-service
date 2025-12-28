package com.netstra.disputes.idempotency;

import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;

import static com.netstra.disputes.idempotency.IdempotencyConstants.MSG_IDEM_HEADER_KEY;

@Component
public class MessageIdempotencyKeyResolver implements IdempotencyKeyResolver {

    @Override
    public Optional<String> resolveKey(IdempotencyRequest request) {
        if (!(request.getSource() instanceof Message)) {
            return Optional.empty();
        }

        Message message = (Message) request.getSource();
        LocalDateTime receivedTimestamp = message.getHeaders().get("kafka_receivedTimestamp", LocalDateTime.class);
        if(null == receivedTimestamp){
            receivedTimestamp = LocalDateTime.now();
        }

        String scheduledTime = receivedTimestamp.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);


        // Check message headers/properties
        String key = (String) message.getHeaders().get(MSG_IDEM_HEADER_KEY);
        if (StringUtils.hasText(key)) {
            return Optional.of("MSG_"+scheduledTime +"_" + key);
        }

        // Use message ID as fallback
        return Optional.of("MSGID_"+scheduledTime +"_" + UUID.randomUUID());
    }

    @Override
    public IdempotencyContext.ActorType determineActorType(IdempotencyRequest request) {
        return IdempotencyContext.ActorType.EVENT_HANDLER;
    }
}
