package cn.sijay.oi.sys.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 站内信 SSE 实时推送服务。
 *
 * <p><b>连接模型</b>：每个登录用户可同时持有多个 SSE 连接（多标签页/多端），
 * 以 userId → 连接列表组织；推送时向该用户的全部连接投递，任一连接写入失败
 * 即摘除（浏览器断开后由前端自动重连）。
 *
 * <p><b>事件协议</b>（event 名 → data 语义）：
 * <ul>
 *   <li>{@code connected} —— 连接建立成功，data 为初始未读数；</li>
 *   <li>{@code message} —— 新消息到达，data 为消息摘要 JSON；</li>
 *   <li>{@code unread} —— 未读数变更，data 为最新未读数；</li>
 *   <li>{@code withdraw} —— 消息被撤回，data 为消息 id；</li>
 *   <li>{@code heartbeat} —— 心跳保活（无 data），默认每 30 秒一次，
 *       用于穿透反向代理的空闲超时。</li>
 * </ul>
 *
 * <p><b>规模说明</b>：单节点内连接保存在进程内存（万级连接为
 * {@code ConcurrentHashMap} 轻量条目，推送为顺序写事件流）；集群部署时
 * 需将"推送广播"替换为 Redis Pub/Sub 等跨节点通道（见 docs/message/README.md）。
 *
 * @author sijay
 */
@Slf4j
@Component
public class MessageSseService {

    /**
     * 在线连接表：userId → 该用户的全部活跃连接（多端/多标签页）。
     */
    private final Map<Long, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    /**
     * 建立连接：注册 emitter 并立即推送连接成功事件（携带初始未读数）。
     *
     * <p>emitter 超时设置为 0L（永不超时），保活依赖服务端心跳与前端重连；
     * 连接完成/超时/出错时自动从注册表摘除。
     *
     * @param userId             用户 id
     * @param initialUnreadCount 连接建立时的未读数（角标初始化）
     * @return 已注册的 emitter（控制器直接返回给异步上下文）
     */
    public SseEmitter connect(Long userId, long initialUnreadCount) {
        SseEmitter emitter = new SseEmitter(0L);
        List<SseEmitter> list = emitters.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>());
        list.add(emitter);
        Runnable remove = () -> {
            list.remove(emitter);
            if (list.isEmpty()) {
                emitters.remove(userId, list);
            }
        };
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(e -> remove.run());
        try {
            emitter.send(SseEmitter.event().name("connected").data(String.valueOf(initialUnreadCount)));
        } catch (IOException e) {
            log.warn("[MessageSse] 连接建立后首推失败，摘除连接: userId={}", userId);
            remove.run();
        }
        log.info("[MessageSse] SSE 连接建立: userId={}, 在线连接数={}", userId, list.size());
        return emitter;
    }

    /**
     * 向指定用户的全部在线连接推送事件。
     *
     * @param userId 用户 id
     * @param event  事件名（message / unread / withdraw）
     * @param data   事件数据（字符串）
     */
    public void pushToUser(Long userId, String event, String data) {
        List<SseEmitter> list = emitters.get(userId);
        if (list == null || list.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event().name(event).data(data));
            } catch (Exception e) {
                // 单连接失败（对端断开）不影响同用户其他连接；emitter 回调会自动摘除
                list.remove(emitter);
            }
        }
    }

    /**
     * 向全部在线用户广播事件（公告广播/撤回场景）。
     *
     * @param event 事件名
     * @param data  事件数据（字符串）
     */
    public void broadcast(String event, String data) {
        emitters.keySet().forEach(userId -> pushToUser(userId, event, data));
    }

    /**
     * 当前在线用户数（运维观测用）。
     */
    public int onlineUserCount() {
        return emitters.size();
    }

    /**
     * 心跳保活：每 30 秒向全部在线连接发送 heartbeat 事件，
     * 穿透 Nginx 等反向代理的空闲连接超时；写失败的连接由回调自动摘除。
     */
    @Scheduled(fixedDelay = 30_000)
    public void heartbeat() {
        if (emitters.isEmpty()) {
            return;
        }
        emitters.forEach((userId, list) -> {
            for (SseEmitter emitter : list) {
                try {
                    emitter.send(SseEmitter.event().name("heartbeat").data(""));
                } catch (Exception e) {
                    list.remove(emitter);
                }
            }
        });
    }
}
