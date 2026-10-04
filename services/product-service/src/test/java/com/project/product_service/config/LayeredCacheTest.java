package com.project.product_service.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LayeredCacheTest {

    @Test
    void concurrentVirtualThreadsLoadSameMissOnlyOnce() throws Exception {
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        ValueOperations<String, Object> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        var redisReads = new CountDownLatch(2);
        when(valueOperations.get("products:sku-1")).thenAnswer(invocation -> {
            redisReads.countDown();
            if (!redisReads.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Both callers should reach Redis before loading");
            }
            return null;
        });
        var cache = new LayeredCache("products", Caffeine.newBuilder().build(), redisTemplate, 60);
        var loaderCalls = new AtomicInteger();
        var loaderEntered = new CountDownLatch(1);
        var releaseLoader = new CountDownLatch(1);
        Callable<String> loader = () -> {
            loaderCalls.incrementAndGet();
            loaderEntered.countDown();
            if (!releaseLoader.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Test did not release loader");
            }
            return "loaded";
        };
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            try {
                Future<String> first = executor.submit(() -> cache.get("sku-1", loader));
                Future<String> second = executor.submit(() -> cache.get("sku-1", loader));

                if (!loaderEntered.await(5, TimeUnit.SECONDS)) {
                    throw new AssertionError("Loader did not start");
                }
                releaseLoader.countDown();
                assertEquals("loaded", first.get(5, TimeUnit.SECONDS));
                assertEquals("loaded", second.get(5, TimeUnit.SECONDS));
                assertEquals(1, loaderCalls.get());
            } finally {
                releaseLoader.countDown();
            }
        }
    }

    @Test
    void loaderFailureReleasesLockForNextMiss() throws Exception {
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        ValueOperations<String, Object> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("products:sku-2")).thenReturn(null);
        var cache = new LayeredCache("products", Caffeine.newBuilder().build(), redisTemplate, 60);
        var loaderEntered = new CountDownLatch(1);
        var releaseFailure = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            try {
                Future<String> failed = executor.submit(() -> cache.get("sku-2", () -> {
                    loaderEntered.countDown();
                    if (!releaseFailure.await(5, TimeUnit.SECONDS)) {
                        throw new AssertionError("Test did not release loader");
                    }
                    throw new IllegalStateException("load failed");
                }));
                if (!loaderEntered.await(5, TimeUnit.SECONDS)) {
                    throw new AssertionError("Loader did not start");
                }
                Future<String> recovered = executor.submit(() -> cache.get("sku-2", () -> "recovered"));
                releaseFailure.countDown();

                ExecutionException executionFailure = assertThrows(ExecutionException.class,
                        () -> failed.get(5, TimeUnit.SECONDS));
                Cache.ValueRetrievalException wrapped = assertInstanceOf(Cache.ValueRetrievalException.class,
                        executionFailure.getCause());
                assertInstanceOf(IllegalStateException.class, wrapped.getCause());
                assertEquals("recovered", recovered.get(5, TimeUnit.SECONDS));
            } finally {
                releaseFailure.countDown();
            }
        }
    }
}
