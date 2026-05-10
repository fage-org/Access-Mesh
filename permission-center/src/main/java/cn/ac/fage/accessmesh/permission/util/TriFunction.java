package cn.ac.fage.accessmesh.permission.util;

/**
 * 三参数函数接口
 * <p>
 * 定义一个接收三个参数并返回结果的函数式接口。
 * 用于需要三个输入参数的函数式场景。
 * </p>
 *
 * @param <T> 第一个参数的类型
 * @param <U> 第二个参数的类型
 * @param <V> 第三个参数的类型
 * @param <R> 返回结果的类型
 */
@FunctionalInterface
public interface TriFunction<T, U, V, R> {
    /**
     * 应用函数逻辑
     * <p>
     * 根据三个输入参数计算并返回结果。
     * </p>
     *
     * @param t 第一个参数
     * @param u 第二个参数
     * @param v 第三个参数
     * @return 函数计算结果
     */
    R apply(T t, U u, V v);
}