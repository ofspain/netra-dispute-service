package com.netstra.disputes.services.client.util;

import com.netra.commons.enums.DomainType;

public record EndpointConfigIdentity(Long selfId, DomainType type, Long ownerId, String domainCode) {
}
