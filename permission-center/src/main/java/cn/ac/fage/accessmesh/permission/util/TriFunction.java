package cn.ac.fage.accessmesh.permission.util;

/**
 * A functional interface for a function that takes three arguments and produces a result.
 *
 * @param <T> the type of the first argument
 * @param <U> the type of the second argument
 * @param <V> the type of the third argument
 * @param <R> the type of the result
 */
@FunctionalInterface
public interface TriFunction<T, U, V, R> {
    R apply(T t, U u, V v);
}
