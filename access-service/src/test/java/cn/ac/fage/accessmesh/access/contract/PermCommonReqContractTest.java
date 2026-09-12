package cn.ac.fage.accessmesh.access.contract;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedArrayType;
import java.lang.reflect.AnnotatedParameterizedType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * perm-common Req 单源契约守卫（T-PERM-065 双轨收敛后定位）。
 * <p>
 * 原守卫形态（S3/M1 domainCode 事故产物）只断言必填性且依赖「双端同改」纪律；
 * {@code UserAssignRoleReq.items} 的 {@code @Size(max=1000)} 服务端有而 SDK 副本缺失的
 * 现役分叉证明该形态抓不到长度类漂移。T-PERM-065 删除 access-service 侧 14 个副本后，
 * perm-common 是服务端 Controller 与 SDK 消费方的唯一 Req 源——本测试以
 * 「组件 → Bean Validation 注解签名全集」快照钉死单源契约：必填性、批量上限、
 * 级联校验（含容器元素位置）任一漂移即失败，增删字段/注解须显式更新快照并过评审。
 * </p>
 * <p>
 * 签名规约：声明位置注解列注解名 + 显式声明的属性（与注解默认值不同的属性才进快照，
 * 属性名排序）；容器元素位置注解（如 {@code List<@Valid X>}）加 {@code element::} 前缀
 * 区分——字段级与逐元素级语义不同，混同会让漂移静默逃过。
 * </p>
 */
class PermCommonReqContractTest {

