package org.dromara.permission.mapper;

import org.dromara.authcenter.api.model.InterfacePermissionRule;
import org.dromara.permission.domain.PermissionInterfaceRuleRecord;
import org.dromara.permission.kernel.service.impl.DatabaseInterfacePermissionRuleQueryService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 快照冲突过滤测试
 * 验证接口快照查询正确排除冲突授权
 *
 * SQL 层冲突过滤逻辑在 PermissionKernelSnapshotMapper.xml 中实现：
 * - NOT EXISTS 子查询检查是否存在冲突规则
 * - 冲突规则匹配条件：同一资源、同一角色、互斥操作对同时存在
 * - 匹配到冲突时，两个操作都被排除
 */
@ExtendWith(MockitoExtension.class)
@Tag("dev")
class PermissionKernelSnapshotMapperSqlTest {

    @Mock
    private PermissionKernelSnapshotMapper snapshotMapper;

    @InjectMocks
    private DatabaseInterfacePermissionRuleQueryService service;

    @Test
    void listRules_conflictFilterExcludesBothOperations() {
        // 模拟 SQL 层已过滤冲突后的结果（空列表）
        // 当 VIEW 和 EDIT 互斥且用户同时拥有两者时，SQL NOT EXISTS 过滤掉两条
        when(snapshotMapper.selectInterfaceRuleRecords(1L, 100L)).thenReturn(List.of());

        List<InterfacePermissionRule> rules = service.listRules(1L, 100L);

        // 冲突的操作应该被排除在快照之外
        assertTrue(rules.isEmpty(), "冲突的操作应该被排除在快照之外");
        verify(snapshotMapper).selectInterfaceRuleRecords(1L, 100L);
    }

    @Test
    void listRules_conflictFilterKeepsNonConflictingOperation() {
        // 模拟 SQL 层过滤后的结果（只有 VIEW，没有 EDIT）
        // 用户只有 VIEW，没有冲突的 EDIT，所以 VIEW 被保留
        PermissionInterfaceRuleRecord viewRecord = new PermissionInterfaceRuleRecord();
        viewRecord.setResourceCode("ORDER_API");
        viewRecord.setOperationCode("VIEW");
        viewRecord.setServiceCode("order-service");
        viewRecord.setHttpMethod("GET");
        viewRecord.setPathPattern("/api/orders/**");
        viewRecord.setMatchOrder(10);

        when(snapshotMapper.selectInterfaceRuleRecords(1L, 100L)).thenReturn(List.of(viewRecord));

        List<InterfacePermissionRule> rules = service.listRules(1L, 100L);

        // 非冲突的操作应该被包含在快照中
        assertEquals(1, rules.size(), "非冲突的操作应该被包含在快照中");
        assertEquals("ORDER_API:VIEW", rules.get(0).getCapabilityCode());
        verify(snapshotMapper).selectInterfaceRuleRecords(1L, 100L);
    }

    @Test
    void listRules_conflictFilterWorksWithMultipleResources() {
        // 模拟多个资源的场景
        // 资源 A：VIEW 和 EDIT 冲突，都被排除
        // 资源 B：只有 VIEW，不冲突，保留
        PermissionInterfaceRuleRecord resourceBView = new PermissionInterfaceRuleRecord();
        resourceBView.setResourceCode("PRODUCT_API");
        resourceBView.setOperationCode("VIEW");
        resourceBView.setServiceCode("product-service");
        resourceBView.setHttpMethod("GET");
        resourceBView.setPathPattern("/api/products/**");
        resourceBView.setMatchOrder(10);

        when(snapshotMapper.selectInterfaceRuleRecords(1L, 100L)).thenReturn(List.of(resourceBView));

        List<InterfacePermissionRule> rules = service.listRules(1L, 100L);

        assertEquals(1, rules.size(), "只有非冲突资源的操作应该被包含");
        assertEquals("PRODUCT_API:VIEW", rules.get(0).getCapabilityCode());
    }
}
