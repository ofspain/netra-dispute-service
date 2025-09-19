package com.netstra.disputes.services.client;

import com.netra.commons.models.EndpointConfig;
import com.netra.commons.util.SecretCryptoUtil;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.CredentialsProvider;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.util.Timeout;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;


@Slf4j
@Component
@RequiredArgsConstructor
public class ProxyRestClientFactory {

    private final SecretCryptoUtil cryptoUtil;
    private final Validator validator;
    private final EndpointSecretManager secretManager;

    // Cache MTLS RestClients per domain
    private final Map<String, RestClient> mtlsClientCache = new ConcurrentHashMap<>();

    /**
     * Builds a RestClient configured with proxy settings from the provided EndpointConfig.
     */
    public RestClient buildProxiedRestClient(EndpointConfig config) {
        Set<ConstraintViolation<EndpointConfig>> violations = validator.validate(config);
        if (!violations.isEmpty()) {
            String errorMessages = violations.stream()
                    .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                    .collect(Collectors.joining(", "));
            throw new IllegalArgumentException("Invalid config: " + errorMessages);
        }

        EndpointConfig.ProxyConfig proxyConfig = config.getProxy();

        String host = proxyConfig.getHost();
        int port = proxyConfig.getPort();
        String username = proxyConfig.getUsername();
        String encryptedPassword = proxyConfig.getPassword();

        log.info("Building RestClient with proxy for config {}", config);

        String decryptedPassword = null;
        if (encryptedPassword != null && !encryptedPassword.isBlank()) {
            try {
                decryptedPassword = cryptoUtil.decrypt(encryptedPassword);
            } catch (Exception e) {
                log.error("Failed to decrypt proxy password", e);
                throw new IllegalStateException("Failed to decrypt proxy password", e);
            }
        }

        return createRestClientWithProxy(
                host, port, username, decryptedPassword,
                config.getTimeoutMillis(), config.getTimeoutMillis()
        );
    }

    private RestClient createRestClientWithProxy(String proxyHost, int proxyPort,
                                                 String proxyUsername, String proxyPassword,
                                                 int connectTimeoutMs, int readTimeoutMs) {

        HttpHost proxy = new HttpHost(proxyHost, proxyPort);
        log.debug("Configuring proxy: {}", proxy);

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(Timeout.ofMilliseconds(connectTimeoutMs))
                .setConnectionRequestTimeout(Timeout.ofMilliseconds(readTimeoutMs))
                .setResponseTimeout(Timeout.ofMilliseconds(readTimeoutMs))
                .setProxy(proxy)
                .build();

        HttpClientBuilder clientBuilder = HttpClientBuilder.create()
                .setDefaultRequestConfig(requestConfig);

        if (proxyUsername != null && !proxyUsername.isBlank() && proxyPassword != null) {
            log.debug("Configuring proxy authentication for user: {}", proxyUsername);

            CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            ((BasicCredentialsProvider) credentialsProvider).setCredentials(
                    new AuthScope(proxyHost, proxyPort),
                    new UsernamePasswordCredentials(proxyUsername, proxyPassword.toCharArray())
            );

            clientBuilder.setDefaultCredentialsProvider(credentialsProvider);
            setSystemProxyAuthenticator(proxyHost, proxyPort, proxyUsername, proxyPassword);
        }

        CloseableHttpClient httpClient = clientBuilder.build();
        HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory(httpClient);

        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setConnectionRequestTimeout(readTimeoutMs);

        return RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    private void setSystemProxyAuthenticator(String proxyHost, int proxyPort,
                                             String username, String password) {
        log.debug("Setting system proxy properties");

        System.setProperty("http.proxyHost", proxyHost);
        System.setProperty("http.proxyPort", String.valueOf(proxyPort));
        System.setProperty("https.proxyHost", proxyHost);
        System.setProperty("https.proxyPort", String.valueOf(proxyPort));

        if (username != null && !username.isBlank() && password != null) {
            Authenticator.setDefault(new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    if (getRequestorType() == RequestorType.PROXY) {
                        if (proxyHost.equals(getRequestingHost()) && proxyPort == getRequestingPort()) {
                            return new PasswordAuthentication(username, password.toCharArray());
                        }
                    }
                    return null;
                }
            });
        }
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

    /**
     * Builds (and caches) a RestClient with MTLS support for the given domain.
     */
    public RestClient buildRestClientWithMtls(EndpointConfig config) {
        return mtlsClientCache.computeIfAbsent(config.getDomainCode(), dc -> {
            log.info("Building RestClient with mTLS for domain {}", dc);
            try {
                Map<String, String> props = config.getAuthConfig().getProperties();
                String certPath = secretManager.resolveSecret(props.get("certPath"));
                String certPassword = secretManager.resolveSecret(props.get("certPassword"));

                SSLContext sslContext = buildSslContext(certPath, certPassword);

                return createRestClientWithMtls(sslContext,
                        config.getTimeoutMillis(),
                        config.getTimeoutMillis());
            } catch (Exception e) {
                throw new RuntimeException("Failed to initialize MTLS RestClient for " + dc, e);
            }
        });
    }

    private SSLContext buildSslContext(String certPath, String certPassword) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream is = new FileInputStream(certPath)) {
            keyStore.load(is, certPassword.toCharArray());
        }

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, certPassword.toCharArray());

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), null, new SecureRandom());

        return sslContext;
    }

    private RestClient createRestClientWithMtls(SSLContext sslContext,
                                                int connectTimeoutMs,
                                                int readTimeoutMs) {
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(Timeout.ofMilliseconds(connectTimeoutMs))
                .setResponseTimeout(Timeout.ofMilliseconds(readTimeoutMs))
                .build();

        PoolingHttpClientConnectionManager connectionManager =
                new PoolingHttpClientConnectionManager();

        // SSLContext is passed directly into the builder
        CloseableHttpClient httpClient = HttpClients.custom()
                .setDefaultRequestConfig(requestConfig)
                .setConnectionManager(connectionManager)
                .setSSLContext(sslContext)
                .build();

        HttpComponentsClientHttpRequestFactory requestFactory =
                new HttpComponentsClientHttpRequestFactory(httpClient);

        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setConnectionRequestTimeout(readTimeoutMs);

        return RestClient.builder()
                .requestFactory(requestFactory)
                .build();
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