    /** 14 个单源 Req（含嵌套 record）的注解签名快照。 */
    private static final Set<String> EXPECTED_ANNOTATIONS = Set.of("""
AuthCheckReq#codeType -> []
AuthCheckReq#context -> []
AuthCheckReq#domainCode -> []
AuthCheckReq#inheritMode -> []
AuthCheckReq#operationCode -> [@NotBlank(message="操作编码不能为空")]
AuthCheckReq#parentCodeType -> []
AuthCheckReq#parentOperationCodes -> [@Size(max=1000, message="批量上限 1000（project-rules §分批约束，超限分批提交）")]
AuthCheckReq#parentResourceCode -> []
AuthCheckReq#parentResourceTypeCode -> []
AuthCheckReq#resourceCode -> []
AuthCheckReq#resourceTypeCode -> [@NotBlank(message="资源类型编码不能为空")]
AuthCheckReq#subjectExternalId -> [@NotBlank(message="主体外部标识不能为空")]
AuthCheckReq#subjectTypeCode -> [@NotBlank(message="主体类型编码不能为空")]
BatchAuthCheckReq#context -> []
BatchAuthCheckReq#items -> [@NotEmpty(message="检查项不能为空"), @Size(max=1000, message="批量上限 1000（project-rules §分批约束，超限分批提交）"), element::@NotNull, element::@Valid]
BatchAuthCheckReq#parentCodeType -> []
BatchAuthCheckReq#parentOperationCodes -> [@Size(max=1000, message="批量上限 1000（project-rules §分批约束，超限分批提交）")]
BatchAuthCheckReq#parentResourceCode -> []
BatchAuthCheckReq#parentResourceTypeCode -> []
BatchAuthCheckReq#subjectExternalId -> [@NotBlank(message="主体外部标识不能为空")]
BatchAuthCheckReq#subjectTypeCode -> [@NotBlank(message="主体类型编码不能为空")]
BatchAuthCheckReq.AuthCheckItem#codeType -> []
BatchAuthCheckReq.AuthCheckItem#domainCode -> []
BatchAuthCheckReq.AuthCheckItem#inheritMode -> []
BatchAuthCheckReq.AuthCheckItem#operationCode -> [@NotBlank]
BatchAuthCheckReq.AuthCheckItem#resourceCode -> []
BatchAuthCheckReq.AuthCheckItem#resourceTypeCode -> [@NotBlank]
CheckInterfaceReq#context -> []
CheckInterfaceReq#httpMethod -> [@NotBlank]
CheckInterfaceReq#path -> [@NotBlank]
CheckInterfaceReq#serviceCode -> [@NotBlank]
CheckInterfaceReq#subjectExternalId -> [@NotBlank]
CheckInterfaceReq#subjectTypeCode -> [@NotBlank]
IdReq#id -> [@NotNull]
IdsReq#ids -> [@NotEmpty, @Size(max=1000, message="批量上限 1000（project-rules §分批约束，超限分批提交）")]
OperationListReq#resourceTypeCode -> []
ResourceBatchCreateReq#items -> [@NotEmpty, @Size(max=1000, message="批量上限 1000（project-rules §分批约束，超限分批提交）"), element::@NotNull]
ResourceCreateReq#code -> [@NotBlank]
ResourceCreateReq#codeType -> []
ResourceCreateReq#extra -> []
ResourceCreateReq#name -> [@NotBlank]
ResourceCreateReq#parentCodeType -> []
ResourceCreateReq#parentDomainCode -> []
ResourceCreateReq#parentId -> []
ResourceCreateReq#parentResourceCode -> []
ResourceCreateReq#parentResourceTypeCode -> []
ResourceCreateReq#path -> []
ResourceCreateReq#resourceTypeCode -> [@NotBlank]
ResourceCreateReq#sortOrder -> []
ResourceCreateReq#status -> []
ResourceKeyReq#code -> [@NotBlank]
ResourceKeyReq#codeType -> []
ResourceKeyReq#resourceTypeCode -> [@NotBlank]
ResourceKeysReq#items -> [@NotEmpty, @Size(max=1000, message="批量上限 1000（project-rules §分批约束，超限分批提交）"), element::@Valid]
ResourceUpdateReq#code -> [@NotBlank]
ResourceUpdateReq#codeType -> []
ResourceUpdateReq#extra -> []
ResourceUpdateReq#extraClear -> []
ResourceUpdateReq#name -> []
ResourceUpdateReq#path -> []
ResourceUpdateReq#resourceTypeCode -> [@NotBlank]
ResourceUpdateReq#sortOrder -> []
ResourceUpdateReq#status -> []
RoleCreateReq#externalId -> []
RoleCreateReq#extra -> []
RoleCreateReq#name -> [@NotBlank(message="角色名称不能为空")]
RoleCreateReq#parentId -> []
RoleCreateReq#roleTypeCode -> [@NotBlank(message="角色类型不能为空")]
RoleCreateReq#sortOrder -> []
RoleDetailReq#roleExternalId -> [@NotBlank(message="角色外部标识不能为空"), @Size(max=128)]
RoleDetailReq#roleTypeCode -> [@NotBlank(message="角色类型编码不能为空"), @Size(max=64)]
RoleListReq#domainCode -> []
RoleListReq#keyword -> []
RoleListReq#pageNum -> []
RoleListReq#pageSize -> []
RoleListReq#roleTypeCode -> []
RoleListReq#roleTypeCodes -> []
RoleListReq#sort -> []
UserAssignRoleReq#items -> [@NotEmpty, @Size(max=1000, message="批量上限 1000（project-rules §分批约束，超限分批提交）"), element::@NotNull, element::@Valid]
UserAssignRoleReq.AssignItem#domainCode -> []
UserAssignRoleReq.AssignItem#relationId -> []
UserAssignRoleReq.AssignItem#roleExternalId -> [@NotBlank]
UserAssignRoleReq.AssignItem#roleTypeCode -> [@NotBlank]
UserAssignRoleReq.AssignItem#subjectExternalId -> [@NotBlank]
UserAssignRoleReq.AssignItem#subjectTypeCode -> [@NotBlank]
UserAssignRoleReq.AssignItem#validFrom -> []
UserAssignRoleReq.AssignItem#validTo -> []
UserRoleBatchRevokeReq#items -> [@NotEmpty, @Size(max=1000, message="批量上限 1000（project-rules §分批约束，超限分批提交）"), @Valid, element::@NotNull]
UserRoleBatchRevokeReq.RevokeItem#domainCode -> []
UserRoleBatchRevokeReq.RevokeItem#relationId -> []
UserRoleBatchRevokeReq.RevokeItem#roleExternalId -> [@NotBlank]
UserRoleBatchRevokeReq.RevokeItem#roleTypeCode -> [@NotBlank]
UserRoleBatchRevokeReq.RevokeItem#subjectExternalId -> [@NotBlank]
UserRoleBatchRevokeReq.RevokeItem#subjectTypeCode -> [@NotBlank]
UserRoleListReq#subjectExternalId -> [@NotBlank]
UserRoleListReq#subjectTypeCode -> [@NotBlank]
""".strip().split("\n"));

    private static final List<Class<?>> GUARDED_CLASSES = List.of(
        cn.ac.fage.accessmesh.perm.common.dto.req.AuthCheckReq.class,
        cn.ac.fage.accessmesh.perm.common.dto.req.BatchAuthCheckReq.class,
        cn.ac.fage.accessmesh.perm.common.dto.req.CheckInterfaceReq.class,
        cn.ac.fage.accessmesh.perm.common.dto.req.IdReq.class,
        cn.ac.fage.accessmesh.perm.common.dto.req.IdsReq.class,
        cn.ac.fage.accessmesh.perm.common.dto.req.OperationListReq.class,
        cn.ac.fage.accessmesh.perm.common.dto.req.ResourceBatchCreateReq.class,
        cn.ac.fage.accessmesh.perm.common.dto.req.ResourceCreateReq.class,
        cn.ac.fage.accessmesh.perm.common.dto.req.ResourceKeyReq.class,
        cn.ac.fage.accessmesh.perm.common.dto.req.ResourceKeysReq.class,
        cn.ac.fage.accessmesh.perm.common.dto.req.ResourceUpdateReq.class,
        cn.ac.fage.accessmesh.perm.common.dto.req.RoleCreateReq.class,
        cn.ac.fage.accessmesh.perm.common.dto.req.RoleDetailReq.class,
        cn.ac.fage.accessmesh.perm.common.dto.req.RoleListReq.class,
        cn.ac.fage.accessmesh.perm.common.dto.req.UserAssignRoleReq.class,
        cn.ac.fage.accessmesh.perm.common.dto.req.UserRoleBatchRevokeReq.class,
        cn.ac.fage.accessmesh.perm.common.dto.req.UserRoleListReq.class);

