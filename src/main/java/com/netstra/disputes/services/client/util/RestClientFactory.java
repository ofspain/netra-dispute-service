package com.netstra.disputes.services.client.util;

import com.netra.commons.models.endpoint.*;
import com.netstra.disputes.services.client.EndpointSecretManager;
import com.netstra.disputes.services.client.MtlsContextService;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.client5.http.ssl.DefaultHostnameVerifier;
import org.apache.hc.client5.http.ssl.HttpsSupport;
import org.apache.hc.client5.http.ssl.TlsSocketStrategy;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.ssl.TLS;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import javax.net.ssl.SSLContext;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


@Slf4j
@Component
@RequiredArgsConstructor
public class RestClientFactory {

    private final Validator validator;
    private final EndpointSecretManager secretManager;
    private final MtlsContextService mtlsContextService;

    // Cache MTLS RestClients per domain
    private final Map<String, CachedRestClientEntry> mtlsClientCache = new ConcurrentHashMap<>();
    private final int clientTtl = 2;

    /**
     * Builds a RestClient configured with proxy settings from the provided EndpointConfig.
     */
    public RestClient createRestClientWithProxy(NetworkConfig networkConfig) {
        ProxyConfig proxyConfig = networkConfig.getProxy();
        String proxyHost = proxyConfig.getHost();
        int proxyPort = proxyConfig.getPort();
        String proxyUsername = proxyConfig.getUsername();
        String proxyPassword = proxyConfig.getPassword() != null
                ? secretManager.resolveSecret(proxyConfig.getPassword())
                : null;

        int timeout = networkConfig.getTimeoutMillis();

        HttpHost proxy = new HttpHost(proxyHost, proxyPort);
        log.debug("Configuring proxy: {}", proxy);

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(Timeout.ofMilliseconds(timeout))
                .setConnectionRequestTimeout(Timeout.ofMilliseconds(timeout))
                .setResponseTimeout(Timeout.ofMilliseconds(timeout))
                .setProxy(proxy)
                .build();

        HttpClientBuilder clientBuilder = HttpClients.custom()
                .setDefaultRequestConfig(requestConfig);

        // Configure proxy authentication only if credentials are provided
        if (proxyUsername != null && !proxyUsername.isBlank() && proxyPassword != null) {
            log.debug("Configuring proxy authentication for user: {}", proxyUsername);

            BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(
                    new AuthScope(proxyHost, proxyPort),
                    new UsernamePasswordCredentials(proxyUsername, proxyPassword.toCharArray())
            );

            clientBuilder.setDefaultCredentialsProvider(credentialsProvider);
        }

        CloseableHttpClient httpClient = clientBuilder.build();

        HttpComponentsClientHttpRequestFactory requestFactory =
                new HttpComponentsClientHttpRequestFactory(httpClient);

        requestFactory.setConnectTimeout(timeout);
        requestFactory.setConnectionRequestTimeout(timeout);

        return RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * Direct RestClient without proxy.
     */
    public RestClient buildRestClientUnProxied(int timeoutMs) {
        log.info("Building direct RestClient (no proxy)");

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(Timeout.ofMilliseconds(timeoutMs))
                .setConnectionRequestTimeout(Timeout.ofMilliseconds(timeoutMs))
                .setResponseTimeout(Timeout.ofMilliseconds(timeoutMs))
                .build();

        CloseableHttpClient httpClient = HttpClientBuilder.create()
                .setDefaultRequestConfig(requestConfig)
                .build();

        HttpComponentsClientHttpRequestFactory requestFactory =
                new HttpComponentsClientHttpRequestFactory(httpClient);

        requestFactory.setConnectTimeout(timeoutMs);
        requestFactory.setConnectionRequestTimeout(timeoutMs);

        return RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    public RestClient createRestClientWithMtls(MtlsAuth mtlsConfig, EndpointConfigIdentity identity,
                                                      NetworkConfig networkConfig) {



        String cacheId = Utility.calculateCachedRestClientId(identity);

        String domainCode = identity.domainCode();

        return mtlsClientCache.compute(cacheId, (id, existing) -> {
            if (existing != null && !existing.isExpired()) {
                return existing;
            }

            int timeout = networkConfig.getTimeoutMillis();
            SSLContext sslContext = mtlsContextService.getOrCreateContext(domainCode, mtlsConfig);

            RequestConfig requestConfig = RequestConfig.custom()
                    .setConnectTimeout(Timeout.ofMilliseconds(timeout))
                    .setResponseTimeout(Timeout.ofMilliseconds(timeout))
                    .build();

            SSLConnectionSocketFactory socketFactory = new SSLConnectionSocketFactory(
                    sslContext,
                    new String[]{"TLSv1.2", "TLSv1.3"},
                    null,
                    SSLConnectionSocketFactory.getDefaultHostnameVerifier()
            );

            TlsSocketStrategy tlsStrategy = ClientTlsStrategyBuilder.create()
                    .setSslContext(sslContext)
                    .setTlsVersions("TLSv1.2", "TLSv1.3")
                    .setHostnameVerifier(HttpsSupport.getDefaultHostnameVerifier())
                    .buildClassic();   // <-- IMPORTANT

            // 2. Connection manager with TLS
            PoolingHttpClientConnectionManager connectionManager =
                    PoolingHttpClientConnectionManagerBuilder.create()
                            .setTlsSocketStrategy(tlsStrategy)
                            .build();



            CloseableHttpClient httpClient = HttpClients.custom()
                    .setDefaultRequestConfig(requestConfig)
                    .setConnectionManager(connectionManager)
                    .evictExpiredConnections()
                    .evictIdleConnections(TimeValue.ofSeconds(30))
                    .build();

            HttpComponentsClientHttpRequestFactory requestFactory =
                    new HttpComponentsClientHttpRequestFactory(httpClient);

            requestFactory.setConnectTimeout(timeout);
            requestFactory.setConnectionRequestTimeout(timeout);
            requestFactory.setReadTimeout(timeout);

            RestClient client = RestClient.builder()
                    .requestFactory(requestFactory)
                    .build();

            return new CachedRestClientEntry(client, Instant.now().plus(Duration.ofHours(clientTtl)));
        }).getClient();

    }

    public void evictCachedMtlsClient(String domainCode) {
        mtlsClientCache.remove(domainCode);
    }

}






/**
 * Enhanced Sample Usage Examples:
 *
 * @Autowired
 * private ProxyRestClientFactory proxyRestClientFactory;
 *
 * // Basic proxy usage
 * public void callWithProxy(EndpointConfig config) {
 *     try {
 *         RestClient client = proxyRestClientFactory.buildFor(config);
 *
 *         String response = client.get()
 *                 .uri(config.getBaseUrl() + "/api/resource")
 *                 .header("Content-Type", "application/json")
 *                 .retrieve()
 *                 .body(String.class);
 *
 *         log.info("Received response: {}", response);
 *     } catch (Exception e) {
 *         log.error("Error calling API through proxy", e);
 *         throw e;
 *     }
 * }
 *
 * // POST request with error handling
 * public ResponseEntity<MyResponse> postWithProxy(EndpointConfig config, MyRequest request) {
 *     RestClient client = proxyRestClientFactory.buildFor(config);
 *
 *     return client.post()
 *             .uri(config.getBaseUrl() + "/api/submit")
 *             .header("Content-Type", "application/json")
 *             .body(request)
 *             .retrieve()
 *             .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
 *                 throw new ClientException("Client error: " + res.getStatusCode());
 *             })
 *             .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
 *                 throw new ServerException("Server error: " + res.getStatusCode());
 *             })
 *             .toEntity(MyResponse.class);
 * }
 *
 * // Direct client usage (fallback)
 * public void callDirect() {
 *     RestClient directClient = proxyRestClientFactory.buildDirectClient(30000);
 *     // use directClient for non-proxy calls
 *
 *
 *     SSL CERT
 *
 *     SSLContext sslContext = mtlsContextService.getOrCreateContext(
 *         config.getDomainCode(),
 *         config.getAuthConfig().getProperties()
 * );
 *
 * RestClient mtlsClient = proxyRestClientFactory.buildRestClientWithMtls(
 *         sslContext,
 *         config.getTimeoutMillis(),
 *         config.getTimeoutMillis()
 * );
 *
 * String response = mtlsClient.get()
 *         .uri(config.getBaseUrl() + "/balance")
 *         .retrieve()
 *         .body(String.class);
 * }
 *
 * RestClient client;
 * if (config.getDomainType() == DomainType.MTLS_PROVIDER) {
 *     client = proxyRestClientFactory.buildRestClientWithMtls(config);
 * } else if (config.isUseProxy()) {
 *     client = proxyRestClientFactory.buildProxiedRestClient(config);
 * } else {
 *     client = proxyRestClientFactory.buildRestClientUnProxied(config.getTimeoutMillis());
 * }
 *
 * String response = client.get()
 *         .uri(config.getBaseUrl() + "/balance")
 *         .retrieve()
 *         .body(String.class);
 */