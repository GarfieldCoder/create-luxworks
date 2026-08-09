package io.github.garfieldcoder.luxworks.light;

import java.util.Arrays;

/**
 * Renderer-neutral polar shadow data for one spotlight.
 *
 * <p>Values are normalized visible distances in [0, 1]. Angular samples wrap
 * around the light axis, while radial rings run from the center toward the
 * outer cone. This layout can be uploaded directly as a small GPU texture.</p>
 */
public final class AngularShadowMask {
    private static final double TAU = Math.PI * 2.0;

    private final double[] ringFractions;
    private final float[][] normalizedDistances;

    public AngularShadowMask(double[] ringFractions, float[][] normalizedDistances) {
        if (ringFractions.length == 0 || ringFractions.length != normalizedDistances.length) {
            throw new IllegalArgumentException("Shadow mask rings must be non-empty and dimensionally consistent");
        }
        this.ringFractions = ringFractions.clone();
        this.normalizedDistances = new float[normalizedDistances.length][];
        int segments = normalizedDistances[0].length;
        if (segments < 2) {
            throw new IllegalArgumentException("Shadow mask requires at least two angular samples");
        }
        double previousFraction = -1.0;
        for (int ring = 0; ring < ringFractions.length; ring++) {
            if (!Double.isFinite(ringFractions[ring])
                    || ringFractions[ring] < 0.0
                    || (ring > 0 && ringFractions[ring] <= previousFraction)
                    || ringFractions[ring] > 1.0
                    || normalizedDistances[ring].length != segments) {
                throw new IllegalArgumentException("Invalid shadow mask ring layout");
            }
            previousFraction = ringFractions[ring];
            this.normalizedDistances[ring] = normalizedDistances[ring].clone();
            for (int segment = 0; segment < segments; segment++) {
                float value = this.normalizedDistances[ring][segment];
                this.normalizedDistances[ring][segment] = Float.isFinite(value)
                        ? Math.clamp(value, 0.0F, 1.0F)
                        : 1.0F;
            }
        }
    }

    public static AngularShadowMask fromDistances(
            double[] ringFractions,
            double[][] distances,
            double range
    ) {
        double safeRange = Double.isFinite(range) && range > 0.0 ? range : 1.0;
        float[][] normalized = new float[distances.length][];
        for (int ring = 0; ring < distances.length; ring++) {
            normalized[ring] = new float[distances[ring].length];
            for (int segment = 0; segment < distances[ring].length; segment++) {
                normalized[ring][segment] = (float) (distances[ring][segment] / safeRange);
            }
        }
        return new AngularShadowMask(ringFractions, normalized);
    }

    public float sample(double radialFraction, double angleRadians) {
        double radial = Math.clamp(radialFraction, 0.0, 1.0);
        double wrappedAngle = ((angleRadians % TAU) + TAU) % TAU;
        double angularIndex = wrappedAngle / TAU * segments();
        int firstSegment = (int) Math.floor(angularIndex) % segments();
        int secondSegment = (firstSegment + 1) % segments();
        float angularProgress = (float) (angularIndex - Math.floor(angularIndex));

        int outerRing = 0;
        while (outerRing < rings() - 1 && radial > ringFractions[outerRing]) {
            outerRing++;
        }
        int innerRing = Math.max(0, outerRing - 1);
        float innerValue = angularSample(innerRing, firstSegment, secondSegment, angularProgress);
        if (innerRing == outerRing) {
            return innerValue;
        }
        float outerValue = angularSample(outerRing, firstSegment, secondSegment, angularProgress);
        double radialStart = ringFractions[innerRing];
        double radialEnd = ringFractions[outerRing];
        float radialProgress = (float) ((radial - radialStart) / (radialEnd - radialStart));
        return Math.clamp(innerValue + (outerValue - innerValue) * radialProgress, 0.0F, 1.0F);
    }

    public int rings() {
        return normalizedDistances.length;
    }

    public int segments() {
        return normalizedDistances[0].length;
    }

    public float value(int ring, int segment) {
        return normalizedDistances[ring][segment];
    }

    public double[] ringFractions() {
        return ringFractions.clone();
    }

    private float angularSample(int ring, int first, int second, float progress) {
        float firstValue = normalizedDistances[ring][first];
        return firstValue + (normalizedDistances[ring][second] - firstValue) * progress;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof AngularShadowMask other)) {
            return false;
        }
        return Arrays.equals(ringFractions, other.ringFractions)
                && Arrays.deepEquals(normalizedDistances, other.normalizedDistances);
    }

    @Override
    public int hashCode() {
        return 31 * Arrays.hashCode(ringFractions) + Arrays.deepHashCode(normalizedDistances);
    }
}
