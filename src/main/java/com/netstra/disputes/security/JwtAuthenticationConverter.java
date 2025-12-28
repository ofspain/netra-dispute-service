package com.netstra.disputes.security;
import com.netra.commons.enums.DomainType;
import com.netra.commons.models.BaseUser;
import com.netra.commons.models.Identity;
import com.netra.commons.util.BasicUtil;
import com.netstra.disputes.services.UserService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.util.CollectionUtils;

import java.security.Permission;
import java.util.*;
import java.util.stream.Collectors;


/**
 * Clean, refactored Jwt -> Authentication converter.
 *
 * Responsibilities:
 *  - Determine if incoming JWT is a user or client token
 *  - Validate required input headers (authorization domain)
 *  - Fetch user/client details & permissions from services
 *  - Map permissions -> GrantedAuthority
 *  - Produce JwtAuthenticationToken with domain-aware principal
 */
@Slf4j
public class JwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private static final String AUTHORIZATION_DOMAIN_HEADER = "X-Interswitch-Authorization-Domain";

    private final Converter<Jwt, Collection<GrantedAuthority>> jwtGrantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();
    private final BaseUserExtractor baseUserExtractor;

    private final String AUTH_SERVER_NAME = "authrex-service";

    public JwtAuthenticationConverter(BaseUserExtractor baseUserExtractor) {

        this.baseUserExtractor = baseUserExtractor;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Collection<GrantedAuthority> authorities = Optional
                .ofNullable(jwtGrantedAuthoritiesConverter.convert(jwt))
                .orElse(new ArrayList<>());

        String clientId = jwt.getClaimAsString("client_id");
        String userName = jwt.getClaimAsString("user_name");
        String scope = jwt.getClaimAsString("scope");
        String grantType = jwt.getClaimAsString("grant_type");
        String domainCode = jwt.getClaimAsString("domain_code");
        DomainType domainType = BasicUtil.safeEnum(DomainType.class,jwt.getClaimAsString("domain_code"));


        Set<String> scopes = scope == null
                ? Set.of()
                : Arrays.stream(scope.trim().split("\\s+"))
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());



        //todo: taps other determinstic fields like is_delegated, is_service etc to determine which way,
        // let use pass through for now....we pass client straight to client as if its user calling

        boolean isClientToken = isClientToken(domainType, domainCode, clientId, grantType);

        if (isClientToken) {
            log.debug("Token classified as CLIENT token (client_id={}, domainHeader={})", clientId, domainCode);
            return handleClientAuthentication(jwt, domainCode, authorities);
        } else {
            log.debug("Token classified as USER token (user_name={}, domainHeader={})", userName, domainCode);
            return handleUserAuthentication(jwt, domainCode, authorities);
        }
    }

    // -------------------------
    // High-level handlers
    // -------------------------

    private JwtAuthenticationToken handleClientAuthentication(Jwt jwt, String domainCode, Collection<GrantedAuthority> authorities) {
        String clientId = jwt.getClaimAsString("client_id");
        String accessToken = jwt.getTokenValue();
        List<String> permissions = jwt.getClaimAsStringList("client_permission");

        BaseUser client = baseUserExtractor.extractUser(jwt, domainCode);


        // Map permissions -> authorities
        authorities.addAll(mapPermissionsToAuthorities(permissions, "CLIENT"));

        // Validate token claims against client record and domain
        validateClientClaims(jwt, client, domainCode);

        // Build principal
        ClientPrincipal principal = new ClientPrincipal(
                accessToken,
                jwt.getIssuedAt(),
                jwt.getExpiresAt(),
                jwt.getHeaders(),
                jwt.getClaims(),
                client,
                domainCode
        );

        log.info("Authenticated CLIENT [{}] for domain [{}]", clientId, domainCode);
        return new JwtAuthenticationToken(principal, authorities, principal.getUserName());
    }

    private JwtAuthenticationToken handleUserAuthentication(
            Jwt jwt,
            String domainCode,
            Collection<GrantedAuthority> authorities
    ) {
        // ---- Build BaseUser directly from the JWT (no remote call needed) ----
        BaseUser user = baseUserExtractor.extractUser(jwt, domainCode);

        // ---- Extract roles from JWT ----
        List<String> roles = jwt.getClaimAsStringList("roles");
        if (roles != null) {
            roles.forEach(r ->
                    authorities.add(new SimpleGrantedAuthority("ROLE_" + r.toUpperCase()))
            );
        }

        // ---- Build principal ----
        UserPrincipal principal = new UserPrincipal(
                jwt.getTokenValue(),
                jwt.getIssuedAt(),
                jwt.getExpiresAt(),
                jwt.getHeaders(),
                jwt.getClaims(),
                user
        );

        return new JwtAuthenticationToken(
                principal,
                authorities,
                user.getIdentity().getUsername()
        );
    }




    //(domainType, domainCode, clientId, grantType)
    private boolean isClientToken(DomainType domainType, String domainCode, String clientId, String grantType) {

        boolean clientType = DomainType.SYSTEM.equals(domainType);
        boolean clientCode = Identity.systemIdentity().getDomainCode().equalsIgnoreCase(domainCode);
        boolean validClientId = BasicUtil.isValidUuid(clientId);
        boolean grantedType = "CLIENT_CREDENTIALS".equalsIgnoreCase(grantType);
        //for now
        return clientType && clientCode && validClientId && grantedType;
    }

    private Set<GrantedAuthority> mapPermissionsToAuthorities(List<String> permissions, String fallbackPrefix) {
        if (CollectionUtils.isEmpty(permissions)) {
            return Set.of(new SimpleGrantedAuthority("NO_" + fallbackPrefix));
        }
        return permissions.stream()
                .filter(Objects::nonNull)
                .map(p -> new SimpleGrantedAuthority(String.format("ROLE_%s", p)))
                .collect(Collectors.toSet());
    }

    // -------------------------
    // Claim validation (user/client)
    // -------------------------

    private void validateUserClaims(Jwt jwt, BaseUser user, String domainCode) {
        // issuer validation (optional - enforce if you rely on 'iss')
        String issuer = jwt.getClaimAsString("iss");
        if (StringUtils.isNotBlank(issuer) && !isExpectedIssuer(issuer)) {
            log.warn("Unexpected issuer in token: {}", issuer);
            throw new IllegalArgumentException("Invalid token issuer");
        }

        // basic roles claim presence
        List<String> roles = jwt.getClaimAsStringList("roles");
        if (roles == null || roles.isEmpty()) {
            log.debug("No roles found in JWT for user: {}", jwt.getClaimAsString("user_name"));
            // optionally fail or allow depending on your policy
        }

        // domain consistency check: prefer header, but ensure user identity domain matches header
        String tokenDomain = jwt.getClaimAsString("domain");
        if (StringUtils.isNotBlank(tokenDomain) && !tokenDomain.equalsIgnoreCase(domainCode)) {
            log.warn("Domain mismatch: header={}, token={}", domainCode, tokenDomain);
            throw new IllegalArgumentException("Domain mismatch between header and token");
        }

        // Validate that the user object domain matches header
        if (user != null && user.getIdentity() != null && user.getIdentity().getDomainCode() != null) {
            if (!user.getIdentity().getDomainCode().equalsIgnoreCase(domainCode)) {
                log.warn("User domain from service differs from header. header={}, user={}", domainCode, user.getIdentity().getDomainCode());
                throw new IllegalArgumentException("User domain mismatch");
            }
        }
    }

    private void validateClientClaims(Jwt jwt, BaseUser client, String domainCode) {
        // issuer validation (optional)
        String issuer = jwt.getClaimAsString("iss");
        if (StringUtils.isNotBlank(issuer) && !isExpectedIssuer(issuer)) {
            log.warn("Unexpected issuer in client token: {}", issuer);
            throw new IllegalArgumentException("Invalid token issuer");
        }

        // client authorization domain should match header domain
        String tokenClientDomain = jwt.getClaimAsString("client_code");
        if (StringUtils.isNotBlank(tokenClientDomain) && !tokenClientDomain.equalsIgnoreCase(domainCode)) {
            log.warn("Client domain mismatch: header={}, token={}", domainCode, tokenClientDomain);
            throw new IllegalArgumentException("Client domain mismatch");
        }

        // client id must match the client record identity (defensive)
        String tokenClientId = jwt.getClaimAsString("client_id");
        if (client != null && client.getIdentity() != null) {
            if (!client.getIdentity().getIdentityUuid().equalsIgnoreCase(tokenClientId)) {
                log.warn("client_id mismatch between JWT and client service: jwt={} vs client={}", tokenClientId, client.getIdentity().getUsername());
                throw new IllegalArgumentException("client_id mismatch");
            }
        }
    }

    private boolean isExpectedIssuer(String issuer) {
        return AUTH_SERVER_NAME.equalsIgnoreCase(issuer);
    }
}