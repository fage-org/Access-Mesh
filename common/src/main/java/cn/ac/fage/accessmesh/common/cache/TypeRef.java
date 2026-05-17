package cn.ac.fage.accessmesh.common.cache;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 泛型类型捕获工具类
 * <p>
 * 用于捕获复杂泛型类型信息，解决 Java 泛型类型擦除问题。
 * 类似于 Jackson 的 TypeReference，但更简洁。
 * </p>
 *
 * <h3>使用示例：</h3>
 * <pre>{@code
 * // 捕获 Set<Long> 类型
 * TypeRef<Set<Long>> typeRef = new TypeRef<Set<Long>>() {};
 * JavaType javaType = typeRef.getType();
 *
 * // 捕获 Map<String, Integer> 类型
 * TypeRef<Map<String, Integer>> typeRef = new TypeRef<Map<String, Integer>>() {};
 * }</pre>
 *
 * <p><b>注意：</b>必须通过匿名子类使用（{@code new TypeRef<T>() {}}），不能直接实例化。</p>
 *
 * @param <T> 要捕获的泛型类型
 */
public abstract class TypeRef<T> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final JavaType type;

    /**
     * 构造 TypeRef，通过反射捕获泛型类型
     */
    protected TypeRef() {
        this.type = MAPPER.constructType(
            ((java.lang.reflect.ParameterizedType) getClass().getGenericSuperclass())
                .getActualTypeArguments()[0]
        );
    }

    /**
     * 获取捕获的 JavaType
     *
     * @return Jackson JavaType 对象，可用于 JSON 序列化/反序列化
     */
    public JavaType getType() {
        return type;
    }

    /**
     * 获取原始 Class 对象（如果类型是简单类）
     *
     * @return Class 对象，如果类型是参数化类型则返回 null
     */
    public Class<?> getRawClass() {
        return type.getRawClass();
    }
}