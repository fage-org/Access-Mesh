package cn.ac.fage.accessmesh.access.contract;

import cn.ac.fage.accessmesh.access.resource.dto.req.ApiMappingUpdateReq;
import cn.ac.fage.accessmesh.access.resource.dto.req.ServiceConfigReq;
import cn.ac.fage.accessmesh.access.role.dto.req.RoleUpdateReq;
import cn.ac.fage.accessmesh.access.type.dto.req.TypeUpdateReq;
import cn.ac.fage.accessmesh.access.user.dto.req.AbstractUserUpdateReq;
import cn.ac.fage.accessmesh.access.user.dto.req.UserUpdateReq;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 显式清空协议统一校验锁（T-API-004，U006 四项拍板的协议面回归）。
 * <p>
 * 拍板口径（2026-09-24）：①新值与 Clear 同传全端点拒绝（含 role/resource 既有两域——
 * 取代旧「Clear 优先于 extra」的静默丢值口径）；②目标字段空串一律拒绝（清空唯一通道
 * =xxxClear，杜绝空串入库与唯一索引空串撞车）；③false/缺省无清空作用（null=不修改维持）；
 * ④TypeUpdateReq.extraClear 不支持清空语义（服务层拒任何非 null 值，DTO 层不做冲突锁——
 * extraClear 单独出现合法交给服务层给出专门错误信息）。
 * 本用例在旧实现（无 Clear 字段/冲突静默丢值）下不编译或必红。
 * </p>
 */
class ClearFieldProtocolValidationTest {

    private static ValidatorFactory validatorFactory;

