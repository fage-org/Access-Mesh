package cn.ac.fage.accessmesh.example.security;

import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.example.enums.ExampleErrorCode;
import cn.ac.fage.accessmesh.example.metadata.dto.MetadataDtos.Caller;
import jakarta.servlet.http.HttpServletRequest;

/** Adapts headers only after the SDK GatewaySignatureFilter has authenticated them. */
public final class ExampleRequestIdentity {
    private ExampleRequestIdentity() {}
    public static Caller read(HttpServletRequest request) {
        try {
            long tenant=Long.parseLong(request.getHeader("X-Tenant-Id"));
            long user=Long.parseLong(request.getHeader("X-User-Id"));
            if (tenant<=0 || user<=0) throw new NumberFormatException();
            return new Caller(tenant,user,request.getHeader("X-Forwarded-For"));
        } catch (NumberFormatException e) {
            throw new BizException(ExampleErrorCode.DEMO_IDENTITY_HEADER_MISSING.getCode(),ExampleErrorCode.DEMO_IDENTITY_HEADER_MISSING.getMessage());
        }
    }
}
