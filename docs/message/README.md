# 站内信系统（消息中心）

站内信模块提供 **系统通知（NOTICE）**、 **用户私信（LETTER）**、**公告广播（BROADCAST）**三类消息的收发、存储、已读/未读标记、批量操作、分类归档、撤回与 SSE 实时推送能力。

## 一、架构与表结构

### 1.1 存储模型（三表）

| 表                            | 职责                     | 关键设计                                                                                                                                                       |
|-------------------------------|--------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `sys_message`                 | 消息主表（三类消息共用） | `msg_type` 区分类型；`status=WITHDRAWN` 表示已撤回；`send_time` 为撤回时限（5 分钟）基准；`attachment_ids` 存附件文件 id（逗号分隔，统一文件服务）             |
| `sys_message_receipt`         | 点对点回执（收件箱行）   | 系统通知/私信发送时按接收人生成；承载用户侧已读（`read_status`）/归档（`archived`）/删除（`deleted`）状态                                                      |
| `sys_message_broadcast_state` | 广播个性化状态           | **惰性创建**：广播发送时不落行，用户首次标记（已读/归档/删除）才 upsert 单行，`uk_broadcast_state(message_id, user_id)` 唯一约束兜底——万级广播不产生全量行膨胀 |

索引：`idx_msg_receiver(receiver_id, deleted, status)`、`idx_msg_broadcast(msg_type, status, deleted)`、`idx_msg_sender(sender_id, deleted)`、
`idx_receipt_inbox(receiver_id, deleted, archived)`、`idx_receipt_msg(message_id)`、`idx_bstate_user(user_id, deleted)`。

### 1.2 收件箱统一视图

收件箱 = 「点对点回执 JOIN 消息」UNION ALL「全部有效广播 LEFT JOIN 个人状态行（缺失按默认态：未读/未归档/未删除）」，动态过滤（类型/已读/归档/关键词）+ `send_time` 倒序 + LIMIT/OFFSET
分页，服务层基于 EntityManager 原生 SQL 实现（`SysMessageService.inbox`）。归档语义：归档即从默认收件箱移除（接口缺省 `archived=0`），归档箱显式传 `archived=1`。

### 1.3 SSE 实时推送

- 端点：`GET /message/stream?satoken=<token>`（EventSource 无法携带请求头，令牌走查询参数，控制器手动校验，语义等同登录校验；已在 `SaTokenConfigure` 豁免全局拦截）。
- 事件协议：`connected`（初始未读数）/ `message`（新消息摘要 JSON）/ `unread`（未读数权威值）/ `withdraw`（被撤回消息 id）/ `heartbeat`（30 秒心跳保活）。
- 连接模型：进程内 `ConcurrentHashMap<userId, List<SseEmitter>>`，支持同用户多端/多标签页；写失败连接自动摘除，前端 5 秒自动重连。
- **集群部署说明**：单节点内推送为内存直投；多节点部署需将"发送/撤回后的推送广播"改走 Redis Pub/Sub 等跨节点通道（`MessageSseService` 为唯一收口点，替换成本可控）。

## 二、接口文档

统一响应格式：HTTP 200 + `Result{code, message, data, total}`；鉴权：除 SSE 端点外均需 `satoken` 请求头。

| 方法 | 路径                                                                        | 说明                                                                           | 权限                                                 |
|------|-----------------------------------------------------------------------------|--------------------------------------------------------------------------------|------------------------------------------------------|
| GET  | `/message/stream?satoken=`                                                  | SSE 实时推送连接                                                               | 有效令牌（查询参数）                                 |
| GET  | `/message/unread-count`                                                     | 未读数（点对点未读 + 未读广播）                                                | 登录                                                 |
| GET  | `/message/inbox?msgType=&readStatus=&archived=&keyword=&pageNum=&pageSize=` | 收件箱分页（archived 缺省 0）                                                  | 登录                                                 |
| GET  | `/message/sent?msgType=&keyword=&pageNum=&pageSize=`                        | 发件箱分页（含已撤回状态展示）                                                 | 登录                                                 |
| GET  | `/message/{id}`                                                             | 消息详情（接收方首次查看自动已读）                                             | 登录（收发双方，否则 607002）                        |
| POST | `/message`                                                                  | 发送消息，body：`{msgType, receiverUsername?, title, content, attachmentIds?}` | LETTER 登录用户；NOTICE/BROADCAST 仅管理员（607009） |
| PUT  | `/message/read`                                                             | 批量已读，body：`{ids:[消息id]}`                                               | 登录（仅本人消息生效）                               |
| PUT  | `/message/read-all`                                                         | 全部标记已读                                                                   | 登录                                                 |
| PUT  | `/message/archive`                                                          | 归档/取消归档，body：`{ids, archived:0                                         | 1}`                                                  | 登录 |
| PUT  | `/message/trash`                                                            | 批量删除，body：`{ids}`                                                        | 登录                                                 |
| POST | `/message/withdraw/{id}`                                                    | 撤回（发送人本人 + 5 分钟内 + 幂等）                                           | 登录                                                 |

错误码（607 段）：607001 消息不存在 / 607002 无权操作该消息 / 607003 接收用户不存在 / 607004 接收账号已禁用 / 607006 超过撤回时限 / 607007 仅发送人可撤回 /
607008 不能给自己发私信 / 607009 仅管理员可发布。

## 三、前端页面

- 菜单「业务应用 → 站内信」（`/message`，组件 `sys/MessageView`），viewer 角色已授权。
- 顶栏铃铛未读角标：SSE 实时驱动（`stores/message.ts`），断线自动重连，登出自动断开。
- 收件箱/发件箱/归档箱三视图切换、类型与已读过滤、关键词搜索、未读红点加粗、批量已读/归档/删除、全部已读。
- 详情抽屉：附件图片内嵌预览（el-image 放大）、文件链接下载；发送人 5 分钟内可见撤回按钮。
- 写消息对话框：管理员可选系统通知/公告广播，普通用户仅私信；附件经统一文件服务上传（仅存文件 id）。
- 响应式：<768px 时详情抽屉 92% 宽度、过滤器换行堆叠、表格横向滚动。

## 四、测试与性能

- 集成测试：`SysMessageControllerTest`（9 个用例：全链路、发送校验、发布权限、跨用户防护、批量操作、广播惰性状态、撤回全流程、撤回防护）。
- 未读数与收件箱均为索引覆盖的聚合/分页查询；万级消息下单用户收件箱查询目标 <200ms（实测见测试报告）。
- 撤回时限（5 分钟）与锁定规则常量位于 `SysMessageService.WITHDRAW_WINDOW`。

## 五、部署说明

1. 新库：`schema-h2.sql` 已含全部表结构；既有库执行增量脚本（三张表 + 菜单 id=12 + viewer 授权）。
2. `data.sql` 幂等重放包含菜单 12 与 `sys_role_menu(1000,12)` 授权。
3. 生产环境建议：多节点部署接入 Redis Pub/Sub；SSE 经反向代理时确认关闭 `proxy_buffering`（Nginx：`X-Accel-Buffering: no` 已由心跳兜底）。
