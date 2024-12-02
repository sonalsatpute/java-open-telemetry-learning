package com.sonalsatpute.observability.configurations;

import com.sonalsatpute.observability.hosting.HostingMetrics;
import com.sonalsatpute.observability.interceptors.MetricsInterceptor;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.metrics.MeterProvider;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.semconv.ServiceAttributes;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenTelemetryConfiguration {

    @Value("${spring.application.name}")
    private String springApplicationName;

    @Value("${otel.exporter.otlp.endpoint}")
    private String otelExporterOtlpEndpoint;

    @Bean
    public OpenTelemetry openTelemetry() {

        Resource resource =
                Resource.getDefault()
                        .merge(
                                Resource.builder()
                                        .put(ServiceAttributes.SERVICE_NAME, springApplicationName)
                                        .build());

        MetricExporter metricExporter = OtlpGrpcMetricExporter.builder()
                .setEndpoint(otelExporterOtlpEndpoint)
                .build();

        SdkMeterProvider meterProvider = SdkMeterProvider.builder()
                .setResource(resource)
                .registerMetricReader(PeriodicMetricReader.builder(metricExporter).build())
                .build();

        OpenTelemetrySdk openTelemetry = OpenTelemetrySdk.builder()
                .setMeterProvider(meterProvider)
                .buildAndRegisterGlobal();

        Runtime.getRuntime().addShutdownHook(new Thread(openTelemetry::close));


        return openTelemetry;
    }

    @Bean
    public MeterProvider meterProvider(OpenTelemetry openTelemetry) {
        return openTelemetry.getMeterProvider();
    }
}
