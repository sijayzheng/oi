package cn.sijay.oi.common.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link BusinessErrorCode} 验证用例。
 *
 * <p>覆盖：全部错误码唯一性、业务码区间合法性、模块分段正确性、
 * 描述信息非空与准确性、{@link BusinessException} 枚举构造兼容性。
 */
@DisplayName("业务失败码枚举测试")
class BusinessErrorCodeTest {

    @Test
    @DisplayName("唯一性：所有枚举常量的错误码互不重复")
    void allCodesAreUnique() {
        BusinessErrorCode[] codes = BusinessErrorCode.values();
        Set<Integer> seen = new HashSet<>();

        for (BusinessErrorCode errorCode : codes) {
            assertThat(seen.add(errorCode.getCode()))
                    .as("错误码 %d 重复（%s）", errorCode.getCode(), errorCode.name())
                    .isTrue();
        }
        assertThat(seen).hasSize(codes.length);
    }

    @ParameterizedTest
    @EnumSource(BusinessErrorCode.class)
    @DisplayName("区间合法性：错误码位于业务段 600000~699999，描述非空")
    void codeInRangeAndMessageNotBlank(BusinessErrorCode errorCode) {
        assertThat(errorCode.getCode())
                .isGreaterThanOrEqualTo(BusinessErrorCode.BASE_CODE)
                .isLessThan(BusinessErrorCode.BASE_CODE + 100 * BusinessErrorCode.SEGMENT_SIZE);
        assertThat(errorCode.getMessage()).isNotBlank();
    }

    @Test
    @DisplayName("分段正确性：错误码 = 600000 + 模块段×1000 + 序号，且与声明分段一致")
    void codeFollowsHierarchicalFormula() {
        for (BusinessErrorCode errorCode : BusinessErrorCode.values()) {
            int expected = BusinessErrorCode.BASE_CODE
                    + errorCode.getModuleSegment() * BusinessErrorCode.SEGMENT_SIZE
                    + errorCode.getSequence();
            assertThat(errorCode.getCode())
                    .as("%s 的错误码不符合层级化编码公式", errorCode.name())
                    .isEqualTo(expected);
            assertThat(errorCode.getSequence())
                    .as("%s 的错误序号应位于 1~999", errorCode.name())
                    .isBetween(1, 999);
        }
    }

    @Test
    @DisplayName("描述准确性：抽查各模块代表常量的错误码与描述")
    void spotCheckKnownCodes() {
        assertThat(BusinessErrorCode.COMMON_PARAM_ERROR.getCode()).isEqualTo(600001);
        assertThat(BusinessErrorCode.COMMON_PARAM_ERROR.getMessage()).isEqualTo("请求参数错误");

        assertThat(BusinessErrorCode.USER_USERNAME_EXISTS.getCode()).isEqualTo(601001);
        assertThat(BusinessErrorCode.USER_USERNAME_EXISTS.getMessage()).isEqualTo("用户名已存在");

        assertThat(BusinessErrorCode.ROLE_CODE_EXISTS.getCode()).isEqualTo(602002);
        assertThat(BusinessErrorCode.ROLE_CODE_EXISTS.getMessage()).isEqualTo("角色编码已存在");

        assertThat(BusinessErrorCode.MENU_HAS_CHILDREN.getCode()).isEqualTo(603002);
        assertThat(BusinessErrorCode.MENU_HAS_CHILDREN.getMessage()).isEqualTo("存在子菜单，无法删除");

        assertThat(BusinessErrorCode.CONFIG_KEY_EXISTS.getCode()).isEqualTo(604001);
        assertThat(BusinessErrorCode.CONFIG_KEY_EXISTS.getMessage()).isEqualTo("参数键已存在");

        assertThat(BusinessErrorCode.DICT_TYPE_EXISTS.getCode()).isEqualTo(605001);
        assertThat(BusinessErrorCode.DICT_TYPE_EXISTS.getMessage()).isEqualTo("字典类型已存在");
    }

    @Test
    @DisplayName("兼容性：BusinessException 可直接接收枚举并透传错误码与描述")
    void businessException_acceptsErrorCode() {
        BusinessException e = new BusinessException(BusinessErrorCode.USER_USERNAME_EXISTS);

        assertThat(e.getCode()).isEqualTo(601001);
        assertThat(e.getMessage()).isEqualTo("用户名已存在");
    }

    @Test
    @DisplayName("兼容性：全部枚举常量均可构建 BusinessException")
    void businessException_acceptsAllEnumConstants() {
        Arrays.stream(BusinessErrorCode.values()).forEach(errorCode -> {
            BusinessException e = new BusinessException(errorCode);
            assertThat(e.getCode()).isEqualTo(errorCode.getCode());
            assertThat(e.getMessage()).isEqualTo(errorCode.getMessage());
        });
    }
}
