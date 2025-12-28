package com.netstra.disputes.security;

import com.netra.commons.enums.DomainType;
import com.netra.commons.models.BaseUser;
import lombok.Getter;
import lombok.ToString;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.Assert;

import java.time.Instant;
import java.util.Map;

@Getter
@ToString
public class ClientPrincipal extends Jwt implements DomainAwarePrincipal {

    private final BaseUser client;
    private final String domainCode;

    public ClientPrincipal(String tokenValue,
                           Instant issuedAt,
                           Instant expiresAt,
                           Map<String, Object> headers,
                           Map<String, Object> claims,
                           BaseUser client,
                           String domainCode) {
        super(tokenValue, issuedAt, expiresAt, headers, claims);
        Assert.notNull(client, "Client cannot be null");
        this.client = client;
        this.domainCode = domainCode;
    }

    @Override
    public String getUserName() {
        return client.getIdentity().getUsername();
    }

    @Override
    public String getDomainCode() {
        return domainCode;
    }

    @Override
    public String getIdentityUUID() {

        return client.getIdentity().getIdentityUuid();
    }

    @Override
    public DomainType getDomainType(){
        return client.getIdentity().getDomainType();
    }

    @Override
    public Boolean getDisabled() {
        return client.getDisabled();
    }
}
