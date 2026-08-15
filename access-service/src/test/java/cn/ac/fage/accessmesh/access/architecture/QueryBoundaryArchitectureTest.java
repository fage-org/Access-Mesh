package cn.ac.fage.accessmesh.access.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleName;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleNameEndingWith;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 跨域只读查询边界架构测试（T-ACCESS-006）。
 * <p>
 * 组合查询的跨域数据读取只允许发生在 access.application.query 白名单包：
 * admin 域与 permission 域不得互相访问 Mapper；application 非 query 包（写编排、
 * 门禁）不得直接使用两域 Mapper；query 包 Mapper 接口方法全部以只读前缀命名。
 * 写 SQL 禁令与 tenant_id 显式条件的静态断言见 QueryMapperXmlContractTest。
 * </p>
 */
class QueryBoundaryArchitectureTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_JARS)
            .importPackages("cn.ac.fage.accessmesh.access");
    }

    @Test
    @DisplayName("admin 域不得直接使用 permission 域 Mapper")
    void adminShouldNotUsePermissionMapper() {
        noClasses()
            .that().resideInAPackage("..admin..")
            .should().dependOnClassesThat()
            .resideInAPackage("..permission.mapper..")
            .because("permission 表只允许 permission 域自身与 access.application.query 白名单读取")
            .check(classes);
    }

    @Test
    @DisplayName("permission 域不得直接使用 admin 域 Mapper")
    void permissionShouldNotUseAdminMapper() {
        noClasses()
            .that().resideInAPackage("..permission..")
            .should().dependOnClassesThat()
            .resideInAPackage("..admin.mapper..")
            .because("admin 表只允许 admin 域自身与 access.application.query 白名单读取")
            .check(classes);
    }

    @Test
    @DisplayName("application 非 query 包（写编排/门禁）不得直接使用两域 Mapper")
    void applicationNonQueryShouldNotUseDomainMappers() {
        noClasses()
            .that().resideInAPackage("..application..")
            .and().resideOutsideOfPackage("..application.query..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("..admin.mapper..", "..permission.mapper..")
            .because("组合查询数据读取集中在 application.query；写编排经 DomainService，门禁经引擎")
            .check(classes);
    }

    @Test
    @DisplayName("query 包 Mapper 接口方法全部以只读前缀命名（select/count）")
    void queryMapperMethodsAreReadOnly() {
        methods()
            .that().areDeclaredInClassesThat()
            .resideInAPackage("..application.query.mapper..")
            .should().haveNameStartingWith("select")
            .orShould().haveNameStartingWith("count")
            .orShould().haveNameStartingWith("list")
            .because("专用 QueryMapper 只包含 SELECT，禁止写 SQL（写禁令的 XML 静态断言见 QueryMapperXmlContractTest）")
            .check(classes);
    }

    @Test
    @DisplayName("query 包不依赖两域实体/Mapper（数据经专用 QueryMapper 直读表）")
    void queryPackageDoesNotUseDomainMappersOrEntities() {
        noClasses()
            .that().resideInAPackage("..application.query..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("..admin.mapper..", "..permission.mapper..", "..admin.entity..", "..permission.entity..")
            .because("query 包经专用 QueryMapper（XML 直读表）读取，不直接操纵领域实体或领域 Mapper")
            .check(classes);
    }

    @Test
    @DisplayName("query 包不得依赖 permission 域除 PermissionViewAppService 外的 AppService（写/管理入口）")
    void queryPackageAppServiceWhitelist() {
        noClasses()
            .that().resideInAPackage("..application.query..")
            .should().dependOnClassesThat(
                resideInAPackage("..permission.service..")
                    .and(simpleNameEndingWith("AppService"))
                    .and(not(simpleName("PermissionViewAppService"))))
            .because("query 包获取权限事实仅限只读入口 PermissionViewAppService（TypeResolutionService/PermQueryEngine 为解析/引擎允许项，不属 AppService 后缀）")
            .check(classes);
    }
}
