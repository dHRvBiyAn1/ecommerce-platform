package com.project.common.feign;

import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

public class HttpServiceTokenClient implements ServiceTokenClient {

    private final RestClient restClient;

    public HttpServiceTokenClient() {
        this(RestClient.builder()
                .requestFactory(boundedRequestFactory())
                .build());
    }

    private static org.springframework.http.client.JdkClientHttpRequestFactory boundedRequestFactory() {
        var client = java.net.http.HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(2))
                .followRedirects(java.net.http.HttpClient.Redirect.NEVER)
                .build();
        var factory = new org.springframework.http.client.JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(java.time.Duration.ofSeconds(3));
        return factory;
    }

    HttpServiceTokenClient(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public ServiceTokenResponse requestToken(ServiceAuthProperties properties) {
        var form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", properties.getClientId());
        form.add("client_secret", properties.getClientSecret());
        if (properties.getScope() != null && !properties.getScope().isBlank()) {
            form.add("scope", properties.getScope());
        }
        ServiceTokenResponse response = restClient.post()
                .uri(properties.getTokenUri())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(ServiceTokenResponse.class);
        if (response == null || response.accessToken() == null || response.accessToken().isBlank()) {
            throw new IllegalStateException("Auth service returned an empty service token");
        }
        return response;
    }
}
