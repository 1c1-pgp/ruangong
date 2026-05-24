package com.zzw.chatserver;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * 拉起完整 Spring 上下文；依赖本机 Docker（会自动启动 MongoDB / Redis 容器）。
 */
@SuppressWarnings("resource")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"dev", "test"})
@Testcontainers(disabledWithoutDocker = true)
class ChatServerApplicationTests {

    @Container
    static MongoDBContainer mongo = new MongoDBContainer("mongo:5.0");

    @Container
    static GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    @DynamicPropertySource
    static void registerDatasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongo::getConnectionString);
        registry.add("spring.redis.host", redis::getHost);
        registry.add("spring.redis.port", () -> String.valueOf(redis.getMappedPort(6379)));
        registry.add("spring.redis.ssl", () -> "false");
        registry.add("spring.redis.password", () -> "");
        registry.add("socketio.enabled", () -> "false");
        registry.add("jwt.secret", () -> "unit-test-jwt-secret-at-least-32-characters-long");
        registry.add("management.server.port", () -> "0");
    }

    @Test
    void contextLoads() {
    }
}
