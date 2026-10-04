package com.project.common.feign;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class HttpServiceTokenClientTest {
    @Test
    void postsUrlEncodedClientCredentialsAndReadsOAuthResponse() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> method = new AtomicReference<>();
        AtomicReference<String> contentType = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        server.createContext("/token", exchange -> {
            method.set(exchange.getRequestMethod());
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = """
                    {"access_token":"machine-token","token_type":"Bearer","expires_in":300,"scope":"coupons.read"}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (var output = exchange.getResponseBody()) {
                output.write(response);
            }
        });
        server.start();
        try {
            ServiceTokenResponse response = new HttpServiceTokenClient().requestToken(properties(server));

            assertThat(method.get()).isEqualTo("POST");
            assertThat(contentType.get()).startsWith("application/x-www-form-urlencoded");
            Map<String, String> form = Arrays.stream(body.get().split("&"))
                    .map(pair -> pair.split("=", 2))
                    .collect(Collectors.toMap(pair -> URLDecoder.decode(pair[0], StandardCharsets.UTF_8),
                            pair -> URLDecoder.decode(pair[1], StandardCharsets.UTF_8)));
            assertThat(form).containsExactlyInAnyOrderEntriesOf(Map.of(
                    "grant_type", "client_credentials", "client_id", "cart-service",
                    "client_secret", "test +&=secret", "scope", "coupons.read"));
            assertThat(response).isEqualTo(new ServiceTokenResponse("machine-token", "Bearer", 300, "coupons.read"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void defaultTransportFailsWithinFiveSecondsWhenTokenEndpointStalls() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        CountDownLatch received = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        server.createContext("/token", exchange -> {
            received.countDown();
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        var executor = Executors.newSingleThreadExecutor();
        server.start();
        try {
            var result = executor.submit(() -> {
                try {
                    new HttpServiceTokenClient().requestToken(properties(server));
                    return (Throwable) null;
                } catch (RuntimeException exception) {
                    return exception;
                }
            });
            assertThat(received.await(2, TimeUnit.SECONDS)).isTrue();
            Throwable failure;
            try {
                failure = result.get(5, TimeUnit.SECONDS);
            } catch (java.util.concurrent.TimeoutException exception) {
                throw new AssertionError("Token exchange must bound its read wait below five seconds", exception);
            }
            assertThat(failure).isInstanceOf(ResourceAccessException.class);
        } finally {
            release.countDown();
            server.stop(0);
            executor.shutdownNow();
        }
    }

    private ServiceAuthProperties properties(HttpServer server) {
        ServiceAuthProperties properties = new ServiceAuthProperties();
        properties.setTokenUri("http://127.0.0.1:" + server.getAddress().getPort() + "/token");
        properties.setClientId("cart-service");
        properties.setClientSecret("test +&=secret");
        properties.setScope("coupons.read");
        return properties;
    }
}
