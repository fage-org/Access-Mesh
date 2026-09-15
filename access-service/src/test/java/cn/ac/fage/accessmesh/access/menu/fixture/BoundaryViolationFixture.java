package cn.ac.fage.accessmesh.access.menu.fixture;

import cn.ac.fage.accessmesh.access.role.mapper.UserRoleMapper;

/**
 * 架构断言负向自证夹具（Q-009 白名单退役后，T-ACCESS-046）：故意构造「menu 能力包类
 * 直读 role 能力包 mapper」的违规依赖。仅被 {@code QueryBoundaryArchitectureTest}
 * 以专用 ClassFileImporter 导入自证规则有牙——主断言的 DO_NOT_INCLUDE_TESTS 不含
 * 测试源集，本类永不进入主扫描面；禁止在任何测试/生产逻辑中实际调用。
 */
public class BoundaryViolationFixture {

    private final UserRoleMapper userRoleMapper;

    public BoundaryViolationFixture(UserRoleMapper userRoleMapper) {
        this.userRoleMapper = userRoleMapper;
    }

    /** 违规读取点（永不执行）：menu 包 → role.mapper 直读即断言面违规。 */
    public int violationPoint() {
        return userRoleMapper.hashCode();
    }
}