    @BeforeAll
    static void initValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
    }

    @AfterAll
    static void closeValidator() {
        if (validatorFactory != null) {
            validatorFactory.close();
        }
    }

    private final Validator validator = validatorFactory.getValidator();

    static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> remainingFields() {
        return java.util.stream.Stream.of(
            org.junit.jupiter.params.provider.Arguments.of(cn.ac.fage.accessmesh.access.rule.dto.req.ConditionUpdateReq.class, "description", "{\"code\":\"condition\"}"),
            org.junit.jupiter.params.provider.Arguments.of(cn.ac.fage.accessmesh.access.platform.dto.req.SystemConfigReq.class, "description", "{\"configKey\":\"access.test\",\"configValue\":\"{}\"}"),
            org.junit.jupiter.params.provider.Arguments.of(cn.ac.fage.accessmesh.access.menu.dto.req.MenuUpdateReq.class, "icon", "{\"id\":1}"),
            org.junit.jupiter.params.provider.Arguments.of(cn.ac.fage.accessmesh.access.auth.dto.Oauth2ClientUpdateReq.class, "redirectUris", "{\"id\":1}"),
            org.junit.jupiter.params.provider.Arguments.of(cn.ac.fage.accessmesh.access.auth.dto.Oauth2ClientUpdateReq.class, "scopes", "{\"id\":1}"),
            org.junit.jupiter.params.provider.Arguments.of(cn.ac.fage.accessmesh.access.auth.dto.Oauth2ClientUpdateReq.class, "audiences", "{\"id\":1}"),
            org.junit.jupiter.params.provider.Arguments.of(RoleUpdateReq.class, "extra", "{\"roleId\":1}"),
            org.junit.jupiter.params.provider.Arguments.of(cn.ac.fage.accessmesh.perm.common.dto.req.ResourceUpdateReq.class, "extra", "{\"resourceTypeCode\":\"DOC\",\"code\":\"doc\"}")
        );
    }

    @org.junit.jupiter.params.ParameterizedTest(name = "{0}.{1} 三态与冲突")
    @org.junit.jupiter.params.provider.MethodSource("remainingFields")
    void shouldValidateRemainingClearProtocol(Class<?> dto, String field, String base) throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var json = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(base);
        assertTrue(validator.validate(mapper.treeToValue(json, dto)).isEmpty());
        json.put(field, "\u3000");
        assertFalse(validator.validate(mapper.treeToValue(json, dto)).isEmpty(), field + " 空白必须拒绝");
        json.put(field, "value");
        json.put(field + "Clear", true);
        assertFalse(validator.validate(mapper.treeToValue(json, dto)).isEmpty());
        json.remove(field);
        assertTrue(validator.validate(mapper.treeToValue(json, dto)).isEmpty());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"clientName", "clientSecret", "grantTypes"})
    void shouldRejectBlankRequiredOauthUpdateFields(String field) throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var json = mapper.createObjectNode().put("id", 1).put(field, " ");
        assertFalse(validator.validate(mapper.treeToValue(json,
            cn.ac.fage.accessmesh.access.auth.dto.Oauth2ClientUpdateReq.class)).isEmpty());
    }

    // ---- 冲突拒绝（拍板①：全端点） ----

    @Test
    @DisplayName("值与 Clear 同传拒绝——user phone/email")
    void userConflictMustBeRejected() {
        assertFalse(validator.validate(new UserUpdateReq(1L, null, "13800000000", null, null, true, null)).isEmpty());
        assertFalse(validator.validate(new UserUpdateReq(1L, null, null, "a@b.c", null, null, true)).isEmpty());
    }

    @Test
    @DisplayName("值与 Clear 同传拒绝——type description / service 三字段 / mapping extra / abstract-user extra")
    void conflictMustBeRejectedAcrossTargets() {
        assertFalse(validator.validate(new TypeUpdateReq(1L, null, "描述", null, null, true, null)).isEmpty());
        assertFalse(validator.validate(new ServiceConfigReq("svc", "名", "/api", null, null, null, true, null, null)).isEmpty());
        assertFalse(validator.validate(new ServiceConfigReq("svc", "名", null, "描述", null, null, null, true, null)).isEmpty());
        assertFalse(validator.validate(new ServiceConfigReq("svc", "名", null, null, null, "{\"k\":1}", null, null, true)).isEmpty());
        assertFalse(validator.validate(new ApiMappingUpdateReq(1L, 2L, null, null, null, null, "{\"k\":1}", true, null)).isEmpty());
        assertFalse(validator.validate(new AbstractUserUpdateReq(1L, null, null, "{\"k\":1}", true)).isEmpty());
    }

    @Test
    @DisplayName("值与 Clear 同传拒绝——role/resource 既有两域同批对齐（旧「Clear 优先」口径退役）")
    void legacyRoleResourceConflictMustBeRejected() {
        assertFalse(validator.validate(new RoleUpdateReq(1L, null, null, null, "{\"k\":1}", true)).isEmpty());
        assertFalse(validator.validate(new cn.ac.fage.accessmesh.perm.common.dto.req.ResourceUpdateReq(
            "API", "code", null, null, null, null, "{\"k\":1}", true)).isEmpty());
    }

    // ---- 空串拒绝（拍板②：六字段，清空唯一通道=xxxClear） ----

    @Test
    @DisplayName("空串/纯空白拒绝——phone/email/description/basePath/extra")
    void blankMustBeRejected() {
        assertFalse(validator.validate(new UserUpdateReq(1L, null, "", null, null, null, null)).isEmpty());
        assertFalse(validator.validate(new UserUpdateReq(1L, null, " ", null, null, null, null)).isEmpty());
        assertFalse(validator.validate(new UserUpdateReq(1L, null, null, "", null, null, null)).isEmpty());
        assertFalse(validator.validate(new TypeUpdateReq(1L, null, "", null, null, null, null)).isEmpty());
        assertFalse(validator.validate(new TypeUpdateReq(1L, null, null, null, " ", null, null)).isEmpty());
        assertFalse(validator.validate(new ServiceConfigReq("svc", "名", "", null, null, null, null, null, null)).isEmpty());
        assertFalse(validator.validate(new ServiceConfigReq("svc", "名", null, " ", null, null, null, null, null)).isEmpty());
        assertFalse(validator.validate(new ServiceConfigReq("svc", "名", null, null, null, "", null, null, null)).isEmpty());
        assertFalse(validator.validate(new ApiMappingUpdateReq(1L, 2L, null, null, null, null, " ", null, null)).isEmpty());
    }

    // ---- false/缺省无清空作用；单用形态放行（拍板③；既有正常 extraClear 消费者不回退） ----

    @Test
    @DisplayName("Clear 单用放行、false 与值同传放行、非目标字段不受影响")
    void singleUseAndFalseMustPass() {
        assertTrue(validator.validate(new UserUpdateReq(1L, null, null, null, null, true, null)).isEmpty());
        assertTrue(validator.validate(new UserUpdateReq(1L, "名", "13800000000", "a@b.c", null, false, false)).isEmpty());
        assertTrue(validator.validate(new TypeUpdateReq(1L, "名", null, null, null, true, null)).isEmpty());
        assertTrue(validator.validate(new ServiceConfigReq("svc", "名", null, null, null, null, true, true, true)).isEmpty());
        assertTrue(validator.validate(new ApiMappingUpdateReq(1L, 2L, null, null, null, null, null, true, null)).isEmpty());
        assertTrue(validator.validate(new RoleUpdateReq(1L, null, null, null, null, true)).isEmpty());
        assertTrue(validator.validate(new RoleUpdateReq(1L, "名", 1, 0, "{\"k\":1}", false)).isEmpty());
        // abstract-user：extraClear 单用=合法业务载荷放行（空 patch 计入）；与值同传见冲突用例
        assertTrue(validator.validate(new AbstractUserUpdateReq(1L, null, null, null, true)).isEmpty());
        // false 与空串同传：冲突锁不触发，空串锁独立生效（两锁正交）
        assertFalse(validator.validate(new UserUpdateReq(1L, null, "", null, null, false, null)).isEmpty());
        // abstract-user extra 空白拒绝（协议覆盖字段）
        assertFalse(validator.validate(new AbstractUserUpdateReq(1L, null, null, " ", null)).isEmpty());
    }

    @Test
    @DisplayName("type extraClear 单独出现放行（语义拒绝在服务层给出专门错误信息，非 Bean Validation）")
    void typeExtraClearDelegatesToServiceLayer() {
        assertTrue(validator.validate(new TypeUpdateReq(1L, null, null, null, null, null, true)).isEmpty());
    }

    // ---- claude 外评（2026-09-24 复核轮）锁面：严格 mapper 线格式 + Unicode 空白 ----

    /** 全部协议 DTO 的线格式键集合必须恰为 record 组件集（派生 @AssertTrue is-getter 泄露
     * 进线格式时，Java SDK 序列化请求会被服务端严格 mapper 以未知字段拒 400——
     * claude 外评 P2；@JsonIgnore 先例 OperationPermission.getEffectiveBits）。 */
    @Test
    @DisplayName("线格式键集==组件集且严格 mapper 往返成功（旧实现 is-getter 泄露必红）")
    void wireFormatKeysMustEqualRecordComponents() throws Exception {
        com.fasterxml.jackson.databind.ObjectMapper mapper =
            new com.fasterxml.jackson.databind.ObjectMapper()
                .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        java.util.List<Class<?>> dtos = java.util.List.of(
            cn.ac.fage.accessmesh.access.rule.dto.req.ConditionUpdateReq.class,
            cn.ac.fage.accessmesh.access.platform.dto.req.SystemConfigReq.class,
            cn.ac.fage.accessmesh.access.menu.dto.req.MenuUpdateReq.class,
            cn.ac.fage.accessmesh.access.auth.dto.Oauth2ClientUpdateReq.class,
            UserUpdateReq.class, AbstractUserUpdateReq.class, TypeUpdateReq.class,
            ServiceConfigReq.class, ApiMappingUpdateReq.class, RoleUpdateReq.class,
            cn.ac.fage.accessmesh.perm.common.dto.req.ResourceUpdateReq.class,
            cn.ac.fage.accessmesh.perm.common.dto.req.AuthCheckReq.class,
            cn.ac.fage.accessmesh.perm.common.dto.req.BatchAuthCheckReq.class);
        for (Class<?> dto : dtos) {
            Object sample = allNullInstance(dto);
            String json = mapper.writeValueAsString(sample);
            java.util.Set<String> keys = mapper.readTree(json).properties().stream()
                .map(java.util.Map.Entry::getKey).collect(java.util.stream.Collectors.toSet());
            java.util.Set<String> components = java.util.Arrays.stream(dto.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName).collect(java.util.stream.Collectors.toSet());
            org.junit.jupiter.api.Assertions.assertEquals(components, keys,
                dto.getSimpleName() + " 线格式键集必须恰为组件集（派生 getter 须 @JsonIgnore）");
            mapper.readValue(json, dto);
        }
    }

    /** 反射构造全 null 组件实例（协议 DTO 组件全为对象类型）。 */
    private static Object allNullInstance(Class<?> dto) throws Exception {
        var ctor = dto.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        Object[] args = new Object[ctor.getParameterCount()];
        java.util.Arrays.fill(args, null);
        return ctor.newInstance(args);
    }

    /** Java 正则 \s 默认仅 ASCII——全角空格（U+3000）/NBSP（U+00A0）须同样拒绝
     * （claude 外评 P3；@Pattern 补 (?U) UNICODE_CHARACTER_CLASS 前、旧正则下本用例必红）。 */
    @Test
    @DisplayName("Unicode 空白（全角空格/NBSP）同样拒绝（旧 ASCII-only 正则必红）")
    void unicodeWhitespaceMustBeRejected() {
        assertFalse(validator.validate(new UserUpdateReq(1L, null, "　　", null, null, null, null)).isEmpty());
        assertFalse(validator.validate(new UserUpdateReq(1L, null, null, " ", null, null, null)).isEmpty());
        assertFalse(validator.validate(new TypeUpdateReq(1L, null, "　", null, null, null, null)).isEmpty());
        assertFalse(validator.validate(new ServiceConfigReq("svc", "名", " ", null, null, null, null, null, null)).isEmpty());
        assertFalse(validator.validate(new ApiMappingUpdateReq(1L, 2L, null, null, null, null, "　", null, null)).isEmpty());
        assertFalse(validator.validate(new AbstractUserUpdateReq(1L, null, null, " ", null)).isEmpty());
    }

    // ---- 2026-10-04 拍板 A：空白拒绝覆盖新建入口（此前沿六字段先例仅做更新面） ----
    // 旧实现（Create DTO 无 @Pattern）下「空白必须拒绝」断言必红；ResourceBatchCreateReq
    // 嵌套项沿 T-PERM-065 宽容收集拍板不级联校验，不在本表。

    static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> createSideFields() {
        return java.util.stream.Stream.of(
            org.junit.jupiter.params.provider.Arguments.of(
                cn.ac.fage.accessmesh.access.rule.dto.req.ConditionCreateReq.class, "description",
                "{\"code\":\"cond-a\",\"name\":\"条件A\",\"conditionRules\":\"[]\"}"),
            org.junit.jupiter.params.provider.Arguments.of(
                cn.ac.fage.accessmesh.access.menu.dto.req.MenuCreateReq.class, "icon",
                "{\"menuType\":\"MENU\",\"displayName\":\"菜单A\"}"),
            org.junit.jupiter.params.provider.Arguments.of(
                cn.ac.fage.accessmesh.access.user.dto.req.UserCreateReq.class, "phone",
                "{\"username\":\"user01\",\"name\":\"用户A\"}"),
            org.junit.jupiter.params.provider.Arguments.of(
                cn.ac.fage.accessmesh.access.user.dto.req.UserCreateReq.class, "email",
                "{\"username\":\"user01\",\"name\":\"用户A\"}"),
            org.junit.jupiter.params.provider.Arguments.of(
                cn.ac.fage.accessmesh.access.type.dto.req.TypeCreateReq.class, "description",
                "{\"typeKey\":\"REPORT\",\"name\":\"报表\"}"),
            org.junit.jupiter.params.provider.Arguments.of(
                cn.ac.fage.accessmesh.access.type.dto.req.TypeCreateReq.class, "extra",
                "{\"typeKey\":\"REPORT\",\"name\":\"报表\"}"),
            org.junit.jupiter.params.provider.Arguments.of(
                cn.ac.fage.accessmesh.access.auth.dto.Oauth2ClientCreateReq.class, "grantTypes",
                "{\"clientId\":\"client-a\",\"clientSecret\":\"secret-a\",\"clientName\":\"客户端A\"}"),
            org.junit.jupiter.params.provider.Arguments.of(
                cn.ac.fage.accessmesh.access.auth.dto.Oauth2ClientCreateReq.class, "redirectUris",
                "{\"clientId\":\"client-a\",\"clientSecret\":\"secret-a\",\"clientName\":\"客户端A\"}"),
            org.junit.jupiter.params.provider.Arguments.of(
                cn.ac.fage.accessmesh.access.auth.dto.Oauth2ClientCreateReq.class, "scopes",
                "{\"clientId\":\"client-a\",\"clientSecret\":\"secret-a\",\"clientName\":\"客户端A\"}"),
            org.junit.jupiter.params.provider.Arguments.of(
                cn.ac.fage.accessmesh.access.auth.dto.Oauth2ClientCreateReq.class, "audiences",
                "{\"clientId\":\"client-a\",\"clientSecret\":\"secret-a\",\"clientName\":\"客户端A\"}"),
            org.junit.jupiter.params.provider.Arguments.of(
                cn.ac.fage.accessmesh.access.user.dto.req.AbstractUserCreateReq.class, "extra",
                "{\"subjectTypeCode\":\"LOCAL_USER\",\"externalId\":\"ext-01\"}"),
            org.junit.jupiter.params.provider.Arguments.of(
                cn.ac.fage.accessmesh.access.resource.dto.req.ApiMappingAddReq.class, "extra",
                "{\"resourceId\":1,\"serviceCode\":\"example-service\",\"httpMethod\":\"GET\",\"pathPattern\":\"/x\"}"),
            org.junit.jupiter.params.provider.Arguments.of(
                cn.ac.fage.accessmesh.perm.common.dto.req.RoleCreateReq.class, "extra",
                "{\"roleTypeCode\":\"BASIC_ROLE\",\"name\":\"角色A\"}"),
            org.junit.jupiter.params.provider.Arguments.of(
                cn.ac.fage.accessmesh.perm.common.dto.req.ResourceCreateReq.class, "extra",
                "{\"resourceTypeCode\":\"REPORT\",\"code\":\"report-a\",\"name\":\"报表A\"}")
        );
    }

    @org.junit.jupiter.params.ParameterizedTest(name = "{0}.{1} 新建入口空白拒绝（拍板 A）")
    @org.junit.jupiter.params.provider.MethodSource("createSideFields")
    void createSideBlankMustBeRejected(Class<?> dto, String field, String base) throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var json = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(base);
        assertTrue(validator.validate(mapper.treeToValue(json, dto)).isEmpty(), "基线载荷必须合法");
        json.put(field, "\u3000");
        assertFalse(validator.validate(mapper.treeToValue(json, dto)).isEmpty(),
            field + " 新建入口纯空白必须拒绝");
        json.put(field, " ");
        assertFalse(validator.validate(mapper.treeToValue(json, dto)).isEmpty(),
            field + " 新建入口空串必须拒绝");
        json.remove(field);
        assertTrue(validator.validate(mapper.treeToValue(json, dto)).isEmpty(), "缺省不传保持合法");
    }
}
