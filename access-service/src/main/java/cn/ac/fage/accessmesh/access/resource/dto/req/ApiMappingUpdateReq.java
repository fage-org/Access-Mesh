package cn.ac.fage.accessmesh.access.resource.dto.req;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * API映射更新请求体
 * <p>
 * 用于更新API映射信息，字段命名与ApiMappingAddReq保持一致。
 * </p>
 *
 * @param resourceId   资源ID，必填
 * @param mappingId    映射ID，必填
 * @param httpMethod   HTTP方法，可选
 * @param pathPattern  路径模式，可选
 * @param matchOrder   匹配顺序，可选
 * @param enabled      是否启用，可选
 * @param extra        扩展属性JSON，可选；空白拒绝，清空须传 extraClear（T-API-004）
 * @param extraClear   扩展属性显式清空标志（T-API-004）：true=清空 extra 为 NULL，
 *                     与 extra 同传拒绝；false/缺省无清空作用
 */
public record ApiMappingUpdateReq(
    @NotNull Long resourceId,
    @NotNull Long mappingId,
    String httpMethod,
    String pathPattern,
    Integer matchOrder,
    Boolean enabled,
    @Pattern(regexp = "(?s).*\\S.*", message = "extra 不能为空白；清空请传 extraClear=true")
    String extra,
    Boolean extraClear
) {
    /**
     * T-API-004（U006 拍板：全端点冲突拒绝）：新值与 Clear 同传拒绝。
     */
    @AssertTrue(message = "extra 与 extraClear 不能同时提供（清空请只传 extraClear=true）")
    public boolean isExtraConflictFree() {
        return extra == null || !Boolean.TRUE.equals(extraClear);
    }
}
