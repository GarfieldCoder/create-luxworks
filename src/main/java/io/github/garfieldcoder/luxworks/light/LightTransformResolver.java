package io.github.garfieldcoder.luxworks.light;

/**
 * Resolves a fixture-specific source into a renderer-ready world transform.
 */
@FunctionalInterface
public interface LightTransformResolver<S> {
    LightTransform resolve(S source);
}
