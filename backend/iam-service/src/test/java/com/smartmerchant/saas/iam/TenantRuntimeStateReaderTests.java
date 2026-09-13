package com.smartmerchant.saas.iam;

import com.smartmerchant.saas.iam.application.TenantRuntimeStateReader;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TenantRuntimeStateReaderTests {
    @SuppressWarnings("unchecked")
    @Test
    void acceptsOnlyActiveTenantStateAndReturnsItsVersion() {
        var redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get("saas:tenant:status:101")).thenReturn("ACTIVE:4");
        when(values.get("saas:tenant:status:102")).thenReturn("SUSPENDED:5");
        var reader = new TenantRuntimeStateReader(redis, true);

        assertThat(reader.requireActive(101)).isEqualTo(4);
        assertThatThrownBy(() -> reader.requireActive(102)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> reader.requireActive(103)).isInstanceOf(AccessDeniedException.class);
        assertThat(reader.requireActive(0)).isZero();
    }
}
