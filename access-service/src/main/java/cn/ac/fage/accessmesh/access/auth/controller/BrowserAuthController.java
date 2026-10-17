package cn.ac.fage.accessmesh.access.auth.controller;

import cn.ac.fage.accessmesh.access.auth.dto.BrowserLoginResp;
import cn.ac.fage.accessmesh.access.infrastructure.dto.EmptyReq;
import cn.ac.fage.accessmesh.access.auth.dto.LoginReq;
import cn.ac.fage.accessmesh.access.auth.dto.PlatformLoginReq;
import cn.ac.fage.accessmesh.access.auth.dto.PlatformBrowserLoginResp;
import cn.ac.fage.accessmesh.access.auth.service.BrowserAuthAppService;
import cn.ac.fage.accessmesh.access.infrastructure.config.BrowserSessionFilter;
import cn.ac.fage.accessmesh.common.model.R;
import cn.ac.fage.accessmesh.common.security.BrowserSessionPolicy;
import cn.ac.fage.accessmesh.common.security.BrowserSessionPolicy.Candidate;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BrowserAuthController {
    private final BrowserAuthAppService service;
    public BrowserAuthController(BrowserAuthAppService service) { this.service = service; }

    @PostMapping(value = "/api/access/auth/browser-login", consumes = "application/json")
    public R<BrowserLoginResp> login(@Valid @RequestBody LoginReq req, HttpServletRequest request, HttpServletResponse response) {
        Candidate candidate = candidate(request);
        var issued = service.login(req);
        writeSession(response, candidate, issued);
        return R.ok(issued.metadata());
    }
    @PostMapping(value = "/api/access/platform-auth/browser-login", consumes = "application/json")
    public R<PlatformBrowserLoginResp> platformLogin(@Valid @RequestBody PlatformLoginReq req, HttpServletRequest request, HttpServletResponse response) {
        Candidate candidate = candidate(request);
        var issued = service.loginPlatform(req);
        writeSession(response, candidate, issued);
        return R.ok(issued.metadata());
    }
    @PostMapping(value = {"/api/access/auth/browser-logout", "/api/access/platform-auth/browser-logout"}, consumes = "application/json")
    public R<Void> logout(@RequestBody EmptyReq req, HttpServletRequest request) {
        Candidate candidate = candidate(request);
        service.logout(candidate.realm(), candidate.token());
        // No Set-Cookie: a late logout response must never overwrite a new login.
        return R.ok();
    }
    private static Candidate candidate(HttpServletRequest request) {
        Object value = request.getAttribute(BrowserSessionFilter.CANDIDATE);
        if (!(value instanceof Candidate candidate)) throw new IllegalStateException("浏览器会话安全适配未执行");
        return candidate;
    }
    private static void writeSession(HttpServletResponse response, Candidate candidate,
                                     BrowserAuthAppService.Issued<?> issued) {
        response.addHeader("Set-Cookie", ResponseCookie.from(BrowserSessionPolicy.cookieName(candidate.realm(), candidate.secure()), issued.token())
            .httpOnly(true).secure(candidate.secure()).sameSite("Strict").path("/").maxAge(issued.expiresIn()).build().toString());
        response.setHeader("Cache-Control", "no-store");
    }
}
