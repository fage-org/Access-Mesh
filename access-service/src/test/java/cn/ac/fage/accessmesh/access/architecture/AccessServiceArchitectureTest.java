package cn.ac.fage.accessmesh.access.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * access-service 架构边界测试（T-ACCESS-033 按 capability-structure §8.4 重判，能力口径）。
 * <p>
 * 旧「admin↔permission 互不依赖」族四条规则（adminShouldNotDependOnPermission /
 * adminServiceImplShouldNotDependOnPermission / permissionShouldNotDependOnAdmin /
 * applicationMayDependOnBothDomains）已删除——能力包合一后 AppService/DomainService
 * 同层跨能力依赖为既有形态（project-rules §8.2、capability-structure §2.4），域互斥断言对象不复存在。
 * </p>
 * <p>
 * 保留并重判两条：
 * ① Mapper 数据边界——与 {@link QueryBoundaryArchitectureTest} 同一规则文本
 * （§8.4「同断言两处引用同一规则文本」）；
 * ② bootstrapSeedWriterIsBootstrapOnly——排除集随裁决 6 收敛为「bootstrap 包 + architecture 测试包」。
 * </p>
 */
class AccessServiceArchitectureTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_JARS)
            .importPackages("cn.ac.fage.accessmesh.access");
    }

    @Test
    @DisplayName("Mapper 数据边界与 QueryBoundary 同源复检（同一规则文本，两处引用）")
    void mapperDataBoundaryIsSameSourceAsQueryBoundary() {
        QueryBoundaryArchitectureTest.checkCapabilityMapperBoundary(classes);
    }

    @Test
    @DisplayName("BootstrapSeedWriter 仅允许 bootstrap 包与 architecture 测试包依赖（无操作者写入入口防扩散）")
    void bootstrapSeedWriterIsBootstrapOnly() {
        noClasses()
            .that().resideOutsideOfPackage("..access.bootstrap..")
            .and().resideOutsideOfPackage("..access.architecture..")
            .should().dependOnClassesThat()
            .haveNameMatching(".*BootstrapSeedWriter(Impl)?")
            .because("BootstrapSeedWriter 是 bootstrap 专用的无操作者写入组件"
                + "（T-ACCESS-020，architecture §14.2；T-ACCESS-032 裁决 6 独立顶层 bootstrap 包后"
                + "排除集收敛为本包——bootstrap 编排与其实现同包，其他任何代码不得依赖，"
                + "防止绕过操作者权限校验的写入扩散）")
            .check(classes);
    }

    @Test
    @DisplayName("负向自证：去掉 bootstrap 排除集后依赖必被拒绝（规则有牙）")
    void bootstrapOnlyRuleRejectsOutsideDependency() {
        // bootstrap 包外存在 BootstrapSeedWriter 依赖者（LocalProjectionDomainServiceImpl 等历史消费方
        // 若回流），裸规则（无排除集）对当前依赖树必须可拒绝——把排除集收窄为仅 architecture 测试包时，
        // bootstrap 包内自身对 BootstrapSeedWriter 的依赖即构成违规，规则必须抛错。
        assertThatThrownBy(() -> noClasses()
            .that().resideOutsideOfPackage("..access.architecture..")
            .should().dependOnClassesThat()
            .haveNameMatching(".*BootstrapSeedWriter(Impl)?")
            .check(classes))
            .isInstanceOf(AssertionError.class);
    }
}
