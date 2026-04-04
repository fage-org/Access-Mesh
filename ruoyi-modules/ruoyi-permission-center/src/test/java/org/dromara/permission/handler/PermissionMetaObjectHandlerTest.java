package org.dromara.permission.handler;

import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.dromara.permission.domain.PermissionBaseEntity;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@Tag("dev")
class PermissionMetaObjectHandlerTest {

    private final PermissionMetaObjectHandler handler = new PermissionMetaObjectHandler();

    static class TestEntity extends PermissionBaseEntity {
        private Long id;
        private String name;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    static class NonPermissionEntity {
        private Long id;
        private LocalDateTime createdAt;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    }

    @Test
    @Disabled("strictInsertFill 依赖 MyBatis-Plus TableInfo，单测无 Mapper 上下文会 NPE，请在集成测试或实际 Mapper 插入时验证")
    void insertFill_permissionBaseEntity_fillsAuditFields() {
        TestEntity entity = new TestEntity();
        entity.setName("test");

        MetaObject metaObject = SystemMetaObject.forObject(entity);
        handler.insertFill(metaObject);

        assertNotNull(entity.getCreatedAt());
        assertNotNull(entity.getUpdatedAt());
        assertTrue(entity.getCreatedAt().isBefore(LocalDateTime.now().plusSeconds(1)));
    }

    @Test
    @Disabled("strictUpdateFill 依赖 MyBatis-Plus TableInfo，单测无 Mapper 上下文会 NPE，请在集成测试或实际 Mapper 更新时验证")
    void updateFill_permissionBaseEntity_fillsUpdateFields() {
        TestEntity entity = new TestEntity();
        entity.setId(1L);
        LocalDateTime yesterday = LocalDateTime.now().minusDays(1);
        entity.setCreatedAt(yesterday);

        MetaObject metaObject = SystemMetaObject.forObject(entity);
        handler.updateFill(metaObject);

        assertNotNull(entity.getUpdatedAt());
        assertTrue(entity.getUpdatedAt().isAfter(yesterday));
    }

    @Test
    void insertFill_nonPermissionBaseEntity_doesNotFill() {
        NonPermissionEntity entity = new NonPermissionEntity();
        entity.setId(1L);

        MetaObject metaObject = SystemMetaObject.forObject(entity);
        handler.insertFill(metaObject);

        assertNull(entity.getCreatedAt());
    }
}
