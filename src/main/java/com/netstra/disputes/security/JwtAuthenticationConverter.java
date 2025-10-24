package com.netstra.disputes.security;

import com.interswitch.backbone.arbitertransactionstoremanager.shared.exception.AuthorizationParameterNotFoundException;
import com.interswitch.backbone.arbitertransactionstoremanager.shared.model.passport.Permission;
import com.interswitch.backbone.arbitertransactionstoremanager.shared.model.passport.User;
import com.interswitch.backbone.arbitertransactionstoremanager.shared.service.ClientService;
import com.interswitch.backbone.arbitertransactionstoremanager.shared.service.UserService;
import com.interswitch.backbone.arbitertransactionstoremanager.shared.util.RequestUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

import java.util.*;
import java.util.stream.Collectors;


@Slf4j
public class JwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private static final String AUTHORIZATION_DOMAIN_HEADER = "X-Interswitch-Authorization-Domain";

    private final Converter<Jwt, Collection<GrantedAuthority>> jwtGrantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();
    private final ClientService clientService;
    private final UserService userService;

    public JwtAuthenticationConverter(ClientService clientService, UserService userService) {
        this.clientService = clientService;
        this.userService = userService;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Collection<GrantedAuthority> authorities = Optional
                .ofNullable(jwtGrantedAuthoritiesConverter.convert(jwt))
                .orElse(Collections.emptyList());

//        log.info(" jwt is {} ", jwt);
//        log.info(" Headers is {} ", jwt.getHeaders());
//        log.info(" Token is {} ", jwt.getTokenValue());
//        log.info(" Claims {} ", jwt.getClaims());
//        log.info(" jwt Subject -> {} ", jwt.getSubject());

        String domainCode = RequestUtils.getHeader(AUTHORIZATION_DOMAIN_HEADER);

//        log.info(" domainCode is {} ", domainCode);

        if (StringUtils.isBlank(domainCode)) {
            throw new AuthorizationParameterNotFoundException(AUTHORIZATION_DOMAIN_HEADER, "header");
        }

        String clientId = jwt.getClaim("client_id");
        String userName = jwt.getClaim("user_name");
        List<String> scopes = jwt.getClaimAsStringList("scope");

        boolean isClientToken =
                (clientId != null && userName == null)
                        || (scopes != null && scopes.contains("clients"));

        if (isClientToken) {
            return handleClientAuthentication(jwt, domainCode, authorities);
        } else {
            return handleUserAuthentication(jwt, domainCode, authorities);
        }
    }

    private JwtAuthenticationToken handleClientAuthentication(Jwt jwt, String domainCode, Collection<GrantedAuthority> authorities) {
        String clientId = jwt.getClaim("client_id");
        String clientDomain = jwt.getClaim("client_authorization_domain");

        User client = clientService.getClient(jwt.getTokenValue(), domainCode, clientId, clientDomain);

        authorities.addAll(getAuthorities(clientService.getClientPermissions()));
        // Add additional authorities if needed

        return new JwtAuthenticationToken(
//                jwt,
                new ClientPrincipal(jwt.getTokenValue(),
                        jwt.getIssuedAt(), jwt.getExpiresAt(), jwt.getHeaders(), jwt.getClaims(), client, domainCode),
                authorities,
                client.getUsername()
        );
    }

    private JwtAuthenticationToken handleUserAuthentication(Jwt jwt, String domainCode, Collection<GrantedAuthority> authorities) {
        String username = jwt.getClaimAsString("user_name");
        String accessToken = jwt.getTokenValue();

        User user = userService.getUser(jwt.getTokenValue(), domainCode, accessToken);
        List<Permission> permissions = userService.getUserPermissions(
                jwt.getTokenValue(),
                username,
                domainCode,
                accessToken
        );

        authorities.addAll(getAuthorities(permissions));

        return new JwtAuthenticationToken(
//                jwt,
                new UserPrincipal(jwt.getTokenValue(),
                        jwt.getIssuedAt(), jwt.getExpiresAt(), jwt.getHeaders(), jwt.getClaims(), user),
                authorities,
                user.getUsername()
        );
    }

    private Set<GrantedAuthority> getAuthorities(List<Permission> permissions) {
        if (CollectionUtils.isEmpty(permissions)) {
            return Set.of(new SimpleGrantedAuthority("NO_USER"));
        } else {
            return permissions
                    .stream()
                    .map(p -> new SimpleGrantedAuthority(String.format("ROLE_%s", p.getName())))
                    .collect(Collectors.toSet());
        }
    }
}
