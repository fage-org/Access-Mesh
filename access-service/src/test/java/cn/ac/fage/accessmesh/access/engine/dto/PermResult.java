package cn.ac.fage.accessmesh.access.engine.dto;

/**
 * X04 退役锁负向自证夹具（T-PERM-092）：故意占用已退役 FQCN
 * {@code cn.ac.fage.accessmesh.access.engine.dto.PermResult}，证明
 * {@code QueryBoundaryArchitectureTest} 的旧执行体退役锁对主源码复活有牙。
 * <p>
 * 仅存在于测试源集（主扫描面 {@code DO_NOT_INCLUDE_TESTS} 永不导入）；
 * 由退役锁的负向自证用例经专用 ClassFileImporter 单独导入断言必红
 * （{@code menu.fixture.BoundaryViolationFixture} 同款形态）。
 * </p>
 */
public class PermResult {
}
