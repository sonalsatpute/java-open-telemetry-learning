package com.sonalsatpute.observability.hosting;

import java.util.List;

public final class MetricsConstants
{
    // Follows boundaries from http.server.request.duration/http.client.request.duration
    public static final List<Double> ShortSecondsBucketBoundaries =
            List.of(0.005, 0.01, 0.025, 0.05, 0.075, 0.1, 0.25, 0.5, 0.75, 1.0, 2.5, 5.0, 7.5, 10.0);

    // Not based on a standard. Larger bucket sizes for longer lasting operations, e.g. HTTP connection duration.
    // See https://github.com/open-telemetry/semantic-conventions/issues/336
    public static final List<Double> LongSecondsBucketBoundaries =
            List.of(0.01, 0.02, 0.05, 0.1, 0.2, 0.5, 1.0, 2.0, 5.0, 10.0, 30.0, 60.0, 120.0, 300.0);
}
