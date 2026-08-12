package cn.ac.fage.accessmesh.access.admin.sync.handler;

import cn.ac.fage.accessmesh.access.admin.enums.AdminErrorCode;
import cn.ac.fage.accessmesh.common.exception.BizException;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 同步任务处理器注册表
 * <p>
 * 启动时收集所有 {@link SyncTaskHandler} 实现，按 {@link SyncTaskHandler#supportedAction()}
 * 分组为 {@code Map<syncAction, handler>}，供 {@code SyncTaskScheduler} 路由使用。
 * </p>
 */
@Component
public class SyncTaskHandlerRegistry {

    private final Map<String, SyncTaskHandler> handlers;

    public SyncTaskHandlerRegistry(List<SyncTaskHandler> handlerList) {
        Map<String, SyncTaskHandler> map = new HashMap<>();
        for (SyncTaskHandler handler : handlerList) {
            String action = handler.supportedAction();
            if (action == null || action.isBlank()) {
                throw new IllegalStateException(
                    "SyncTaskHandler.supportedAction must not be blank: " + handler.getClass().getName());
            }
            SyncTaskHandler prev = map.put(action, handler);
            if (prev != null) {
                throw new IllegalStateException(
                    "Duplicate SyncTaskHandler for action " + action
                        + ": " + prev.getClass().getName() + " vs " + handler.getClass().getName());
            }
        }
        this.handlers = Map.copyOf(map);
    }

    /**
     * 解析 syncAction 对应的 handler。
     *
     * @param syncAction 同步动作
     * @return 处理器实例
     * @throws BizException 当未注册对应 handler 时
     */
    public SyncTaskHandler resolve(String syncAction) {
        SyncTaskHandler handler = handlers.get(syncAction);
        if (handler == null) {
            throw new BizException(AdminErrorCode.EXTERNAL_SERVICE_ERROR.getCode(),
                "No SyncTaskHandler registered for action: " + syncAction);
        }
        return handler;
    }
}
