-- T-PERM-054：仅同步接口映射注释至当前契约，可重复执行；不修改列约束、映射或授权数据。
BEGIN;
COMMENT ON TABLE resource_api_mapping IS '接口登记映射：resource_entity_id 引用 API 登记实体，required_operation_id 引用业务准入操作；完整匹配启用路由后同要求去重、不同要求报配置故障；绑定不产生 API 授权，业务服务仍须检查实际目标';
COMMENT ON COLUMN resource_api_mapping.required_operation_id IS '业务准入操作 ID，类型从操作定义取得；共同保存入口要求有效引用，启用映射缺失或损坏引用报 20071 配置故障，不回退默认操作；不得以 API 类型操作作准入要求（API 授权已退役，恒无候选，读侧同报 20071）；有效引用阻止操作删除和位变更';
COMMIT;
