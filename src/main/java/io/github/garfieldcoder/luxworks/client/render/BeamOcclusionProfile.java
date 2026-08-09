package io.github.garfieldcoder.luxworks.client.render;

/** Cached light-ray distances for concentric angular samples of a cone. */
public record BeamOcclusionProfile(double[][] distances) {
    public double distance(int ring, int segment) {
        return distances[ring][segment];
    }

    public static BeamOcclusionProfile interpolate(
            BeamOcclusionProfile previous,
            BeamOcclusionProfile current,
            double progress
    ) {
        double clamped = Math.clamp(progress, 0.0, 1.0);
        double[][] result = new double[current.distances.length][];
        for (int ring = 0; ring < current.distances.length; ring++) {
            result[ring] = new double[current.distances[ring].length];
            for (int segment = 0; segment < current.distances[ring].length; segment++) {
                double from = previous.distance(ring, segment);
                result[ring][segment] = from + (current.distance(ring, segment) - from) * clamped;
            }
        }
        return new BeamOcclusionProfile(result);
    }
}
