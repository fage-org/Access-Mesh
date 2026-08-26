package cn.ac.fage.accessmesh.example.controller;

import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.ac.fage.accessmesh.example.dto.DemoHelloReq;
import cn.ac.fage.accessmesh.example.dto.DemoHelloResp;
import cn.ac.fage.accessmesh.example.enums.ExampleErrorCode;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 演示控制器
 * <p>
 * AccessMesh 权限中心接入示例：本接口自身不做任何鉴权，接口级鉴权由 Gateway 承担
 * （规范 §2.4 服务内不重复鉴权）——Gateway 路由 /example/**（serviceCode=example-service）
 * 命中 resource_api_mapping 后按接口快照放行或拒绝（未授权 403，授权后 200）。
 * 注册方式：access-service 中创建资源实体与 API 映射（serviceCode=example-service、
 * pathPattern=/example/api/example/demo/hello，外部路径），再向角色授予 API:ACCESS。
 * </p>
 */
@RestController
@RequestMapping("/api/example/demo")
public class DemoController {

    /**
     * 问候接口（身份回显）
     * <p>
     * 返回问候语并回显 Gateway 注入的 X-User-Id / X-Tenant-Id 身份请求头，
     * 证明请求经 Gateway 鉴权后真实到达业务服务；身份头缺失说明未走 Gateway 链路。
     * </p>
     *
     * @param req       请求体（name 非空白）
     * @param userId    Gateway 注入的用户 ID 请求头
     * @param tenantId  Gateway 注入的租户 ID 请求头
     * @return 问候语与身份回显
     */
    @PostMapping("/hello")
    public PermResult<DemoHelloResp> hello(@RequestBody DemoHelloReq req,
                                           @RequestHeader(name = "X-User-Id", required = false) String userId,
                                           @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId) {
        if (req == null || req.name() == null || req.name().isBlank()) {
            throw new BizException(ExampleErrorCode.DEMO_PARAM_INVALID.getCode(),
                ExampleErrorCode.DEMO_PARAM_INVALID.getMessage());
        }
        if (userId == null || tenantId == null) {
            throw new BizException(ExampleErrorCode.DEMO_IDENTITY_HEADER_MISSING.getCode(),
                ExampleErrorCode.DEMO_IDENTITY_HEADER_MISSING.getMessage());
        }
        return PermResult.success(new DemoHelloResp("hello, " + req.name(), userId, tenantId));
    }
}