    /** 14 类（含嵌套）组件序与组件类型快照——record 位置构造器被 SDK Feign/Gateway/测试大量使用，字段重排或改型会静默改变语义（注解签名快照不含此两面）。 */
    private static final List<String> EXPECTED_COMPONENT_ORDER_AND_TYPES = List.of("""
AuthCheckReq: String subjectTypeCode, String subjectExternalId, String resourceTypeCode, String resourceCode, String operationCode, String domainCode, String codeType, String inheritMode, String parentResourceTypeCode, String parentResourceCode, String parentCodeType, List<String> parentOperationCodes, Map<String, Object> context
BatchAuthCheckReq: String subjectTypeCode, String subjectExternalId, List<BatchAuthCheckReq$AuthCheckItem> items, String parentResourceTypeCode, String parentResourceCode, String parentCodeType, List<String> parentOperationCodes, Map<String, Object> context
BatchAuthCheckReq.AuthCheckItem: String resourceTypeCode, String resourceCode, String operationCode, String domainCode, String codeType, String inheritMode
CheckInterfaceReq: String subjectTypeCode, String subjectExternalId, String serviceCode, String httpMethod, String path, Map<String, Object> context
IdReq: Long id
IdsReq: List<Long> ids
OperationListReq: String resourceTypeCode
ResourceBatchCreateReq: List<ResourceCreateReq> items
ResourceCreateReq: Long parentId, String parentResourceTypeCode, String parentResourceCode, String parentCodeType, String parentDomainCode, String resourceTypeCode, String code, String codeType, String name, String path, Integer status, Integer sortOrder, String extra
ResourceKeyReq: String resourceTypeCode, String code, String codeType
ResourceKeysReq: List<ResourceKeyReq> items
ResourceUpdateReq: String resourceTypeCode, String code, String codeType, String name, String path, Integer status, Integer sortOrder, String extra, Boolean extraClear
RoleCreateReq: Long parentId, String roleTypeCode, String externalId, String name, Integer sortOrder, String extra
RoleDetailReq: String roleTypeCode, String roleExternalId
RoleListReq: String domainCode, String roleTypeCode, List<String> roleTypeCodes, String keyword, Integer pageNum, Integer pageSize, String sort
UserAssignRoleReq: List<UserAssignRoleReq$AssignItem> items
UserAssignRoleReq.AssignItem: String subjectTypeCode, String subjectExternalId, String domainCode, String roleTypeCode, String roleExternalId, Long relationId, java.time.LocalDateTime validFrom, java.time.LocalDateTime validTo
UserRoleBatchRevokeReq: List<UserRoleBatchRevokeReq$RevokeItem> items
UserRoleBatchRevokeReq.RevokeItem: String subjectTypeCode, String subjectExternalId, String domainCode, String roleTypeCode, String roleExternalId, Long relationId
UserRoleListReq: String subjectTypeCode, String subjectExternalId
""".strip().split("\n"));

    @Test
    @DisplayName("perm-common 14 个单源 Req 的 Bean Validation 注解签名快照（含容器元素位置与批量上限）")
    void permCommonReqAnnotationSignaturesAreFrozen() {
        Set<String> actual = new TreeSet<>();
        for (Class<?> top : GUARDED_CLASSES) {
            collectSignatures(top, top.getSimpleName(), actual);
            for (Class<?> nested : top.getDeclaredClasses()) {
                collectSignatures(nested, top.getSimpleName() + "." + nested.getSimpleName(), actual);
            }
        }
        Set<String> snapshotMissing = new TreeSet<>(actual);
        snapshotMissing.removeAll(EXPECTED_ANNOTATIONS);
        Set<String> snapshotExtra = new TreeSet<>(EXPECTED_ANNOTATIONS);
        snapshotExtra.removeAll(actual);
        assertThat(snapshotMissing)
            .as("perm-common Req 实际注解签名超出快照（新增字段/注解须显式更新快照并过评审）: %s", snapshotMissing)
            .isEmpty();
        assertThat(snapshotExtra)
            .as("快照声明了实际不存在的注解签名（注解被删/漂移，须核对 SDK 契约）: %s", snapshotExtra)
            .isEmpty();
    }

