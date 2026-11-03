# FreeMarker 模板开发文档

> 面向模板维护者：说明渲染数据模型、模板清单、语法约定与调试方法。

## 一、渲染链路

```
GenTableController
  → GenTableService（导入/配置：gen_table + gen_table_column 元数据）
  → GenRenderService.buildRenderModel()   # 构建 Map 数据模型
  → render(templateName, model)           # FreeMarker 渲染（classpath:/templates/gen/）
  → buildFiles()                          # 组装 11~12 个产物（预览返回 / 生成写盘）
  → insertFrontendAnchors()               # 路由/菜单锚点注册（仅落盘时）
```

## 二、渲染数据模型（模板可用变量）

### 表级变量

| 变量 | 类型 | 说明 |
| --- | --- | --- |
| `tableName` | String | 业务表名（如 `sys_notice`） |
| `tableComment` | String | 表注释 |
| `className` | String | 类名主干（`Notice`） |
| `entityName` | String | 实体类名（`Sys` + className） |
| `moduleName` / `businessName` | String | 模块名 / 业务名 |
| `functionName` | String | 功能名（优先取表注释） |
| `author` | String | 署名 |
| `hasBigDecimal` / `hasLocalDateTime` | boolean | 是否存在对应类型列（决定 import） |
| `isChildTable` | boolean | 本表是否为子表（有 parent_table_id） |
| `fkJavaField` | String | 子表外键列 Java 字段名（仅子表） |
| `masterDetail` | boolean | 本表是否为主子表场景的主表（存在子表配置） |
| `child` | Map | 主子表场景的子表视图（见下） |

### 列视图变量

四个过滤后的列集合：`queryColumns` / `insertColumns` / `editColumns` / `listColumns`，
以及全量 `columns`。每个列元素（`Map<String, Object>`）字段：

| 键 | 说明 |
| --- | --- |
| `columnName` / `javaField` | 列名 / 驼峰字段名 |
| `label` | 显示名（列注释，空则回退字段名） |
| `javaType` / `tsType` | Java 类型 / TS 类型 |
| `pk` / `required` / `insert` / `edit` / `list` / `query` | 各开关（Boolean） |
| `auditTime` / `systemHidden` | 审计时间列 / 系统隐藏列（create_by/update_by/deleted） |
| `likeQuery` | 查询方式为 LIKE |
| `htmlType` | 表单组件（input/textarea/select/radio/switch/number/datetime） |
| `optionsJson` / `dictType` | 静态选项 JSON / 字典编码 |
| `isString` / `isNumber` / `isDate` | 类型派生标记 |
| `hasRegex` / `hasMax` / `hasMin` / `regex` / `maxLength` / `minLength` | 校验约束 |

`masterDetail=true` 时 `child` 变量可用：`businessName` / `functionName` / `moduleName` /
`className` / `entityName` / `fkJavaField` / `columns` / `listColumns` / `insertColumns` / `editColumns`。

## 三、模板清单

| 模板 | 产物 | 场景 |
| --- | --- | --- |
| `entity.ftl` | 实体（软删/审计注解） | 通用 |
| `repository.ftl` | Repository（softDelete；子表加级联软删） | 通用 |
| `service.ftl` | Service（CRUD + QBE；主表加级联删除） | 通用 |
| `controller.ftl` | Controller（标准五接口） | 通用 |
| `create-cmd.ftl` / `update-cmd.ftl` | 创建/更新入参（校验注解） | 通用 |
| `query.ftl` | 查询入参 | 通用 |
| `vo.ftl` | 出参 VO + from() | 通用 |
| `types.ftl` | 前端类型 `types/<biz>.d.ts` | 通用 |
| `api.ftl` | 前端 API `api/<biz>.ts` | 通用 |
| `view.ftl` | 单表管理页 | 单表 |
| `view-master-detail.ftl` | 主子表双栏管理页 | 主子表主表 |

## 四、语法与风格约定

1. **转义**：生成前端代码中的模板字符串需转义插值——`${'$'}{id}` 输出字面 `${id}`；
   生成 Java 代码避免使用 `${}`（用字符串拼接）。
2. **循环过滤**：优先 `<#list columns as col><#if !col.pk>` 形式（兼容 FreeMarker 2.3.32）；
   跳过项用 `<#continue>`。
3. **校验注解生成**：严格对齐项目 Bean Validation 约定——
   String 必填 `@NotBlank`、非 String 必填 `@NotNull`、长度 `@Size`、正则 `@Pattern`、
   数值下限 `@Min`；前端规则一一对应（required/min-max/pattern/type:number+min）。
4. **代码风格**：产物必须与手写模块（如 SysConfig 全套、DictView）逐行同构——
   中文 JavaDoc、2 空格缩进、jspecify 注解、`@SQLRestriction`、QBE `CONTAINING`、
   `BaseController` 响应包装、`Result` 统一返回。
5. **FreeMarker 配置**：`Configuration.VERSION_2_3_32`，模板目录
   `classpath:/templates/gen`，UTF-8，**数字格式必须为 `computer`**（默认 `number`
   会输出千分位逗号，`2000` → `2,000` 污染生成的注解与代码）；新增模板无需注册，
   按文件名直接 `render()`。
6. **条件产物**：模板内按数据模型条件生成——如 `view.ftl` 在 `queryColumns` 为空时
   同时省略搜索表单与 `query`/`search`/`reset` 解构，保证产物零 lint 告警；
   新模板应遵循同一原则（未使用的变量/导入必须条件化）。

## 五、调试方法

1. 改模板后运行 `GenServiceTest`（`mvnw test -Dtest=GenServiceTest`）：
   导入 `sys_notice` → 渲染断言，产物内容在断言失败消息中完整输出，可快速定位模板错误。
   注意该测试显式配置 `spring.jpa.hibernate.ddl-auto=none`——内嵌 H2 的默认
   `create-drop` 会用实体重建表并**丢弃 COMMENT ON 注释**，而注释是字段配置推导
   与断言的数据来源，勿移除该配置；
2. 页面预览：管理页「预览」对话框直接查看渲染结果（不落盘）；
3. 渲染异常统一抛 `GEN_RENDER_FAILED(609001)`，查看后端日志堆栈中的 FreeMarker
   `TemplateException` 定位模板行号。

## 六、落盘与锚点机制

- 项目根目录 = 应用工作目录（`mvnw spring-boot:run` 时即项目根）；
- 产物按「相对路径 → 内容」写入，同名覆盖（幂等）；
- 落盘后执行两个锚点插入（幂等，已包含目标路径标记则跳过）：
  - `vue/src/router/index.ts`：锚点行 `          path: 'system/config',`，在其路由块后插入新路由块；
  - `vue/src/layouts/AdminLayout.vue`：锚点行 `  { index: '/system/config', title: '参数配置', icon: Setting },`，
    在其后插入新菜单项（图标固定复用 `Setting`，已在该文件 import）；
- 锚点行被改动会导致插入静默跳过（不阻断生成），此时人工补注册即可。
