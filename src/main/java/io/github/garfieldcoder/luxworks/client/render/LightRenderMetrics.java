package io.github.garfieldcoder.luxworks.client.render;

import io.github.garfieldcoder.luxworks.Luxworks;

/**
 * Early CPU-side rendering counters. These measure submission work on the
 * render thread, not time spent executing the shader on the GPU.
 */
public final class LightRenderMetrics {
    private static final long REPORT_INTERVAL_NANOS = 5_000_000_000L;

    private static long reportStartedAt = System.nanoTime();
    private static long sampledFrames;
    private static long submittedBeams;
    private static long submittedVertices;
    private static long totalCpuNanos;
    private static long worstCpuNanos;

    private LightRenderMetrics() {
    }

    public static void record(long cpuNanos, int beamCount, int vertexCount) {
        sampledFrames++;
        submittedBeams += beamCount;
        submittedVertices += vertexCount;
        totalCpuNanos += cpuNanos;
        worstCpuNanos = Math.max(worstCpuNanos, cpuNanos);

        long now = System.nanoTime();
        if (now - reportStartedAt < REPORT_INTERVAL_NANOS) {
            return;
        }

        double averageMicros = sampledFrames == 0 ? 0.0 : totalCpuNanos / sampledFrames / 1_000.0;
        double worstMicros = worstCpuNanos / 1_000.0;
        Luxworks.LOGGER.info(
                "Debug beam metrics: {} sampled frames, {} beams, {} vertices, avg CPU {} us, worst CPU {} us",
                sampledFrames,
                submittedBeams,
                submittedVertices,
                String.format("%.2f", averageMicros),
                String.format("%.2f", worstMicros)
        );

        reportStartedAt = now;
        sampledFrames = 0;
        submittedBeams = 0;
        submittedVertices = 0;
        totalCpuNanos = 0;
        worstCpuNanos = 0;
    }
}