    @Test
    @DisplayName("perm-common 14 个单源 Req 的组件序与组件类型快照（位置构造器消费方防字段重排/改型静默漂移）")
    void permCommonReqComponentOrderAndTypesAreFrozen() {
        List<String> actual = new ArrayList<>();
        for (Class<?> top : GUARDED_CLASSES) {
            collectComponentTypes(top, top.getSimpleName(), actual);
            for (Class<?> nested : top.getDeclaredClasses()) {
                collectComponentTypes(nested, top.getSimpleName() + "." + nested.getSimpleName(), actual);
            }
        }
        assertThat(actual)
            .as("组件序与类型快照有序精确比对（record 规范构造器按位置传参，重排/改型=消费方静默语义漂移）")
            .containsExactlyElementsOf(EXPECTED_COMPONENT_ORDER_AND_TYPES);
    }

    /** 提取一个 record 的「路径: 类型 名, 类型 名...」行（类型去包前缀归一化）。 */
    private void collectComponentTypes(Class<?> recordClass, String path, List<String> out) {
        StringBuilder sb = new StringBuilder(path).append(':');
        RecordComponent[] components = recordClass.getRecordComponents();
        for (int i = 0; i < components.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(' ').append(normalizeTypeName(components[i].getGenericType().getTypeName()))
                .append(' ').append(components[i].getName());
        }
        out.add(sb.toString());
    }

    private String normalizeTypeName(String typeName) {
        return typeName
            .replace("java.lang.", "")
            .replace("java.util.", "")
            .replace("cn.ac.fage.accessmesh.perm.common.dto.req.", "");
    }

    /** 提取一个 record 的「路径#组件 → [声明注解 + element::容器元素注解]」签名行。 */
    private void collectSignatures(Class<?> recordClass, String path, Set<String> out) {
        RecordComponent[] components = recordClass.getRecordComponents();
        Constructor<?> ctor = null;
        for (Constructor<?> c : recordClass.getDeclaredConstructors()) {
            if (c.getParameterCount() == components.length) {
                ctor = c;
                break;
            }
        }
        assertThat(ctor).as("%s 规范构造器可解析", path).isNotNull();
        for (int i = 0; i < components.length; i++) {
            List<String> anns = new ArrayList<>();
            Parameter param = ctor.getParameters()[i];
            for (var a : param.getAnnotations()) {
                anns.add(signature(a));
            }
            if (param.getAnnotatedType() instanceof AnnotatedParameterizedType parameterized) {
                for (var typeArg : parameterized.getAnnotatedActualTypeArguments()) {
                    for (var a : typeArg.getAnnotations()) {
                        anns.add("element::" + signature(a));
                    }
                }
            } else if (param.getAnnotatedType() instanceof AnnotatedArrayType arrayType) {
                for (var a : arrayType.getAnnotatedGenericComponentType().getAnnotations()) {
                    anns.add("element::" + signature(a));
                }
            }
            anns.sort(String::compareTo);
            out.add(path + "#" + components[i].getName() + " -> " + anns);
        }
    }

    /** 注解签名 = @名(显式属性名=值，属性名排序)；无显式属性时仅 @名。 */
    private String signature(Annotation a) {
        Class<? extends Annotation> type = a.annotationType();
        if (!type.getName().startsWith("jakarta.validation")) {
            return "@" + type.getSimpleName();
        }
        List<String> attrs = new ArrayList<>();
        for (Method m : type.getDeclaredMethods()) {
            if (m.getParameterCount() != 0) continue;
            try {
                Object value = m.invoke(a);
                if (value != null && !isDefault(value, m.getDefaultValue())) {
                    attrs.add(m.getName() + "=" + formatValue(value));
                }
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("读取注解属性失败: " + type.getSimpleName() + "." + m.getName(), e);
            }
        }
        attrs.sort(String::compareTo);
        return attrs.isEmpty() ? "@" + type.getSimpleName()
            : "@" + type.getSimpleName() + "(" + String.join(", ", attrs) + ")";
    }

    private String formatValue(Object value) {
        if (value instanceof String s) {
            return "\"" + s + "\"";
        }
        return String.valueOf(value);
    }

    /** 数组属性（groups/payload）默认值比较须逐元素（equals 是引用比较，会把默认空数组当显式声明）。 */
    private boolean isDefault(Object value, Object defaultValue) {
        if (value instanceof Object[] arr && defaultValue instanceof Object[] def) {
            return java.util.Arrays.equals(arr, def);
        }
        return value.equals(defaultValue);
    }
}
