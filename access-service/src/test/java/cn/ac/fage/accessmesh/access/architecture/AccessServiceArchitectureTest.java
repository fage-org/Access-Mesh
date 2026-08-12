package cn.ac.fage.accessmesh.access.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
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
            .should().dependOnClassesThat()
            .resideInAPackage("..permission..")
            .because("admin 与 permission 禁止横向调用，跨域编排必须通过 access.application")
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
    @DisplayName("admin 与 permission 各自为独立切片，不循环依赖")
    void slicesShouldNotBeCyclic() {
        slices()
            .matching("cn.ac.fage.accessmesh.access.(*)..")
            .should().beFreeOfCycles()
            .check(classes);
    }
}
