package com.netstra.disputes.services.client;

import com.netra.commons.models.EndpointConfig;

import java.net.URI;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;

import javax.net.ssl.SSLContext;

public class DynamicMtlsRequestFactory extends HttpComponentsClientHttpRequestFactory {

    private final MtlsContextService mtlsContextService;
    private final EndpointConfig config;

    public DynamicMtlsRequestFactory(MtlsContextService mtlsContextService, EndpointConfig config) {
        this.mtlsContextService = mtlsContextService;
        this.config = config;
    }

    @Override
    protected HttpContext createHttpContext(HttpMethod httpMethod, URI uri) {
        try {
            SSLContext sslContext = mtlsContextService.getOrCreateContext(
                    config.getDomainCode(),
                    config.getAuthConfig().getProperties()
            );

            SSLConnectionSocketFactory socketFactory = new SSLConnectionSocketFactory(sslContext);

            CloseableHttpClient client = HttpClients.custom()
                    .setSSLSocketFactory(socketFactory)
                    .build();

            setHttpClient(client); // hot-swap for this request
        } catch (Exception e) {
            throw new RuntimeException("Failed to configure MTLS for " + config.getDomainCode(), e);
        }

        return super.createHttpContext(httpMethod, uri);
    }
}

