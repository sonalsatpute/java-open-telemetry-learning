package com.sonalsatpute.http_metrics.hosting;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.*;

import java.util.Set;

//Reference C# implementation:
// https://github.com/dotnet/aspnetcore/blob/81a2bab8704d87d324039b42eb1bab0d977f25b8/src/Hosting/Hosting/src/Internal/HostingMetrics.cs
//

public final class HostingMetrics {

    public static final String HOSTING_METER_NAME = "spring-boot.hosting";
    public static final String REQUEST_DURATION = "http.server.duration";
    public static final String ACTIVE_REQUEST_COUNT = "http.server.active_requests";
    public static final String DURATION_HISTOGRAM_UNIT = "ms"; // milliseconds
    public static final String ACTIVE_REQUEST_COUNTER_UNIT = "request"; // milliseconds

    private final DoubleHistogram durationHistogram;
    private final LongUpDownCounter activeRequestCounter;
    private static final String UNKNOWN_METHOD = "_OTHER";

    private static final Set<String> KnownMethods;

    static {
        KnownMethods = Set.of(
                "CONNECT",
                "DELETE",
                "GET",
                "HEAD",
                "OPTIONS",
                "PATCH",
                "POST",
                "PUT",
                "TRACE"
        );
    }

    private static final Integer[] boxedStatusCodes = new Integer[512];



    private static final AttributeKey<String> SCHEME = AttributeKey.stringKey("scheme");
    private static final AttributeKey<String> METHOD = AttributeKey.stringKey("method");

    private Attributes attributes;

    public HostingMetrics(MeterProvider meterProvider) {
        this.activeRequestCounter = buildActiveRequestCounter(meterProvider);
        this.durationHistogram = buildDurationHistogram(meterProvider);
    }

    public void requestStart(String scheme, String method, Attributes customAttributes) {
        attributes = Attributes.builder()
                .putAll(Attributes.of(SCHEME, scheme, METHOD, resolveHttpMethod(method)))
                .putAll(customAttributes)
                .build();

        activeRequestCounter.add(1, attributes);
    }

    public void requestEnd(String protocol,
                           String scheme,
                           String method,
                           String route,
                           int statusCode,
                           boolean unhandledRequest,
                           Exception exception,
                           Attributes customAttributes,
                           long startTimestamp,
                           long currentTimestamp,
                           boolean disableHttpRequestDurationMetric){

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
                : UNKNOWN_METHOD;
    }

    private static Integer getBoxedStatusCode(int statusCode) {
        if (statusCode >= 0 && statusCode < boxedStatusCodes.length) {
            if (boxedStatusCodes[statusCode] == null) {
                boxedStatusCodes[statusCode] = statusCode;
            }
            return boxedStatusCodes[statusCode];
        }
        return statusCode;
    }

}
