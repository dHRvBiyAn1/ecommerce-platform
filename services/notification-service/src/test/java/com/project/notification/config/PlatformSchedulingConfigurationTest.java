package com.project.notification.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlatformSchedulingConfigurationTest {
    @Test
    void schedulerKeepsBootDefaultsAndPlatformThreadsForBothVirtualThreadSettings() throws Exception {
        for (boolean virtualThreads : new boolean[]{false, true}) {
            ThreadPoolTaskScheduler scheduler;
            try (var context = new SpringApplicationBuilder(TestApplication.class)
                    .web(WebApplicationType.NONE)
                    .run("--spring.main.keep-alive=true", "--spring.threads.virtual.enabled=" + virtualThreads,
                            "--spring.cloud.config.enabled=false", "--spring.cloud.discovery.enabled=false",
                            "--eureka.client.enabled=false", "--spring.task.scheduling.shutdown.await-termination=true")) {
                scheduler = context.getBean("taskScheduler", ThreadPoolTaskScheduler.class);
                Probe probe = context.getBean(Probe.class);
                assertEquals(1, scheduler.getScheduledThreadPoolExecutor().getCorePoolSize());
                assertEquals("scheduling-", scheduler.getThreadNamePrefix());
                assertTrue(probe.invoked.await(2, TimeUnit.SECONDS));
                assertFalse(probe.virtualThread.get());
            }
            assertTrue(scheduler.getScheduledThreadPoolExecutor().isShutdown());
        }
    }

    @Test
    void schedulerHonorsPoolAndNameOverridesForBothVirtualThreadSettings() throws Exception {
        for (boolean virtualThreads : new boolean[]{false, true}) {
            try (var context = new SpringApplicationBuilder(TestApplication.class)
                    .web(WebApplicationType.NONE)
                    .run("--spring.main.keep-alive=true", "--spring.threads.virtual.enabled=" + virtualThreads,
                            "--spring.cloud.config.enabled=false", "--spring.cloud.discovery.enabled=false",
                            "--eureka.client.enabled=false", "--spring.task.scheduling.pool.size=3",
                            "--spring.task.scheduling.thread-name-prefix=platform-job-",
                            "--spring.task.scheduling.shutdown.await-termination=true")) {
                ThreadPoolTaskScheduler scheduler = context.getBean("taskScheduler", ThreadPoolTaskScheduler.class);
                Probe probe = context.getBean(Probe.class);
                assertEquals(3, scheduler.getScheduledThreadPoolExecutor().getCorePoolSize());
                assertEquals("platform-job-", scheduler.getThreadNamePrefix());
                assertTrue(probe.invoked.await(2, TimeUnit.SECONDS));
                assertFalse(probe.virtualThread.get());
                assertEquals(1, context.getBeansOfType(ThreadPoolTaskScheduler.class).size());
                context.close();
                assertTrue(scheduler.getScheduledThreadPoolExecutor().isShutdown());
            }
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration(excludeName = {
            "org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration",
            "org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration",
            "org.springframework.boot.autoconfigure.data.mongo.MongoRepositoriesAutoConfiguration",
            "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration",
            "org.springframework.cloud.netflix.eureka.EurekaClientAutoConfiguration"
    })
    @Import({PlatformSchedulingConfiguration.class, ProbeConfiguration.class})
    static class TestApplication { }

    @TestConfiguration(proxyBeanMethods = false)
    static class ProbeConfiguration {
        @Bean Probe probe() { return new Probe(); }
    }

    static class Probe {
        final CountDownLatch invoked = new CountDownLatch(1);
        final AtomicBoolean virtualThread = new AtomicBoolean(true);
        @Scheduled(fixedDelay = 20)
        void tick() {
            virtualThread.set(Thread.currentThread().isVirtual());
            invoked.countDown();
        }
    }
}
