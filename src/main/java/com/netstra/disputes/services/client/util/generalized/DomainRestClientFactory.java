package com.netstra.disputes.services.client.util.generalized;

import com.netra.commons.models.endpoint.AuthConfig;
import com.netra.commons.models.endpoint.EndpointConfig;
import com.netra.commons.models.endpoint.MtlsAuth;
import com.netstra.disputes.services.client.MtlsContextService;
import com.netstra.disputes.services.client.util.EndpointConfigIdentity;
import com.netstra.disputes.services.client.util.Utility;
import com.netstra.disputes.services.client.vault.VaultManager;
import lombok.RequiredArgsConstructor;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.SSLContext;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.client5.http.ssl.HttpsSupport;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.client5.http.impl.classic.HttpClients;


@Component
@RequiredArgsConstructor
public class DomainRestClientFactory {

    private final VaultManager vaultManager;
    private final MtlsContextService mtlsContextService;

    private final Map<String, GeneralizedCachedClient> clientCache = new ConcurrentHashMap<>();
    private final int clientTtlHours = 2;

    public RestTemplate getClient(EndpointConfig config) {
        EndpointConfigIdentity identity = new EndpointConfigIdentity(config.getDomainType(), config.getDomainOwnerId(), config.getDomainCode());

        String cachedId = Utility.calculateCachedRestClientId(identity);

        return clientCache.compute(cachedId, (key, existing) -> {
            if (existing != null && !existing.isExpired()) return existing;

            RestTemplate client = buildClient(config);
            return new GeneralizedCachedClient(client, Instant.now().plus(Duration.ofHours(clientTtlHours)));
        }).getClient();
    }

    private RestTemplate buildClient(EndpointConfig config) {
        int timeout = config.getNetwork().getTimeoutMillis();
        HttpComponentsClientHttpRequestFactory requestFactory;

        AuthConfig auth = config.getSecurity().getAuthConfigs().isEmpty()
                ? null
                : config.getSecurity().getAuthConfigs().get(0);

        if (auth instanceof MtlsAuth mtlsAuth) {
            SSLContext sslContext = mtlsContextService.getOrCreateContext(config.getDomainCode(), mtlsAuth);
            PoolingHttpClientConnectionManager connManager = PoolingHttpClientConnectionManagerBuilder.create()
                    .setTlsSocketStrategy(ClientTlsStrategyBuilder.create()
                            .setSslContext(sslContext)
                            .setTlsVersions("TLSv1.2", "TLSv1.3")
                            .setHostnameVerifier(HttpsSupport.getDefaultHostnameVerifier())
                            .buildClassic())
                    .build();

            CloseableHttpClient httpClient = HttpClients.custom()
                    .setConnectionManager(connManager)
                    .evictIdleConnections(TimeValue.ofSeconds(30))
                    .evictExpiredConnections()
                    .build();

            requestFactory = new HttpComponentsClientHttpRequestFactory(httpClient);

        } else {
            requestFactory = new HttpComponentsClientHttpRequestFactory();
        }

        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);

        RestTemplate restTemplate = new RestTemplate(requestFactory);

        if (auth != null && !(auth instanceof MtlsAuth)) {
            restTemplate.getInterceptors().add(new AuthInterceptor(auth, vaultManager));
        }

        return restTemplate;
    }
}

