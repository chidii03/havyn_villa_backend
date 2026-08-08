package com.havyn.auth.domain;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "havyn.bootstrap-admin")
public record AdminBootstrapProperties(
        String email,
        String password,
        String fullName,
        boolean enabled) {
}
