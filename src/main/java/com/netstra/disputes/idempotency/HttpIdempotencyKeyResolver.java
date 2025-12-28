package com.netstra.disputes.idempotency;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.security.Principal;
import java.util.Optional;

@Component
public class HttpIdempotencyKeyResolver implements IdempotencyKeyResolver {

    @Override
    public Optional<String> resolveKey(IdempotencyRequest request) {
        if (!(request.getSource() instanceof HttpServletRequest)) {
            return Optional.empty();
        }

        HttpServletRequest httpRequest = (HttpServletRequest) request.getSource();

        // Check headers first
        String key = httpRequest.getHeader(IdempotencyConstants.HTTP_IDEM_HEADER_KEY);
        if (StringUtils.hasText(key)) {
            return Optional.of(key);
        }

        // Check parameters
        key = httpRequest.getParameter(IdempotencyConstants.HTTP_IDEM_QUERY_PARAM_KEY);
        if (StringUtils.hasText(key)) {
            return Optional.of(key);
        }

        return Optional.empty();
    }

    @Override
    public IdempotencyContext.ActorType determineActorType(IdempotencyRequest request) {
        HttpServletRequest httpRequest = (HttpServletRequest) request.getSource();
        Principal principal = httpRequest.getUserPrincipal();
        String userId = (principal != null) ? principal.getName() : null;
        return userId != null ? IdempotencyContext.ActorType.USER : IdempotencyContext.ActorType.SYSTEM;
    }
}
