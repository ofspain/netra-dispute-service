package com.netstra.disputes.security;

import com.interswitch.backbone.arbitertransactionstoremanager.shared.model.passport.User;
import lombok.Getter;
import lombok.ToString;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.Assert;

import java.time.Instant;
import java.util.Map;

@ToString
@Getter
public class UserPrincipal extends Jwt implements DomainAwarePrincipal{

    private final User user;

    public UserPrincipal(String tokenValue, Instant issuedAt, Instant expiresAt,
                         Map<String, Object> headers, Map<String, Object> claims, User user) {
        super(tokenValue, issuedAt, expiresAt, headers, claims);
        Assert.notNull(user, "user cannot be null");
        this.user = user;
    }

    @Override
    public String getUserName() {
        return user.getUsername();
    }

//    @Override
    public String getDomainCode() {
      return user.getDomainCode();
    }

    @Override
    public String getEffectiveDomainCode() {
        return user.getDomainCode();
    }

}