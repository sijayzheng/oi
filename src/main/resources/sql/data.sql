-- ========== 初始化数据 ==========
-- 业务含义：系统初始化数据（动态路由种子菜单 + 种子账号 + 角色授权关联），
--   与 schema-h2.sql 配套使用：先执行 schema（建表）再执行本文件（灌数据）。
-- 使用方式：spring.sql.init.mode=never（项目约定），本文件由人工通过 H2 RunScript 等工具手动执行；
--   测试环境（mode=always）仅执行 schema-locations，不会自动加载本文件。
-- 幂等性：前置 DELETE 后显式指定主键插入，可重复执行；各表主键固定
--   （菜单 1~9、用户 1/38、角色 1000），重建后关联数据自动恢复有效。
-- 密码说明：admin/viewer 的初始密码均为 123456（列中为 BCrypt 密文，严禁明文入库），
--   生产环境必须在首次登录后立即修改。
-- 自举说明：AdminUserInitializer 仅在系统无用户时创建种子 admin；
--   本文件已提供 admin/viewer，应用启动时初始化器自动跳过，二者行为一致。
-- 范围说明：sys_dict_* / sys_config / sys_notice / sys_log 为业务运行数据，不属于初始化范围。

-- ========== 数据清理（幂等重放前置；无物理外键，顺序仅作规范） ==========
DELETE FROM sys_user_role;
DELETE FROM sys_role_menu;
DELETE FROM sys_user;
DELETE FROM sys_role;
DELETE FROM sys_menu;

-- ========== 菜单（动态路由与左侧导航数据源） ==========
INSERT INTO sys_menu (id, parent_id, name, type, path, component, perms, icon, sort, visible, enabled, deleted) VALUES
  (1, 0, '系统管理', 'DIRECTORY', '/system',      NULL,               NULL, 'Setting',   1, TRUE, TRUE, 0),
  (2, 1, '用户管理', 'MENU',      '/system/user', 'system/UserView',  NULL, 'User',      1, TRUE, TRUE, 0),
  (3, 1, '角色管理', 'MENU',      '/system/role', 'system/RoleView',  NULL, 'Avatar',    2, TRUE, TRUE, 0),
  (4, 1, '菜单管理', 'MENU',      '/system/menu', 'system/MenuView',  NULL, 'Menu',      3, TRUE, TRUE, 0),
  (5, 1, '字典管理', 'MENU',      '/system/dict', 'system/DictView',  NULL, 'Collection', 4, TRUE, TRUE, 0),
  (6, 1, '参数配置', 'MENU',      '/system/config', 'system/ConfigView', NULL, 'Setting', 5, TRUE, TRUE, 0),
  (7, 0, '业务应用', 'DIRECTORY', '/apps',        NULL,               NULL, 'Files',     2, TRUE, TRUE, 0),
  (8, 7, '通知公告', 'MENU',      '/sys/notice',  'sys/NoticeView',   NULL, 'Bell',      1, TRUE, TRUE, 0),
  (9, 7, '代码生成', 'MENU',      '/system/gen',  'system/GenView',   NULL, 'MagicStick', 2, TRUE, TRUE, 0),
  (10, 1, '在线用户', 'MENU',      '/monitor/online', 'monitor/OnlineUserView', NULL, 'Monitor', 6, TRUE, TRUE, 0),
  (11, 1, '日志管理', 'MENU',      '/system/log',  'system/LogView',   NULL, 'Document',  7, TRUE, TRUE, 0),
  (12, 7, '站内信',   'MENU',      '/message',     'sys/MessageView',  NULL, 'ChatDotRound', 3, TRUE, TRUE, 0);

-- 显式主键插入后重置自增序列，避免后续新增菜单时主键冲突
ALTER TABLE sys_menu ALTER COLUMN id RESTART WITH 100;

-- ========== 账号（超管 admin + 动态路由验证账号 viewer） ==========
INSERT INTO sys_user (id, username, password, nickname, mobile, email, enabled, remark, create_by, deleted) VALUES
  (1,  'admin',  '$2a$10$AKOiPNAloCJQCX7sXNwn0.dLg3UGo/9Fh.gTDPNYMqJm7AiTSXCGO', '管理员', NULL, NULL, TRUE, NULL, 0, 0),
  (38, 'viewer', '$2a$10$pMYYoNKFzOaYLEun1TyJ9.R.XhYlBiSVgc9i4xrmx45TsmIBlMNFW', 'Viewer', NULL, NULL, TRUE, NULL, 0, 0);

ALTER TABLE sys_user ALTER COLUMN id RESTART WITH 100;

-- ========== 角色（访客角色：仅授权参数配置与通知公告，用于动态路由权限验证） ==========
INSERT INTO sys_role (id, code, name, sort, enabled, remark, create_by, deleted) VALUES
  (1000, 'viewer', '访客角色', 1, TRUE, '动态路由验证：仅授权参数配置与通知公告', 0, 0);

-- 注意：RESTART WITH 的值即下一行自增 id。角色 1000 已被 viewer 占用，
-- 必须从 1001 开始，否则首个新建角色主键冲突（唯一约束 23505 → 系统异常）
ALTER TABLE sys_role ALTER COLUMN id RESTART WITH 1001;

-- ========== 授权关联（用户-角色 / 角色-菜单） ==========
INSERT INTO sys_user_role (user_id, role_id) VALUES
  (38, 1000);

-- viewer 授权：两个目录（1/7）+ 参数配置（6）+ 通知公告（8）+ 站内信（12）；
-- 目录 7 下未授权的代码生成（9）不出现在其菜单树中，直达路由由前端 404 拦截
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
  (1000, 1),
  (1000, 6),
  (1000, 7),
  (1000, 8),
  (1000, 12);
