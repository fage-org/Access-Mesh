package cn.ac.fage.accessmesh.perm.common.dto.req;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 资源实体业务键集合请求
 * <p>
 * 用于按业务键批量定位资源实体（remove 等），替代内部 id 列表（T-PERM-028）。
 * perm-common 单源契约——服务端 Controller 与 SDK 消费方共用本类（T-PERM-065）。
 * </p>
 */
public record ResourceKeysReq(
    /**
     * 业务键列表（批量上限 1000，project-rules §分批约束）
     */
    @NotEmpty @Size(max = 1000, message = "批量上限 1000（project-rules §分批约束，超限分批提交）")
    List<@Valid ResourceKeyReq> items
) {}
