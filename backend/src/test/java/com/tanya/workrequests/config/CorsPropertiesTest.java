package com.tanya.workrequests.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class CorsPropertiesTest {

    @ParameterizedTest
    @ValueSource(strings = {"*", "https://*.example.com"})
    void rejectsWildcardOrigins(String origin) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new CorsProperties(List.of("http://localhost:5173", origin)))
                .withMessageContaining("app.cors.allowed-origins");
    }

    @Test
    void keepsExactOrigins() {
        assertThat(new CorsProperties(List.of("https://app.example.com")).allowedOrigins())
                .containsExactly("https://app.example.com");
    }
}
