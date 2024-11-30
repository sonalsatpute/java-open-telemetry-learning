package com.sonalsatpute.http_metrics.hosting;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.metrics.*;
import org.springframework.stereotype.Component;

import java.util.Set;

import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.HttpAttributes.*;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PROTOCOL_VERSION;
import static io.opentelemetry.semconv.UrlAttributes.URL_SCHEME;
import static io.opentelemetry.semconv.UserAgentAttributes.USER_AGENT_ORIGINAL;

//Reference C# implementation:
// https://github.com/dotnet/aspnetcore/blob/81a2bab8704d87d324039b42eb1bab0d977f25b8/src/Hosting/Hosting/src/Internal/HostingMetrics.cs
//

@Component
public final class HostingMetrics {

    public static final String HOSTING_METER_NAME = "spring-boot.hosting";
    public static final String REQUEST_DURATION = "http.server.duration";
    public static final String ACTIVE_REQUEST_COUNT = "http.server.active_requests";
    public static final String DURATION_HISTOGRAM_UNIT = "ms"; // milliseconds
    public static final String ACTIVE_REQUEST_COUNTER_UNIT = "request"; // milliseconds

    private final DoubleHistogram durationHistogram;
    private final LongUpDownCounter activeRequestCounter;
    private static final Set<String> KnownMethods;

    static {
        KnownMethods = Set.of(
                HttpRequestMethodValues.CONNECT,
                HttpRequestMethodValues.DELETE,
                HttpRequestMethodValues.GET,
                HttpRequestMethodValues.HEAD,
                HttpRequestMethodValues.OPTIONS,
                HttpRequestMethodValues.PATCH,
                HttpRequestMethodValues.POST,
                HttpRequestMethodValues.PUT,
                HttpRequestMethodValues.TRACE
        );
    }

    public HostingMetrics(MeterProvider meterProvider) {
        this.activeRequestCounter = buildActiveRequestCounter(meterProvider);
        this.durationHistogram = buildDurationHistogram(meterProvider);
    }

    public void requestStart(String scheme, String method, Attributes customAttributes) {
        activeRequestCounter.add(1,
                Attributes.builder()
                        .putAll(customAttributes)
                        .putAll(Attributes.of(URL_SCHEME, scheme, HTTP_REQUEST_METHOD, resolveHttpMethod(method)))
                        .build()
        );
    }

    public void requestEnd(String protocol,
                           String scheme,
                           String method,
                           String route,
                           int statusCode,
//                           boolean unhandledRequest,
                           String userAgent,
                           Exception exception,
                           Attributes customAttributes,
                           long startTimestamp,
                           long currentTimestamp,
                           boolean disableHttpRequestDurationMetric){

        Attributes attributes = Attributes.builder()
                .putAll(customAttributes) // Add before some built in tags so custom tags are prioritized when dealing with duplicates.
                .putAll(Attributes.of(URL_SCHEME, scheme, HTTP_REQUEST_METHOD, resolveHttpMethod(method)))
                .build();

        activeRequestCounter.add(-1, attributes);

        if (!disableHttpRequestDurationMetric) {

            AttributesBuilder attributesBuilder = Attributes.builder()
                    .putAll(attributes)
                    .put(NETWORK_PROTOCOL_VERSION , protocol)
                    .put(HTTP_RESPONSE_STATUS_CODE, statusCode)
                    .put(HTTP_ROUTE, route)
                    .put(USER_AGENT_ORIGINAL, userAgent);


            // This exception is only present if there is an unhandled exception.
            // An exception caught by ExceptionHandlerMiddleware and DeveloperExceptionMiddleware isn't thrown to here.
            // Instead, those middleware add error.type to custom tags.
            if (exception != null) {
                // Exception tag could have been added by middleware. If an exception is later thrown in a request pipeline,
                // then we don't want to add a duplicate tag here because that breaks some metrics systems.
                attributesBuilder.put(ERROR_TYPE, exception.getClass().getName());
            }

            long duration = currentTimestamp - startTimestamp;
            durationHistogram.record(duration, attributesBuilder.build());
        }

    }

    private DoubleHistogram buildDurationHistogram(MeterProvider meterProvider) {
        return meterProvider
                .get(HOSTING_METER_NAME)
                .histogramBuilder(REQUEST_DURATION)
                .setDescription("Duration of HTTP requests")
                .setUnit(DURATION_HISTOGRAM_UNIT)
                .setExplicitBucketBoundariesAdvice(MetricsConstants.ShortSecondsBucketBoundaries)
                .build();
    }

    private LongUpDownCounter buildActiveRequestCounter(MeterProvider meterProvider) {
        return meterProvider
                .get(HOSTING_METER_NAME)
                .upDownCounterBuilder(ACTIVE_REQUEST_COUNT)
                .setDescription("Active HTTP requests")
                .setUnit(ACTIVE_REQUEST_COUNTER_UNIT)
                .build();
    }

    private static String resolveHttpMethod(String method)
    {
        String methodUpperCase = method.toUpperCase();
        return KnownMethods.contains(methodUpperCase)
                ? methodUpperCase
                : HttpRequestMethodValues.OTHER;
    }
}