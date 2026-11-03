# 代码生成器使用手册

> 一套基于 FreeMarker 的前后端 CRUD 代码生成体系：导入数据库表 → 微调字段配置 → 一键生成可直接运行的前后端全套代码。

## 一、体系总览

| 组成 | 位置 | 说明 |
| --- | --- | --- |
| 元数据表 | `sql/schema-h2.sql` 中 `gen_table` / `gen_table_column` | 存储表配置与字段配置（类型/校验/组件/查询方式） |
| 元数据服务 | `cn.sijay.oi.gen.service.GenTableService` | 导入表结构、推导字段默认配置、配置维护 |
| 渲染引擎 | `cn.sijay.oi.gen.service.GenRenderService` | FreeMarker 渲染 + 落盘 + 前端路由/菜单锚点注册 |
| 管理接口 | `cn.sijay.oi.gen.controller.GenTableController` | `/gen/db/tables`、`/gen/table/*` |
| 管理页面 | `vue/src/views/system/GenView.vue`（菜单：代码生成） | 导入/编辑/预览/生成全流程操作 |
| 代码模板 | `src/main/resources/templates/gen/*.ftl` | 12 个 FreeMarker 模板（见模板开发文档） |
| 集成测试 | `src/test/java/cn/sijay/oi/gen/service/GenServiceTest.java` | 导入推导与渲染产物断言 |

## 二、支持场景

### 1. 单表 CRUD（模板 `view.ftl`）
生成后端 8 类文件 + 前端 3 类文件，接口为标准五接口（创建/删除/更新/详情/分页），
页面为标准管理页（搜索/分页/排序/列设置/批量删除/新增/编辑对话框）。

### 2. 主子表关联（模板 `view-master-detail.ftl`）
在**子表**配置上指定 `parent_table_id`（主表配置 id）与 `fk_column`（本表外键列）后：
- **主表**自动识别为双栏页场景：页面渲染为左主表 + 右子表联动（参考字典管理页）；
- **主表** Service 生成级联软删逻辑（删除主表时同事务软删子表关联行）；
- **子表** Repository 额外生成 `cascadeSoftDeleteByXxx` 方法；
- **子表**按普通单表生成（其 Query 含外键列，供右栏按主表过滤加载）。

## 三、操作步骤（五步生成）

1. **建表**：在 `sql/schema-h2.sql` 追加业务表 DDL。
   - 表注释与列注释用 `COMMENT ON TABLE/COLUMN` 书写（导入时自动映射为字段注释/表单标签）；
   - 惯例列：`id BIGINT IDENTITY`、`create_by/update_by BIGINT`、
     `create_time/update_time TIMESTAMP`、`deleted BIGINT DEFAULT 0`（生成器自动识别并排除出表单/查询）；
   - 必填/列表/查询默认值由导入自动推导：NOT NULL **且无默认值**的列才默认必填
     （有 `DEFAULT` 的列默认选填）；`VARCHAR(1000)` 及以上长文本默认不进列表；
     时间列默认不进查询条件。
2. **导入**：菜单「代码生成」→ 导入表 → 点击目标表行（自动推导字段配置）；
3. **微调**：行操作「编辑」→ 修正类名主干/模块/业务名/功能名，按需调整各列的
   注释、Java 字段名、Java/TS 类型、表单组件、必填/列表/查询开关、最大长度、
   静态选项 JSON（select/radio）或字典编码、校验正则 → 保存；
4. **生成**：行操作「生成」→ 前后端文件写入项目目录（覆盖同名文件），
   同时自动注册前端路由（`vue/src/router/index.ts`）与侧边栏菜单
   （`vue/src/layouts/AdminLayout.vue`），重复生成幂等；
5. **生效**：重启后端（编译新代码），前端刷新即可使用新页面（HMR 自动生效）。

> 建议先「预览」检查产物再「生成」。

## 四、生成产物清单

| 产物 | 路径 |
| --- | --- |
| 实体 | `src/main/java/cn/sijay/oi/<module>/entity/Sys<Xxx>.java` |
| Repository | `src/main/java/cn/sijay/oi/<module>/repository/Sys<Xxx>Repository.java` |
| Service | `src/main/java/cn/sijay/oi/<module>/service/Sys<Xxx>Service.java` |
| Controller | `src/main/java/cn/sijay/oi/<module>/controller/Sys<Xxx>Controller.java` |
| 入参/查询/出参模型 | `src/main/java/cn/sijay/oi/<module>/model/<biz>/Sys<Xxx>{CreateCmd,UpdateCmd,Query,VO}.java` |
| 前端类型 | `vue/src/types/<biz>.d.ts` |
| 前端 API | `vue/src/api/<biz>.ts` |
| 前端页面 | `vue/src/views/<module>/<Xxx>View.vue` |

## 五、字段配置项说明

| 配置 | 生成效果（后端 / 前端） |
| --- | --- |
| 字段注释 | Java 字段 JavaDoc / 表单 label、搜索 label、表格列头 |
| Java 类型 | 实体/模型字段类型；BigDecimal、LocalDateTime 自动补 import |
| TS 类型 | 前端 interface 字段类型（string/number/boolean） |
| 必填 | `@NotBlank`/`@NotNull` / 前端 `required` 规则 |
| 最大长度 | `@Size(max)` / 前端 `max` 长度规则与 `maxlength` 属性 |
| 校验正则 | `@Pattern` / 前端 `pattern` 规则 |
| 最小值 | 数值列 `@Min(0)` / 前端 `type:'number', min` 规则与 `el-input-number :min` |
| 表单组件 | input/textarea/select/radio/switch/number/datetime 七类控件与列表展示样式 |
| 静态选项 JSON | select/radio 的选项常量（格式 `[{"value":"0","label":"通知"}]`） |
| 字典编码 | select/radio 选项运行时从 `/dict/item/options/{dictType}` 加载（优先于静态选项） |
| 查询开关 + 方式 | 是否进入 Query 模型与搜索表单；LIKE 走 `CONTAINING` 模糊匹配 |

## 六、常见问题

- **生成后接口 404**：后端需重启（生成的是源码，需重新编译）。
- **搜索区没有出现**：所有列的「查询」开关都关闭时，页面自动省略搜索筛选区（模板条件生成）；
  需要搜索请在字段配置中打开对应列的查询开关后重新生成。
- **必填/列表开关与预期不符**：默认值由 DDL 推导——有 `DEFAULT` 的列默认选填、
  长文本列默认不进列表；可在「编辑」中调整后重新生成。
- **菜单未出现**：锚点插入仅在路由/菜单文件包含目标路径标记时跳过；
  若锚点行被手工改动，按 `vue/src/router/index.ts` 现有路由块手工补一行即可。
- **需要模块专属错误码**：生成代码默认使用通用码 600002（数据不存在）/600003（已存在），
  需要专属码时在 `BusinessErrorCode` 对应模块段追加并替换生成代码。
- **重复导入提示 600003**：同一表只能导入一次；如需重置请先删除该行配置。
