package cn.ac.fage.accessmesh.access.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 能力包数据边界架构测试（T-ACCESS-033 按 capability-structure §8.4 重建，能力口径）。
 * <p>
 * 断言面 = mapper 包：能力包类不得依赖<b>其他能力包</b>的 mapper 包（12 能力包全组合，
 * <b>零容忍无白名单</b>——Q-009 收敛完成后终态，T-ACCESS-043~046 四批将 2026-09-13 冻结的
 * 19 类 30 边全量收敛为零；冻结期历史基线见 capability-structure §8.4 豁免 6 表）。
 * 跨能力实体/Service/DTO import 为既有普遍形态、不在断言面（§8.4 规则 1 口径）。
 * engine / projection / bootstrap / sync 非能力包，其豁免面见 §8.4 豁免 1-4，不在本规则对象内。
 * </p>
 * <p>
 * 另承载：① {@code *QueryMapper} 按类名匹配的只读前缀规则（§8.4 规则 3，
 * 整包规则随 application.query 解散而退役）；② 落位兜底断言——主源码全部类必须落在
 * 17 顶层包、根包唯一例外为启动类（§8.4 规则 4，外评补充）。
 * </p>
 */
class QueryBoundaryArchitectureTest {

    private static final String BASE = "cn.ac.fage.accessmesh.access";

    /** 12 能力包（§8.1；sync/engine/projection/bootstrap/infrastructure 非能力包不在此列） */
    static final List<String> CAPABILITY_PACKAGES = List.of(
        "auth", "user", "org", "menu", "role", "grant",
        "resource", "type", "domain", "rule", "audit", "platform");

