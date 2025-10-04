package com.netstra.disputes.services.client.util;

import com.netra.commons.enums.DomainType;

public record EndpointConfigIdentity(Long selfId, DomainType type, Long ownerId, String domainCode) {


    @Override
    public String toString() {
        return String.format(
                "ecid:%d:%s:%d:%s",
                selfId,
                type != null ? type.name().toLowerCase() : "none",
                ownerId,
                domainCode != null ? domainCode.toLowerCase() : "none"
        );
    }
}
