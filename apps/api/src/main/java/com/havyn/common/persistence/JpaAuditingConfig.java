package com.havyn.common.persistence;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/** Enables {@code @CreatedDate}/{@code @LastModifiedDate} population on {@link BaseEntity}. */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
