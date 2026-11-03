# 代码生成器扩展指南

> 面向后续演进：新增模板场景、新增表单组件、扩展推导规则、接入真实数据库的改造路径。

## 一、新增表单组件类型

以新增「富文本（richtext）」为例：

1. `GenTableColumn.htmlType` 约束放开：实体注释与前端 `types/gen.d.ts` 的 `htmlType`
   联合类型加入 `richtext`（数据库列宽 `VARCHAR(20)` 无需变更）；
2. `view.ftl` / `view-master-detail.ftl` 的表单宏位置各加一个分支：
   ```ftl
   <#elseif col.htmlType == 'richtext'>
           <el-input v-model="createForm.${col.javaField}" type="textarea" :rows="8" />
   ```
   建议抽公共宏文件（`<#import 'control-macro.ftl' as m>`）消除两份重复；
3. 列表展示按文本截断处理（textarea 分支已有 `show-overflow-tooltip`，同法）；
4. 更新 `GenView.vue` 的 `htmlTypeOptions` 候选与本文档。

## 二、新增校验规则类型

以「邮箱」为例（当前由正则覆盖，若需专用注解）：

1. `gen_table_column` 加 `format VARCHAR(20)` 列 + 实体/保存 Cmd/前端类型同步；
2. 导入推导：注释含"邮箱"→ `format = 'email'`；
3. `create-cmd.ftl` / `update-cmd.ftl`：`<#if col.format == 'email'>@Email(message = "${col.label}格式不正确")</#if>`；
4. `view.ftl` 规则数组：`{ type: 'email', message: '...', trigger: ['blur','change'] }`。

## 三、新增生成场景（如树形表）

以「树形表」（参考 sys_menu：parent_id 层级 + 树接口）为例：

1. `gen_table` 加 `treeFlag TINYINT`（或约定：存在 `parent_id` 列即树形表）；
2. `GenRenderService.buildRenderModel` 按标志注入 `tree=true`；
3. 新增模板：
   - `repository-tree.ftl`：`countByParentId`、`findByParentId`；
   - `service-tree.ftl`：层级校验 + `tree()` 方法（`TreeNode.buildTree`）；
   - `view-tree.ftl`：树形表格（`row-key` + `:tree-props`）或双栏（参考菜单管理页）；
4. `buildFiles()` 按标志选择模板并补充产物（如 `GET /<biz>/tree` 接口）；
5. 在 `GenServiceTest` 增加树形表演示表（如 `sys_region`）断言。

## 四、扩展推导规则

导入推导集中在 `GenTableService.deriveColumns()`：

- **类型映射**：`TYPE_MAPPINGS` 常量（key = JDBC `TYPE_NAME` 大写），
  新数据库类型（如 `JSONB`）在此登记 Java/TS 映射；
- **系统列识别**：`isAuditTime` / `isSystemHidden` 判断（create_by/update_by/create_time/
  update_time/deleted），新增项目级惯例列在此扩展；
- **表单组件推导**：`deriveHtmlType()`（数值/布尔/时间/长文本/关键词）；
- **查询方式推导**：String→LIKE、其余→EQ，时间列强制不参与查询；
- **必填/列表推导**：NOT NULL 且无默认值 → 必填；`VARCHAR(1000)` 及以上长文本
  默认不进列表；调整规则时同步更新使用手册「建表」一节的说明。

## 五、接入 MySQL/生产库

当前导入元数据走 JDBC `DatabaseMetaData`（跨库标准 API），理论天然兼容 MySQL；
需要关注的差异：

1. MySQL 8 建议连接串追加 `nullCatalogMeansCurrent=true`，并在 `getColumns`/
   `getTables` 传 `catalog`（数据库名）而非依赖 schema；
2. MySQL `information_schema` 的 REMARKS 需连接参数 `useInformationSchema=true`
   才返回列注释；
3. 类型映射差异：`TINYINT(1)`→Boolean、`DATETIME`→LocalDateTime、`UNSIGNED INT`→Long，
   在 `TYPE_MAPPINGS` 登记；
4. 落盘目录：多环境部署时建议将 `GenRenderService.generate` 的项目根改为可配置项
   （`gen.output-root`），并支持 ZIP 下载（替换直接写盘）。

## 六、生成代码的二次定制边界

| 情况 | 处理 |
| --- | --- |
| 业务唯一键校验 | 在生成 Service 的 create/update 中加 `existsBy...` 前置校验（对齐 SysRoleService 模式） |
| 模块专属错误码 | `BusinessErrorCode` 追加常量后替换生成代码中的通用码 |
| 字段枚举化（如 status） | 生成 enum + 实体 `@Enumerated(EnumType.STRING)` + 前端选项走字典 |
| 接口路径/权限 | 路径由 `businessName` 驱动；鉴权接入 sa-token 后在 Controller 补注解 |

## 七、质量门禁

- 修改模板后必须通过：`mvnw test -Dtest=GenServiceTest`（渲染断言）+ 全量 `mvnw test`
  （生成物参与编译）；
- 前端模板改动后运行 `pnpm type-check && pnpm lint`（先对生成产物执行一次生成验证）；
- 端到端验收：生成 → 重启 → 浏览器 CRUD 全链路（参见使用手册第三节）。
