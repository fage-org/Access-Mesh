package cn.ac.fage.accessmesh.perm.common.dto.req;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 资源批量创建请求
 * <p>
 * 用于在权限中心批量创建资源的共享请求对象。
 * </p>
 */
public record ResourceBatchCreateReq(
    /**
     * 资源创建项列表（批量上限 1000；嵌套项不级联校验——畸形项走服务端宽容收集逐条跳过，
     * 部分成功语义为有意设计，T-PERM-065 用户拍板维持。null 元素仅元素级 @NotNull 拦 400，
     * 不级联嵌套字段——不触碰宽容语义）
     */
    @NotEmpty @Size(max = 1000, message = "批量上限 1000（project-rules §分批约束，超限分批提交）")
    List<@NotNull ResourceCreateReq> items
) {}