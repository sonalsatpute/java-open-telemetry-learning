package com.sonalsatpute.http_metrics.configurations;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.metrics.MeterProvider;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.resources.Resource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenTelemetryConfig {

    @Bean
    public OpenTelemetry openTelemetry() {

        Resource resource =
                Resource.getDefault()
                        .merge(
                                Resource.builder()
                                        .put("service.name", "http-metrics")
                                        .build());

        OtlpGrpcMetricExporter metricExporter = OtlpGrpcMetricExporter.builder()
                .setEndpoint("http://localhost:4317")
                .build();

        SdkMeterProvider meterProvider = SdkMeterProvider.builder()
                .setResource(resource)
                .registerMetricReader(PeriodicMetricReader.builder(metricExporter).build())
                .build();

        return OpenTelemetrySdk.builder()
                .setMeterProvider(meterProvider)
                .buildAndRegisterGlobal();
    }

    @Bean
    public MeterProvider meterProvider(OpenTelemetry openTelemetry) {
        return openTelemetry.getMeterProvider();
    }
}
