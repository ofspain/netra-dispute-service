package com.netstra.disputes.services.client.util;

import com.netra.commons.enums.DomainType;

public record EndpointConfigIdentity(DomainType type, Long ownerId, String domainCode) {
}
