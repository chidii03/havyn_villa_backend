package com.havyn.common.observability;

import io.opentelemetry.exporter.logging.LoggingSpanExporter;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * project-docs/prompts/26-observability.md: "OpenTelemetry tracing across API →
 * DB/Redis/providers." {@code micrometer-tracing-bridge-otel} (build.gradle) does the
 * actual instrumentation — Spring Boot auto-instruments JDBC (Postgres), Lettuce
 * (Redis), and outbound {@code RestClient} calls (Paystack/Cloudinary) automatically
 * once it's on the classpath, no manual span code needed for any of those.
 *
 * <p>What Boot does <em>not</em> auto-configure is a default exporter: {@code
 * OtlpAutoConfiguration} only activates when {@code management.otlp.tracing.endpoint}
 * is set (see application.yml's prompt 26 notes on why that's left unset by default).
 * Without this bean, spans would still be created and propagated (context/MDC
 * population works regardless), just never sent anywhere visible — this makes traces
 * actually observable in dev/CI console output with zero external infra, and keeps
 * working alongside a real OTLP exporter once one is configured (Boot combines every
 * {@link SpanExporter} bean in the context, it doesn't pick just one).
 */
@Configuration
public class TracingConfig {

    @Bean
    public SpanExporter loggingSpanExporter() {
        return LoggingSpanExporter.create();
    }
}
