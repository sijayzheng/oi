package cn.sijay.oi.task;

import cn.sijay.oi.sys.job.OiScheduled;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 打印当前时间任务：每 30 秒输出一次系统当前时间。
 *
 * <p>任务身份由 {@code @OiScheduled} 声明（jobKey=sys.printTime，默认固定频率 30 秒）；
 * 调度配置以 {@code sys_job} 表为准（管理界面可在线调整周期，免重启生效）。
 *
 * @author sijay
 */
@Slf4j
@Component
public class PrintTimeTask {

    /**
     * 执行体：打印当前时间（无参方法，异常由执行引擎统一处理）。
     */
    @OiScheduled(jobKey = "sys.printTime", name = "打印当前时间")
    public void printCurrentTime() {
        log.info("[PrintTimeTask] 当前时间: {}", LocalDateTime.now());
    }
}
