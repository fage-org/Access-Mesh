package cn.ac.fage.accessmesh.common.cache;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TypeRef 单元测试
 */
class TypeRefTest {

    @Test
    void getType_shouldCaptureSimpleType() {
        TypeRef<String> typeRef = new TypeRef<String>() {};

        assertEquals(String.class, typeRef.getType().getRawClass());
    }

    @Test
    void getType_shouldCaptureGenericSetType() {
        TypeRef<java.util.Set<Long>> typeRef = new TypeRef<java.util.Set<Long>>() {};

        assertEquals(java.util.Set.class, typeRef.getType().getRawClass());
        assertEquals(Long.class, typeRef.getType().getContentType().getRawClass());
    }

    @Test
    void getType_shouldCaptureGenericMapType() {
        TypeRef<java.util.Map<String, Integer>> typeRef = new TypeRef<java.util.Map<String, Integer>>() {};

        assertEquals(java.util.Map.class, typeRef.getType().getRawClass());
        assertEquals(String.class, typeRef.getType().getKeyType().getRawClass());
        assertEquals(Integer.class, typeRef.getType().getContentType().getRawClass());
    }

    @Test
    void getType_shouldCaptureNestedGenericListType() {
        TypeRef<java.util.List<java.util.Map<String, Long>>> typeRef = new TypeRef<java.util.List<java.util.Map<String, Long>>>() {};

        assertEquals(java.util.List.class, typeRef.getType().getRawClass());
        assertEquals(java.util.Map.class, typeRef.getType().getContentType().getRawClass());
    }
}