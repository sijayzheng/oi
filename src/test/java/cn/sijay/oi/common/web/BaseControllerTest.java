package cn.sijay.oi.common.web;

import cn.sijay.oi.common.exception.BusinessErrorCode;
import cn.sijay.oi.common.model.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link BaseController} 验证用例：通过测试子类直接调用各 protected 包装方法，
 * 断言返回的 {@link Result} 结构符合约定。
 */
@DisplayName("基础控制器测试")
class BaseControllerTest extends BaseController {

    @Test
    @DisplayName("ok：无数据 / 带数据 / 带分页总数三种成功场景")
    void okScenarios() {
        Result<Void> empty = ok();
        assertThat(empty.code()).isEqualTo(200);
        assertThat(empty.message()).isEqualTo("操作成功");
        assertThat(empty.data()).isNull();
        assertThat(empty.total()).isNull();

        Result<List<String>> withData = ok(List.of("a", "b"));
        assertThat(withData.code()).isEqualTo(200);
        assertThat(withData.data()).containsExactly("a", "b");
        assertThat(withData.total()).isNull();

        Result<List<String>> withPage = ok(List.of("a"), 100L);
        assertThat(withPage.data()).containsExactly("a");
        assertThat(withPage.total()).isEqualTo(100L);
    }

    @Test
    @DisplayName("fail：自定义码+消息 / 仅消息 / 标准错误码枚举 / 异常对象四种场景")
    void failScenarios() {
        Result<Void> custom = fail(400, "参数不合法");
        assertThat(custom.code()).isEqualTo(400);
        assertThat(custom.message()).isEqualTo("参数不合法");
        assertThat(custom.data()).isNull();

        Result<Void> messageOnly = fail("出错了");
        assertThat(messageOnly.code()).isEqualTo(500);
        assertThat(messageOnly.message()).isEqualTo("出错了");

        Result<Void> fromEnum = fail(BusinessErrorCode.USER_USERNAME_EXISTS);
        assertThat(fromEnum.code()).isEqualTo(601001);
        assertThat(fromEnum.message()).isEqualTo("用户名已存在");

        Result<Void> fromException = fail(new IllegalStateException("网络中断"));
        assertThat(fromException.code()).isEqualTo(500);
        assertThat(fromException.message()).isEqualTo("网络中断");
    }
}
