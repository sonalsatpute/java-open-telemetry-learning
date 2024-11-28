package com.sonalsatpute.http_metrics.configurations;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.MeterProvider;
import io.opentelemetry.api.common.Attributes;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import java.util.List;


@Component
public class MetricsInterceptor implements HandlerInterceptor {

    // Helper constants
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

    static {
        environmentAttributes = Attributes.of(
                ENVIRONMENT, "local",
                SCALE_UNIT_ID, "development",
                NAMESPACE_NAME, "local-development");
    }

    // Helper function


    private final Meter meter;

    public MetricsInterceptor(MeterProvider meterProvider) {
        this.meter = meterProvider.get("http.server.request");
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        request.setAttribute("startTime", System.currentTimeMillis());
        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
        // Implementation can be added if needed
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        long startTime = (long) request.getAttribute("startTime");
        long duration = System.currentTimeMillis() - startTime;

        DoubleHistogram histogram = meter.histogramBuilder("http.server.request.duration")
                .setDescription("Request duration in milliseconds")
                .setUnit("ms")
                .setExplicitBucketBoundariesAdvice(List.of(0.005d, 0.01d, 0.025d, 0.05d, 0.075d, 0.1d, 0.25d, 0.5d, 0.75d, 1d, 2.5d, 5d, 7.5d, 10d))
                .build();

        System.out.println("Duration: " + duration + "ms");

        histogram.record(duration, httpAttributes(request, response, getErrorType(response, ex)));
    }

    private Attributes httpAttributes(
            HttpServletRequest request,
            HttpServletResponse response,
            String errorType
    ) {
        return Attributes.builder()
                .put(HTTP_REQUEST_METHOD, request.getMethod())
                .put(HTTP_ROUTE, request.getRequestURI())
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
        String errorType = null;

        if (ex != null) {
            errorType = ex.getClass().getSimpleName();
        } else if (response.getStatus() == 500) {
            errorType = "InternalServerError";
        } else if (response.getStatus() == 408) {
            errorType = "Timeout";
        } else if (response.getStatus() == 404) {
            errorType = "NameResolutionError";
        } else if (response.getStatus() == 400) {
            errorType = "BadRequest";
        } else if (response.getStatus() == 401) {
            errorType = "Unauthorized";
        } else if (response.getStatus() == 403) {
            errorType = "Forbidden";
        } else if (response.getStatus() == 429) {
            errorType = "RateLimitExceeded";
        } else if (response.getStatus() == 503) {
            errorType = "ServiceUnavailable";
        } else if (response.getStatus() == 504) {
            errorType = "GatewayTimeout";
        } else if (response.getStatus() == 502) {
            errorType = "BadGateway";
        }
        return errorType;
    }
}