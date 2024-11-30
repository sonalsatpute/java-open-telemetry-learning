package com.sonalsatpute.http_metrics.interceptors;

import com.sonalsatpute.http_metrics.hosting.HostingMetrics;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.common.Attributes;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

@Component
public class MetricsInterceptor implements HandlerInterceptor {

    private static final String REQUEST_START_TIME = "request_start_time";
    private static final String REQUEST_ROUTE_TEMPLATE = "request_route_template";

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

    private final HostingMetrics hostingMetrics;

    public MetricsInterceptor(HostingMetrics hostingMetrics){
        this.hostingMetrics = hostingMetrics;
    }


    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute(REQUEST_START_TIME, System.currentTimeMillis());
        request.setAttribute(REQUEST_ROUTE_TEMPLATE, request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE));

        this.hostingMetrics.requestStart(request.getScheme(), request.getMethod(), httpAttributes(request));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        long startTime = (long) request.getAttribute(REQUEST_START_TIME);
        String routeTemplate = (String) request.getAttribute(REQUEST_ROUTE_TEMPLATE);

        this.hostingMetrics.requestEnd(
                request.getProtocol(),
                request.getScheme(),
                request.getMethod(),
                routeTemplate,
                response.getStatus(),
                request.getHeader(USER_AGENT_HEADER),
                ex,
                httpAttributes(request),
                startTime,
                System.currentTimeMillis(),
                false
        );
    }

    private Attributes httpAttributes(HttpServletRequest request) {
        return Attributes.builder()
                .putAll(environmentAttributes)
                .put(TENANT_ID, getHeaderLongValue(request, TENANT_ID_HEADER))
                .put(SITE_ID, getHeaderLongValue(request, SITE_ID_HEADER))
                .putAll(buildQueryStringAttributes(request))
                .build();
    }

    private static Long getHeaderLongValue(HttpServletRequest request, String headerName) {
        String headerValue = request.getHeader(headerName);
        if (headerValue == null) {
            return null;
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
}