    /** 17 顶层包（落位兜底断言的允许集合；§8.1） */
    static final List<String> TOP_LEVEL_PACKAGES = List.of(
        "auth", "user", "org", "menu", "role", "grant",
        "resource", "type", "domain", "rule", "audit", "platform",
        "sync", "engine", "projection", "bootstrap", "infrastructure");

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_JARS)
            .importPackages(BASE);
    }

    /**
     * 能力包 Mapper 数据边界（§8.4 规则 1，12 能力包全组合，零容忍无白名单）。
     * <p>
     * 实现=字节码级依赖遍历（{@code getDirectDependenciesFromSelf} 覆盖 import 行与内联 FQCN
     * 全形态）：消费方能力包 → 他能力包 {@code {cap}.mapper} 的任何依赖即违规
     * （Q-009 收敛完成，冻结白名单退役为零容忍——无任何可注入豁免通道）。
     * 供 {@link AccessServiceArchitectureTest} 以同一规则文本复检（§8.4「同断言两处引用同一规则文本」）。
     * </p>
     */
    static void checkCapabilityMapperBoundary(JavaClasses imported) {
        Set<String> violations = new java.util.TreeSet<>();
        for (JavaClass clazz : imported) {
            String consumerCap = capabilityOf(clazz.getPackageName());
            if (consumerCap == null) {
                continue;
            }
            for (com.tngtech.archunit.core.domain.Dependency dep : clazz.getDirectDependenciesFromSelf()) {
                JavaClass target = dep.getTargetClass();
                String targetCap = mapperCapabilityOf(target.getPackageName());
                if (targetCap == null || targetCap.equals(consumerCap)) {
                    continue;
                }
                violations.add(outerClassName(clazz.getFullName()) + " -> " + outerClassName(target.getFullName())
                    + "（" + consumerCap + " 直读 " + targetCap + " mapper）");
            }
        }
        assertThat(violations).as("能力包互读他包 mapper（Q-009 收敛完成，零容忍无白名单）").isEmpty();
    }

    /** FQCN 包 → 所属能力包（12 能力包之一，否则 null） */
    private static String capabilityOf(String pkg) {
        String first = firstSegment(pkg);
        return first != null && CAPABILITY_PACKAGES.contains(first) ? first : null;
    }

    /** FQCN 包 → 其所属能力包（仅当包形如 {@code {cap}.mapper} 精确一层；否则 null） */
    private static String mapperCapabilityOf(String pkg) {
        String prefix = BASE + ".";
        if (!pkg.startsWith(prefix)) {
            return null;
        }
        String rest = pkg.substring(prefix.length());
        int firstDot = rest.indexOf('.');
        if (firstDot < 0 || !"mapper".equals(rest.substring(firstDot + 1))) {
            return null;
        }
        String first = rest.substring(0, firstDot);
        return CAPABILITY_PACKAGES.contains(first) ? first : null;
    }

    /** 嵌套类 FQCN（Outer$Inner）归一为外部类（无 $ 则原样） */
    private static String outerClassName(String fullName) {
        int dollar = fullName.indexOf('$');
        return dollar < 0 ? fullName : fullName.substring(0, dollar);
    }

    private static String firstSegment(String pkg) {
        String prefix = BASE + ".";
        if (!pkg.startsWith(prefix)) {
            return null;
        }
        String rest = pkg.substring(prefix.length());
        int firstDot = rest.indexOf('.');
        return firstDot < 0 ? rest : rest.substring(0, firstDot);
    }

    @Test
    @DisplayName("能力包不得直读其他能力包 mapper（全组合零容忍，Q-009 白名单已退役）")
    void capabilityPackagesMustNotReadOtherCapabilityMappers() {
        checkCapabilityMapperBoundary(classes);
    }

    @Test
    @DisplayName("*QueryMapper 接口方法只读前缀（按类名匹配，非整包——§8.4 规则 3）")
    void queryMapperMethodsAreReadOnly() {
        methods()
            .that().areDeclaredInClassesThat()
            .haveSimpleNameEndingWith("QueryMapper")
            .should().haveNameStartingWith("select")
            .orShould().haveNameStartingWith("count")
            .orShould().haveNameStartingWith("list")
            .because("专用 QueryMapper 只包含 SELECT，禁止写 SQL（写禁令的 XML 静态断言见 QueryMapperXmlContractTest；"
                + "按类名匹配避免误杀同包写 Mapper——§8.4 规则 3）")
            .check(classes);
    }

    @Test
    @DisplayName("落位兜底：主源码全部类落在 17 顶层包（§8.4 规则 4，防漏行类静默残留旧包）")
    void allClassesMustResideInTopLevelPackages() {
        String[] allowed = TOP_LEVEL_PACKAGES.stream()
            .map(p -> "..access." + p + "..").toArray(String[]::new);
        classes()
            .that().resideInAPackage("..access..")
            .and().doNotHaveFullyQualifiedName(BASE + ".AccessServiceApplication")
            .should().resideInAnyPackage(allowed)
            .because("T-ACCESS-033 搬包后主源码只允许 17 顶层包（§8.1），"
                + "残留旧包（admin/permission/application）即漏迁")
            .check(classes);
    }

    @Test
    @DisplayName("落位兜底（反向锁）：根包仅允许启动类存在")
    void rootPackageContainsOnlyApplicationClass() {
        classes()
            .that().resideInAPackage(BASE)
            .should().haveFullyQualifiedName(BASE + ".AccessServiceApplication")
            .because("根包唯一例外=启动类（architecture §2 锚点，e2e 以 FQCN 字符串引用）；"
                + "任何其他类落在根包即漏迁")
            .check(classes);
    }

    /**
     * QueryGate 薄门面约束（T-PERM-089，设计 §9.4）：getDenied 等四方法可留作薄门面，
     * 但门面仅依赖新执行器——禁止注入权限 Mapper、解析角色或调用条件/互斥服务
     * （依赖蔓延即门面退化为第二执行体，违反唯一执行主体）。
     */
    @Test
    @DisplayName("QueryGate 薄门面约束：仅依赖新执行器与基础设施工具，禁止 Mapper/DomainService 依赖")
    void queryGateMustRemainThinFacadeOverQueryExecutionEngine() {
        JavaClass gate = classes.stream()
            .filter(clazz -> clazz.getFullName().equals(BASE + ".engine.query.QueryGate"))
            .findFirst().orElseThrow(() -> new AssertionError("engine.query.QueryGate 未在主源码中找到"));
        Set<String> violations = new java.util.TreeSet<>();
        for (com.tngtech.archunit.core.domain.Dependency dep : gate.getDirectDependenciesFromSelf()) {
            String target = dep.getTargetClass().getFullName();
            if (target.startsWith("java.") || target.startsWith("org.springframework.")
                || target.startsWith("cn.ac.fage.accessmesh.access.engine.query.")
                || target.startsWith("cn.ac.fage.accessmesh.access.infrastructure.")) {
                continue;
            }
            violations.add(target);
        }
        assertThat(violations)
            .as("QueryGate 允许依赖面=engine.query 包 + JDK/Spring 基础设施；越界依赖（Mapper/DomainService/其他能力包）即门面增重（设计 §9.4）")
            .isEmpty();
    }

    /**
     * 旧执行体退役锁（T-PERM-092，设计 §9.4 X04）：旧执行体与四旧 DTO 自主源码退出后
     * 不得回潮——任何形态的复活（含改名同形）都意味着第二执行主体或绕过引擎判定面。
     * 供负向自证（夹具占用退役 FQCN）与主扫描面复用同一规则。
     */
    static void checkRetiredClassesAbsent(JavaClasses imported) {
        Set<String> retired = Set.of(
            BASE + ".engine.core.PermQueryEngine",
            BASE + ".engine.core.ResolveContext",
            BASE + ".engine.dto.PermQuery",
            BASE + ".engine.dto.PermResult",
            BASE + ".engine.dto.PermBatchQuery",
            BASE + ".engine.dto.PermBatchResult",
            BASE + ".grant.enums.TargetMode");
        Set<String> present = imported.stream()
            .map(JavaClass::getFullName)
            .filter(retired::contains)
            .collect(java.util.stream.Collectors.toCollection(java.util.TreeSet::new));
        assertThat(present)
            .as("旧执行体/四旧 DTO 已于 T-PERM-092 删除，主源码再现即退役回潮（设计 §9.4）")
            .isEmpty();
    }

    @Test
    @DisplayName("旧执行体退役锁：PermQueryEngine/四旧 DTO/TargetMode/ResolveContext 不得存在于主源码（X04 双向锁）")
    void retiredLegacyEngineClassesMustNotBeReintroduced() {
        checkRetiredClassesAbsent(classes);
    }

    // ------------------------------------------------------------------
    // 负向样例自证（§8.4：每条重建规则以负向样例证明仍能拒绝违规）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("负向自证：夹具违规边必被拒绝（规则有牙；白名单退役后零容忍）")
    void mapperBoundaryRuleRejectsUnwhitelistedEdge() {
        // 白名单退役后无真实存量边可裁剪——以测试源集夹具（menu.fixture.BoundaryViolationFixture，
        // 故意 import role.mapper.UserRoleMapper）经专用 ClassFileImporter 单独导入自证：
        // 零容忍规则必须拒绝该违规边（DO_NOT_INCLUDE_TESTS 使其永不进入主扫描面，双重不干扰）。
        JavaClasses fixture = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_JARS)
            .importPackages(BASE + ".menu.fixture");
        assertThat(fixture.size()).as("夹具类必须被导入").isGreaterThan(0);
        assertThatThrownBy(() -> checkCapabilityMapperBoundary(fixture))
            .isInstanceOf(AssertionError.class)
            .hasMessageContaining("UserRoleMapper");
    }

    @Test
    @DisplayName("负向自证：写方法前缀必被只读前缀规则拒绝")
    void readOnlyPrefixRuleRejectsWriteMethods() {
        // 用已知含写方法的 AbstractRoleMapper（insert/update 等）套用同一前缀规则形态，
        // 规则必须拒绝——证明前缀检测对写方法有牙。
        assertThatThrownBy(() -> methods()
            .that().areDeclaredInClassesThat()
            .haveFullyQualifiedName(BASE + ".role.mapper.AbstractRoleMapper")
            .should().haveNameStartingWith("select")
            .orShould().haveNameStartingWith("count")
            .orShould().haveNameStartingWith("list")
            .check(classes))
            .isInstanceOf(AssertionError.class);
    }

    @Test
    @DisplayName("负向自证：占用退役 FQCN 的夹具类必被退役锁拒绝（X04 锁有牙）")
    void retiredClassLockRejectsFixtureOccupyingRetiredFqcn() {
        // 测试源集夹具（engine.dto.PermResult，故意占用已退役 FQCN）经专用
        // ClassFileImporter 单独导入——退役锁必须拒绝（DO_NOT_INCLUDE_TESTS 使其
        // 永不进入主扫描面，双重不干扰；BoundaryViolationFixture 同款形态）。
        JavaClasses fixture = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_JARS)
            .importPackages(BASE + ".engine.dto");
        assertThat(fixture.size()).as("夹具包必须被导入").isGreaterThan(0);
        assertThatThrownBy(() -> checkRetiredClassesAbsent(fixture))
            .isInstanceOf(AssertionError.class)
            .hasMessageContaining("PermResult");
    }

    @Test
    @DisplayName("负向自证：不在允许集合的包必被落位断言拒绝")
    void residencyRuleRejectsClassesOutsideAllowedPackages() {
        // 把允许集合收窄为单包 user——其余 16 包全部类必被拒绝，证明兜底断言有牙。
        assertThatThrownBy(() -> classes()
            .that().resideInAPackage("..access..")
            .and().doNotHaveFullyQualifiedName(BASE + ".AccessServiceApplication")
            .should().resideInAnyPackage("..access.user..")
            .check(classes))
            .isInstanceOf(AssertionError.class);
    }

}
