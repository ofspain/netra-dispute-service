package com.netstra.disputes.security;

import com.netra.commons.models.BaseUser;
import lombok.Getter;
import lombok.ToString;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.Assert;

import java.time.Instant;
import java.util.Map;

@ToString
@Getter
public class UserPrincipal extends Jwt implements DomainAwarePrincipal{

    private final BaseUser user;

    public UserPrincipal(String tokenValue, Instant issuedAt, Instant expiresAt,
                         Map<String, Object> headers, Map<String, Object> claims, BaseUser user) {
        super(tokenValue, issuedAt, expiresAt, headers, claims);
        Assert.notNull(user, "user cannot be null");
        this.user = user;
    }

    @Override
    public String getUserName() {
        return user.getIdentity().getUsername();
    }

//    @Override
    public String getDomainCode() {
      return user.getIdentity().getDomainCode();
    }

    @Override
    public Long getIDonHostDB() {
        return user.getId();
    }

    @Override
    public Boolean getDisabled() {
        return user.getDisabled();
    }


}