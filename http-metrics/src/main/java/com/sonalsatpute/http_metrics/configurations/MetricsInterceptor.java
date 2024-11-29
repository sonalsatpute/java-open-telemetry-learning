package com.sonalsatpute.http_metrics.configurations;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.metrics.LongHistogram;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.MeterProvider;
import io.opentelemetry.api.common.Attributes;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Arrays;
import java.util.List;


@Component
public class MetricsInterceptor implements HandlerInterceptor {

    private static final String REQUEST_START_TIME = "request_start_time";
    private static final String REQUEST_ROUTE_TEMPLATE = "request_route_template";


    private static final AttributeKey<String> HTTP_REQUEST_METHOD = AttributeKey.stringKey("http.request.method");
    private static final AttributeKey<String> HTTP_ROUTE = AttributeKey.stringKey("http.route");
    private static final AttributeKey<Long> HTTP_RESPONSE_STATUS_CODE = AttributeKey.longKey("http.response.status_code");

    private static final AttributeKey<String> NETWORK_PROTOCOL_VERSION = AttributeKey.stringKey("network.protocol.version");
    private static final AttributeKey<String> URL_SCHEME = AttributeKey.stringKey("url.scheme");
    private static final AttributeKey<String> HTTP_HOST = AttributeKey.stringKey("http.host");
    private static final AttributeKey<String> ERROR_TYPE = AttributeKey.stringKey("error.type");
    private static final AttributeKey<String> HTTP_USER_AGENT = AttributeKey.stringKey("http.user_agent");

    private static final AttributeKey<Long> TENANT_ID = AttributeKey.longKey("tenant.id");
    private static final AttributeKey<Long> SITE_ID = AttributeKey.longKey("site.id");
    private static final AttributeKey<String> ENVIRONMENT = AttributeKey.stringKey("environment");
    private static final AttributeKey<String> SCALE_UNIT_ID = AttributeKey.stringKey("scale_unit_id");
    private static final AttributeKey<String> NAMESPACE_NAME = AttributeKey.stringKey("namespace.name");

    private static final String USER_AGENT_HEADER = "User-Agent";
    private static final String TENANT_ID_HEADER = "X-TenantId";
    private static final String SITE_ID_HEADER = "X-SiteId";

    private static final Attributes environmentAttributes;
    private static final Logger log = LoggerFactory.getLogger(MetricsInterceptor.class);

    static {
        environmentAttributes = Attributes.of(
                ENVIRONMENT, "local",
                SCALE_UNIT_ID, "development",
                NAMESPACE_NAME, "local-development");
    }

    private final LongHistogram durationHistogram;

    //This range is designed to capture short to moderately long request durations, from 0 milliseconds up to 10,000 milliseconds (10 seconds).
    List<Long> bucketBoundaries = Arrays.asList(0L, 5L, 10L, 25L, 50L, 75L, 100L, 250L, 500L, 750L, 1000L, 2500L, 5000L, 7500L, 10000L);


    public MetricsInterceptor(MeterProvider meterProvider) {
        Meter meter = meterProvider.get("http.server.request");
        this.durationHistogram = buildDurationHistogram(meter);
    }


    private LongHistogram buildDurationHistogram(Meter meter) {

        return meter.histogramBuilder("http.server.duration")
                .setDescription("Duration of HTTP requests")
                .setUnit("ms")
                .ofLongs()
                .setExplicitBucketBoundariesAdvice(bucketBoundaries)
                .build();
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute(REQUEST_START_TIME, System.currentTimeMillis());
        request.setAttribute(REQUEST_ROUTE_TEMPLATE, request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE));

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        long startTime = (long) request.getAttribute(REQUEST_START_TIME);
        long duration = System.currentTimeMillis() - startTime;

        this.durationHistogram.record(duration, httpAttributes(request, response, getErrorType(response, ex)));
    }

    private Attributes httpAttributes(
            HttpServletRequest request,
            HttpServletResponse response,
            String errorType
    ) {
        return Attributes.builder()
                .put(HTTP_REQUEST_METHOD, request.getMethod())
                .put(HTTP_ROUTE, (String) request.getAttribute(REQUEST_ROUTE_TEMPLATE))
                .put(HTTP_RESPONSE_STATUS_CODE, response.getStatus())
                .put(NETWORK_PROTOCOL_VERSION, request.getProtocol())
                .put(URL_SCHEME, request.getScheme())
                .put(HTTP_HOST, request.getServerName())
                .put(ERROR_TYPE, errorType)
                .put(HTTP_USER_AGENT, request.getHeader(USER_AGENT_HEADER))
                .put(TENANT_ID, getHeaderLongValue(request, TENANT_ID_HEADER))
                .put(SITE_ID, getHeaderLongValue(request, SITE_ID_HEADER))
                .putAll(buildQueryStringAttributes(request))
                .putAll(environmentAttributes)
                .build();
    }

    private static long getHeaderLongValue(HttpServletRequest request, String headerName) {
        String headerValue = request.getHeader(headerName);
        if (headerValue == null) {
            return 0;
        }
        return Long.parseLong(headerValue);
    }

    private static Attributes buildQueryStringAttributes(HttpServletRequest request) {
        AttributesBuilder attributesBuilder = Attributes.builder();
        request.getParameterMap().forEach((key, value) -> {
            if (value.length == 1) {
                attributesBuilder.put(key, value[0]);
            } else {
                attributesBuilder.put(key, value);
            }
        });
        return attributesBuilder.build();
    }

//    private static Attributes buildHeadersAttributes(HttpServletRequest request) {
//        AttributesBuilder attributesBuilder = Attributes.builder();
//        request.getHeaderNames().asIterator().forEachRemaining(headerName -> {
//            String headerValue = request.getHeader(headerName);
//            attributesBuilder.put(headerName, headerValue);
//        });
//        return attributesBuilder.build();
//    }

    private static String getErrorType(HttpServletResponse response, Exception ex) {
        return switch (response.getStatus()) {
            case 500 -> "InternalServerError";
            case 408 -> "Timeout";
            case 404 -> "NameResolutionError";
            case 400 -> "BadRequest";
            case 401 -> "Unauthorized";
            case 403 -> "Forbidden";
            case 429 -> "RateLimitExceeded";
            case 503 -> "ServiceUnavailable";
            case 504 -> "GatewayTimeout";
            case 502 -> "BadGateway";
            default -> ex != null ? ex.getClass().getSimpleName() : null;
        };
    }
}