package com.smartmerchant.saas.merchant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M6 infrastructure gate. Run explicitly with M6_TESTCONTAINERS=true so normal
 * unit-test feedback remains fast while CI/release validation uses real services.
 */
@Testcontainers
@EnabledIfSystemProperty(named = "m6.testcontainers", matches = "(?i)true")
class InfrastructureContainersTests {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("analytics_test").withUsername("saas").withPassword("saas-test");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4-alpine")
            .withExposedPorts(6379).withCommand("redis-server", "--appendonly", "no");

    @Container
    static final GenericContainer<?> RABBITMQ = new GenericContainer<>("rabbitmq:4-management")
            .withExposedPorts(5672, 15672);

    @Test
    void mysqlRedisAndRabbitMqAreReachable() throws Exception {
        try (var connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
             var statement = connection.createStatement()) {
            assertThat(statement.executeQuery("SELECT 1").next()).isTrue();
        }
        assertThat(REDIS.isRunning()).isTrue();
        assertThat(REDIS.getMappedPort(6379)).isPositive();
        assertThat(RABBITMQ.isRunning()).isTrue();
        assertThat(RABBITMQ.getMappedPort(5672)).isPositive();
    }
}
