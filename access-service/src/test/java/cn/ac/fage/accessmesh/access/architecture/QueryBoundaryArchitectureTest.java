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
 * 断言面 = mapper 包：能力包类不得依赖<b>其他能力包</b>的 mapper 包（12 能力包全组合），
 * 唯一例外为 2026-09-13 拍板的「存量跨能力 mapper 直读冻结白名单」（T-ACCESS-032 裁决 9）——
 * 断言锁「不得新增」，存量按 Q-009 收敛（capability-mapper-convergence-plan 四批推进，
 * 闭合清单基线见 capability-structure §8.4 豁免 6 表；T-ACCESS-043 批次① 后余 15 类 21 边）。
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

    /**
     * 存量跨能力 mapper 直读冻结白名单（T-ACCESS-032 裁决 9；2026-09-13 拍板、Q-009 收敛中）。
     * <p>
     * 行格式 {消费方 FQCN, 被直读的他能力包 mapper FQCN}。断言语义=「不得新增」：白名单外的
     * 任何能力包间 mapper 依赖即违规。存量收敛（改走被读方 DomainService 封装）按
     * capability-mapper-convergence-plan 四批推进：T-ACCESS-043 批次①已收敛 9 边
     * （30→21 边、19→15 消费类），收敛行随代码同 commit 删除。
     * 采集口径=字节码级全形态（import 行 + 内联 FQCN 字段声明），ArchUnit 字节码分析天然覆盖。
     * </p>
     */
    static final String[][] FROZEN_WHITELIST = {
        // grant（4 类 8 边，批次① 后）
        {BASE + ".grant.service.impl.PermissionGrantAppServiceImpl", BASE + ".rule.mapper.PermissionConditionMapper"},
        {BASE + ".grant.service.impl.PermissionGrantAppServiceImpl", BASE + ".type.mapper.OperationPermissionMapper"},
        {BASE + ".grant.service.domain.impl.PermissionGrantPlanDomainServiceImpl", BASE + ".domain.mapper.DomainConfigMapper"},
        {BASE + ".grant.service.domain.impl.PermissionGrantPlanDomainServiceImpl", BASE + ".rule.mapper.PermissionConditionMapper"},
        {BASE + ".grant.service.domain.impl.PermissionGrantPlanDomainServiceImpl", BASE + ".type.mapper.OperationPermissionMapper"},
        {BASE + ".grant.service.domain.impl.PermissionGrantDomainServiceImpl", BASE + ".type.mapper.OperationPermissionMapper"},
        {BASE + ".grant.service.domain.impl.PermissionGrantDomainServiceImpl", BASE + ".type.mapper.TypeDefinitionMapper"},
        {BASE + ".grant.service.domain.impl.GrantOriginDomainServiceImpl", BASE + ".type.mapper.OperationPermissionMapper"},
        // user（2 类 3 边，批次① 后）
        {BASE + ".user.service.impl.UserManageAppServiceImpl", BASE + ".role.mapper.UserRoleMapper"},
        {BASE + ".user.service.domain.impl.BatchAdminUserProjectionWriter", BASE + ".role.mapper.UserRoleMapper"},
        {BASE + ".user.service.domain.impl.BatchAdminUserProjectionWriter", BASE + ".resource.mapper.ResourceEntityMapper"},
        // menu / domain（2 类 2 边）
        {BASE + ".menu.service.impl.UserMenuQueryAppServiceImpl", BASE + ".role.mapper.UserRoleQueryMapper"},
        {BASE + ".domain.service.domain.impl.DomainClassifyServiceImpl", BASE + ".type.mapper.TypeDefinitionMapper"},
        // type（2 类 3 边）
        {BASE + ".type.service.impl.TypeDefinitionAppServiceImpl", BASE + ".grant.mapper.RoleResourcePermissionMapper"},
        {BASE + ".type.service.impl.TypeDefinitionAppServiceImpl", BASE + ".resource.mapper.ResourceApiMappingMapper"},
        {BASE + ".type.service.domain.ResourceTypeOwnershipGuard", BASE + ".resource.mapper.ServiceConfigMapper"},
        // resource（2 类 2 边，批次① 后）
        {BASE + ".resource.service.impl.ResourceManageAppServiceImpl", BASE + ".grant.mapper.RoleResourcePermissionMapper"},
        {BASE + ".resource.service.impl.DependencyAppServiceImpl", BASE + ".type.mapper.OperationPermissionMapper"},
        // rule（3 类 3 边，批次① 后；ConflictRuleAppServiceImpl 边已收敛）
        {BASE + ".rule.service.impl.ConditionAppServiceImpl", BASE + ".grant.mapper.RoleResourcePermissionMapper"},
        {BASE + ".rule.service.domain.impl.PermissionConditionDomainServiceImpl", BASE + ".grant.mapper.RoleResourcePermissionMapper"},
        // rule（续 1 边）
        {BASE + ".rule.service.domain.impl.PermissionConflictDomainServiceImpl", BASE + ".type.mapper.OperationPermissionMapper"},
    };

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_JARS)
            .importPackages(BASE);
    }

    /**
     * 能力包 Mapper 数据边界（§8.4 规则 1，12 能力包全组合 + 冻结白名单）。
     * <p>
     * 实现=字节码级依赖遍历（{@code getDirectDependenciesFromSelf} 覆盖 import 行与内联 FQCN
     * 全形态）：消费方能力包 → 他能力包 {@code {cap}.mapper} 的每条依赖必须在冻结白名单内，
     * 否则违规（「不得新增」锁）。
     * 供 {@link AccessServiceArchitectureTest} 以同一规则文本复检（§8.4「同断言两处引用同一规则文本」）。
     * </p>
     */
    static void checkCapabilityMapperBoundary(JavaClasses imported) {
        checkCapabilityMapperBoundary(imported, frozenWhitelistAsMap());
    }

    /** 负向自证入口：可注入被裁剪的白名单，验证规则的拒绝能力。 */
    static void checkCapabilityMapperBoundary(JavaClasses imported, java.util.Map<String, java.util.Set<String>> whitelist) {
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
                // 嵌套类（编译产物 $ 形态）归一到外部类再查白名单——§8.4 表按源文件级「19 类 30 边」计数
                String consumerKey = outerClassName(clazz.getFullName());
                String targetKey = outerClassName(target.getFullName());
                if (whitelist.getOrDefault(consumerKey, Set.of()).contains(targetKey)) {
                    continue;
                }
                violations.add(clazz.getFullName() + " -> " + target.getFullName()
                    + "（" + consumerCap + " 直读 " + targetCap + " mapper）");
            }
        }
        assertThat(violations).as("能力包互读他包 mapper（冻结白名单 30 边外不得新增，Q-009）").isEmpty();
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

    private static java.util.Map<String, java.util.Set<String>> frozenWhitelistAsMap() {
        java.util.Map<String, java.util.Set<String>> map = new java.util.LinkedHashMap<>();
        for (String[] pair : FROZEN_WHITELIST) {
            map.computeIfAbsent(pair[0], k -> new java.util.LinkedHashSet<>()).add(pair[1]);
        }
        return map;
    }

    @Test
    @DisplayName("能力包不得直读其他能力包 mapper（全组合，冻结白名单 21 边除外、不得新增）")
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

    // ------------------------------------------------------------------
    // 负向样例自证（§8.4：每条重建规则以负向样例证明仍能拒绝违规）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("负向自证：去掉白名单后存量边必被拒绝（规则有牙 + 白名单必要）")
    void mapperBoundaryRuleRejectsUnwhitelistedEdge() {
        // 取冻结白名单第一条真实边（批次① 后为 grant.PermissionGrantAppServiceImpl →
        // rule.PermissionConditionMapper），注入被移除该边的白名单——该边必须出现在违规集合中
        // （AssertionError），证明规则具备拒绝能力且白名单是必要豁免而非摆设。
        String[] sample = FROZEN_WHITELIST[0];
        java.util.Map<String, java.util.Set<String>> pruned = frozenWhitelistAsMap();
        pruned.get(sample[0]).remove(sample[1]);
        assertThat(sample[0]).endsWith("PermissionGrantAppServiceImpl");
        assertThatThrownBy(() -> checkCapabilityMapperBoundary(classes, pruned))
            .isInstanceOf(AssertionError.class)
            .hasMessageContaining("PermissionConditionMapper");
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

    @Test
    @DisplayName("白名单完整性：21 边与 §8.4 豁免 6 表收敛中状态对齐（15 消费类，T-ACCESS-043 批次① 后）")
    void frozenWhitelistShapeIsLocked() {
        assertThat(FROZEN_WHITELIST.length).isEqualTo(21);
        long consumers = java.util.Arrays.stream(FROZEN_WHITELIST).map(p -> p[0]).distinct().count();
        assertThat(consumers).as("15 消费类 21 边（Q-009 收敛中，capability-mapper-convergence-plan）").isEqualTo(15);
    }
}
