package cn.ac.fage.accessmesh.access.bootstrap;

import cn.ac.fage.accessmesh.access.type.enums.ResourceTypeCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * bootstrap 固定图 USER 段形状锁定（T-ACCESS-034）。
 * <p>
 * USER 轨细粒度化的配套断言：GrantSpec 集合零变更（USER 段维持
 * CREATE/VIEW/UPDATE/DELETE/ENABLE/RESET_PASSWORD 六码、无 MANAGE 行）。
 * 固定图本无 USER:MANAGE 位——旧实现下空库 perm 轨 update/remove 恒拒不可用
 * （死锁），换绑 UPDATE/DELETE/ENABLE 后由拒转放行为预期行为变化；本断言钉死
 * 「固定图不因换绑补 MANAGE 位」的边界（补位会静默恢复粗粒度门禁语义）。
 * </p>
 */
class BootstrapGraphUserSectionTest {

    @Test
    @DisplayName("固定图 USER 意外出现 MANAGE 行，违反换绑边界")
    void userSectionMustStayFineGrainedWithoutManage() {
        List<BootstrapGraphDefinition.GrantSpec> all = Stream.concat(
                BootstrapGraphDefinition.businessGrants().stream(),
                BootstrapGraphDefinition.apiAccessGrants().stream())
            .toList();

        Set<String> userOps = all.stream()
            .filter(g -> ResourceTypeCode.USER.equals(g.resourceTypeCode()))
            .map(BootstrapGraphDefinition.GrantSpec::operationCode)
            .collect(java.util.stream.Collectors.toSet());

        assertThat(userOps)
            .as("USER 段 GrantSpec 集合零变更（T-ACCESS-034）：六细码、无 MANAGE")
            .containsExactlyInAnyOrder("CREATE", "VIEW", "UPDATE", "DELETE", "ENABLE", "RESET_PASSWORD");
    }
}
