package cn.ac.fage.accessmesh.access.permission.cache;

import cn.ac.fage.accessmesh.access.permission.entity.OperationPermission;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OperationPermission 缓存 wire format 锁定（OPERATION_PERMISSIONS_BY_TYPE 目录，实体 Map 值）。
 * <p>
 * getEffectiveBits() 是计算型 getter，无 @JsonIgnore 时 Jackson 会把 effectiveBits 写进缓存值，
 * 读回时按未知属性抛 UnrecognizedPropertyException（缓存读链路事故的回归锁——旧实现下本用例
 * 在序列化断言与反序列化两处均失败）。缓存序列化用独立 ObjectMapper（非 Spring 容器实例），
 * FAIL_ON_UNKNOWN_PROPERTIES 保持默认开启，与本用例一致。
 */
class OperationPermissionCacheWireFormatTest {

    @Test
    void effectiveBitsExcludedFromWireFormatAndRoundTripSucceeds() throws Exception {
        OperationPermission op = new OperationPermission();
        op.setId(7L);
        op.setTenantId(1L);
        op.setResourceType(2);
        op.setCode("VIEW");
        op.setName("查看");
        op.setBinaryBit(1L);
        op.setInheritMask(4L);

        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(op);

        assertThat(json).doesNotContain("effectiveBits");

        OperationPermission back = mapper.readValue(json, OperationPermission.class);
        assertThat(back.getCode()).isEqualTo("VIEW");
        assertThat(back.getBinaryBit()).isEqualTo(1L);
        assertThat(back.getInheritMask()).isEqualTo(4L);
        assertThat(back.getEffectiveBits()).isEqualTo(5L);
    }
}
