package com.netstra.disputes.services.client.util;

import com.netra.commons.models.endpoint.*;
import com.netstra.disputes.services.client.EndpointSecretManager;
import com.netstra.disputes.services.client.MtlsContextService;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.JdkSslContext;
import io.netty.handler.ssl.SslContext;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.tcp.SslProvider;
import reactor.netty.transport.ProxyProvider;

import javax.net.ssl.SSLContext;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebClientFactory {

    private final Validator validator;
    private final EndpointSecretManager secretManager;
    private final MtlsContextService mtlsContextService;

    // Cache MTLS WebClients per domain
    private final Map<String, CachedWebClientEntry> mtlsClientCache = new ConcurrentHashMap<>();
    private final int clientTtl = 2; // hours

    /**
     * Build a proxied WebClient.
     */
    public WebClient createWebClientWithProxy(NetworkConfig networkConfig) {
        ProxyConfig proxyConfig = networkConfig.getProxy();
        int timeout = networkConfig.getTimeoutMillis();

        HttpClient httpClient = HttpClient.create()
                .proxy(spec -> spec
                        .type(ProxyProvider.Proxy.HTTP)
                        .address(new InetSocketAddress(proxyConfig.getHost(), proxyConfig.getPort()))
                        .username(proxyConfig.getUsername())
                        .password(p -> secretManager.resolveSecret(proxyConfig.getPassword())))
                .responseTimeout(Duration.ofMillis(timeout));

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    /**
     * Direct WebClient without proxy.
     */
    public WebClient buildWebClientUnProxied(int timeoutMs) {
        log.info("Building direct WebClient (no proxy)");

        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofMillis(timeoutMs));

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    /**
     * Build MTLS-enabled WebClient with caching.
     */
    public WebClient createWebClientWithMtls(MtlsAuth mtlsConfig,
                                             EndpointConfigIdentity identity,
                                             NetworkConfig networkConfig) {
        String cacheId = Utility.calculateCachedRestClientId(identity);

        return mtlsClientCache.compute(cacheId, (id, existing) -> {
            if (existing != null && !existing.isExpired()) {
                return existing;
            }

            try {
                String domainCode = identity.domainCode();
                int timeout = networkConfig.getTimeoutMillis();

                // JDK SSLContext from your service
                SSLContext jdkSslContext = mtlsContextService.getOrCreateContext(domainCode, mtlsConfig);

// Wrap it as Netty's SslContext
                SslContext nettySslContext = new JdkSslContext(jdkSslContext, true, ClientAuth.NONE);

                HttpClient httpClient = HttpClient.create()
                        .secure(spec -> spec.sslContext(nettySslContext))
                        .responseTimeout(Duration.ofMillis(timeout));

                WebClient client = WebClient.builder()
                        .clientConnector(new ReactorClientHttpConnector(httpClient))
                        .build();

                return new CachedWebClientEntry(client, Instant.now().plus(Duration.ofHours(clientTtl)));

            } catch (Exception e) {
                throw new RuntimeException("Failed to build MTLS WebClient for domain=" + identity.domainCode(), e);
            }
        }).getClient();
    }

    public void evictCachedMtlsClient(String domainCode) {
        mtlsClientCache.remove(domainCode);
    }

    // --- cache entry wrapper ---
    private record CachedWebClientEntry(WebClient client, Instant expiresAt) {
        boolean isExpired() {
            return expiresAt != null && Instant.now().isAfter(expiresAt);
        }
        WebClient getClient() {
            return client;
        }
    }
}
