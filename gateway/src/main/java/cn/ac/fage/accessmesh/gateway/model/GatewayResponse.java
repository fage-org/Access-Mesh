package cn.ac.fage.accessmesh.gateway.model;

import cn.ac.fage.accessmesh.common.model.R;
import com.fasterxml.jackson.annotation.JsonInclude;

/** 复用 common 的唯一信封字段；保留 Gateway 原有空值省略策略与工厂入口。 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class GatewayResponse extends R<Object> {
    private GatewayResponse(int code, String message, Object data) {
        super(code, message, data, null, null);
    }
    public static GatewayResponse success(Object data) { return new GatewayResponse(200, "success", data); }
    public static GatewayResponse error(int code, String message) { return new GatewayResponse(code, message, null); }
    public static GatewayResponse error(int code, String message, Object data) { return new GatewayResponse(code, message, data); }
}
