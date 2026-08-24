package cn.ac.fage.accessmesh.access.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * access-service 架构边界测试骨架。
 * <p>
 * 后续任务（T-ACCESS-006 等）可在此扩展依赖白名单和跨域调用规则。
 * 当前仅验证核心边界：admin 与 permission 域不直接互相依赖实现类。
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
    @DisplayName("admin 域不得依赖 permission 域的实现类")
    void adminShouldNotDependOnPermission() {
        noClasses()
            .that().resideInAPackage("..admin..")
            .and().resideOutsideOfPackage("..admin.controller..")
            .should().dependOnClassesThat()
            .resideInAPackage("..permission..")
            .because("admin 与 permission 禁止横向调用，跨域编排必须通过 access.application")
            .check(classes);
    }

    @Test
    @DisplayName("admin.service.impl 不得依赖 permission 包")
    void adminServiceImplShouldNotDependOnPermission() {
        noClasses()
            .that().resideInAPackage("..admin.service.impl..")
            .should().dependOnClassesThat()
            .resideInAPackage("..permission..")
            .because("admin AppService 不得直连 permission，写编排走 access.application")
            .check(classes);
    }

    @Test
    @DisplayName("permission 域不得依赖 admin 域的实现类")
    void permissionShouldNotDependOnAdmin() {
        noClasses()
            .that().resideInAPackage("..permission..")
            .should().dependOnClassesThat()
            .resideInAPackage("..admin..")
            .because("admin 与 permission 禁止横向调用，跨域编排必须通过 access.application")
            .check(classes);
    }

    @Test
    @DisplayName("application 可依赖两域 DomainService，admin.service.impl 不得依赖 permission")
    void applicationMayDependOnBothDomains() {
        noClasses()
            .that().resideInAPackage("..admin.service.impl..")
            .should().dependOnClassesThat()
            .resideInAPackage("..permission..")
            .check(classes);
        noClasses()
            .that().resideInAPackage("..permission.service.impl..")
            .should().dependOnClassesThat()
            .resideInAPackage("..admin..")
            .check(classes);
    }

    @Test
    @DisplayName("BootstrapSeedWriter 仅允许 bootstrap 编排与所属领域 impl 包依赖（无操作者写入入口防扩散）")
    void bootstrapSeedWriterIsBootstrapOnly() {
        noClasses()
            .that().resideOutsideOfPackage("..application.bootstrap..")
            .and().resideOutsideOfPackage("..permission.service.domain.impl..")
            .and().resideOutsideOfPackage("..architecture..")
            .should().dependOnClassesThat()
            .haveNameMatching(".*BootstrapSeedWriter(Impl)?")
            .because("BootstrapSeedWriter 是包内可见、bootstrap 专用的无操作者写入组件"
                + "（T-ACCESS-020，architecture §14.2）——除 bootstrap initializer 与实现落位的"
                + " domain.impl 包外任何代码（含 permission 领域其他 DomainService）不得依赖，"
                + "防止绕过操作者权限校验的写入扩散")
            .check(classes);
    }
}
