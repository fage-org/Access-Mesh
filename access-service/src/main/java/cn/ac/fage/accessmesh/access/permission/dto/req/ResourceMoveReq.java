package cn.ac.fage.accessmesh.access.permission.dto.req;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * 资源移动请求体
 * <p>
 * 以业务键定位被移动资源与目标父资源（T-PERM-028，替代 resourceId/parentId 内部 id）。
 * parent 为 null 表示移动到顶层；parent 与 resource 必须同资源类型，
 * 且 parent 不得是 resource 自身或其子孙（防环，后端校验）。
 * </p>
 *
 * @param resource 被移动资源的业务键，必填
 * @param parent   目标父资源的业务键，可选；null=移动到顶层
 */
public record ResourceMoveReq(
    @Valid @NotNull ResourceKeyReq resource,
    @Valid ResourceKeyReq parent
) {}
