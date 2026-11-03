# Vue 项目依赖添加与基础配置方案

## 摘要

在 `e:\sijay\oi\vue` 的 Vue 3 + Vite + TS 脚手架中新增 Element Plus（按需自动导入）、Pinia 持久化、alova、VueUse、dayjs、qs 及配套构建/ESLint 依赖，并完成可运行的初始化配置：Vite 插件、main.ts 接入、alova 实例、ESLint 切换为 @antfu/eslint-config，同时按你的选择将缺少视图文件的路由简化为单条内联路由，使 `type-check` / `build` 恢复可用。

## 现状分析

- [package.json](file:///e:/sijay/oi/vue/package.json)：仅 `pinia`、`vue`、`vue-router` 三个运行时依赖；devDependencies 为 Vite 8 / ESLint 10 / oxlint 体系。
- [vite.config.ts](file:///e:/sijay/oi/vue/vite.config.ts)：仅启用 `vue()`、`vueJsx()`、`vueDevTools()`，并配置 `@` → `./src` 别名；没有任何自动导入插件。
- [main.ts](file:///e:/sijay/oi/vue/src/main.ts)：仅注册 Pinia 与 Router，未挂载 Element Plus / 持久化插件。
- [eslint.config.ts](file:///e:/sijay/oi/vue/eslint.config.ts)：基于 `@vue/eslint-config-typescript` + `eslint-plugin-vue` + `eslint-plugin-oxlint`，与 `@antfu/eslint-config` 属两套体系，按你的选择整体替换。
- [router/index.ts](file:///e:/sijay/oi/vue/src/router/index.ts)：引用了 `../views/HomeView.vue` 与 `../views/AboutView.vue`，但 `src/views` 为空目录，当前项目无法通过 `pnpm build`（含 `vue-tsc --build`）。
- [tsconfig.app.json](file:///e:/sijay/oi/vue/tsconfig.app.json)：`include` 为 `env.d.ts` + `src/**/*`，`src` 下生成的 `.d.ts` 会自动纳入类型检查；`tsconfig.node.json` 已包含 `eslint.config.*`。
- 环境：Node `v24.22.1`、pnpm `10.33.1`、registry `https://registry.npmjs.org/`。
- 已核实的目标版本：`alova 3.5.5`、`element-plus 2.14.7`、`@element-plus/icons-vue 2.3.2`、`pinia-plugin-persistedstate 4.7.1`（peer `pinia >=3`，本项目 pinia 4 满足）、`@vueuse/core 15.0.0`、`dayjs 1.11.23`、`qs 6.17.1`、`unplugin-auto-import 21.1.0`、`unplugin-vue-components 32.1.0`、`unplugin-element-plus 0.11.2`、`sass 1.107.0`、`@antfu/eslint-config 10.0.0`（peer `eslint ^9.10 || ^10`，本项目 eslint 10 满足）、`@typescript-eslint/eslint-plugin|parser 8.72.1`。

## 变更清单

### 1. `package.json` 依赖

在 `vue` 目录执行（pnpm 会自动写入 `^` 版本区间）：

```bash
pnpm add alova element-plus @element-plus/icons-vue pinia-plugin-persistedstate @vueuse/core dayjs qs
pnpm add -D unplugin-auto-import unplugin-vue-components unplugin-element-plus sass @antfu/eslint-config @typescript-eslint/eslint-plugin @typescript-eslint/parser
pnpm remove @vue/eslint-config-typescript eslint-plugin-vue vue-eslint-parser
```

预期结果：

- dependencies 新增：`alova`、`element-plus`、`@element-plus/icons-vue`、`pinia-plugin-persistedstate`、`@vueuse/core`、`dayjs`、`qs`。
- devDependencies 新增：`unplugin-auto-import`、`unplugin-vue-components`、`unplugin-element-plus`、`sass`、`@antfu/eslint-config`、`@typescript-eslint/eslint-plugin`、`@typescript-eslint/parser`。
- devDependencies 移除：`@vue/eslint-config-typescript`、`eslint-plugin-vue`、`vue-eslint-parser`（后两者已由 `@antfu/eslint-config` 自带）。
- 保留不变：`eslint`、`eslint-plugin-oxlint`、`oxlint`、`jiti`、`typescript`、`vite`、`vue-tsc`、`npm-run-all2` 等；`scripts` 不变（`lint:oxlint` / `lint:eslint` 继续可用）。

原因：`@antfu/eslint-config` 不支持内置 oxlint 集成（v10 README 明确说明仍在规划中），因此 `eslint-plugin-oxlint` 与现有 `.oxlintrc.json` 保留，继续作为独立 lint 步骤。

### 2. `vite.config.ts` — 注册自动导入与 Element Plus 插件

在现有插件数组后追加：

```ts
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import ElementPlus from 'unplugin-element-plus/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

// plugins 内追加
AutoImport({
  imports: ['vue', 'vue-router', 'pinia', '@vueuse/core'],
  resolvers: [ElementPlusResolver()],
  dts: 'src/types/auto-imports.d.ts',
  vueTemplate: true,
}),
Components({
  dirs: ['src/components'],
  resolvers: [ElementPlusResolver()],
  dts: 'src/types/components.d.ts',
}),
ElementPlus(),
```

要点：

- `ElementPlusResolver()` 默认 `importStyle: 'css'`，即按需引入组件及其 CSS，无需在 `main.ts` 引入 `element-plus/dist/index.css`；`sass` 仅用于项目自身样式/后续主题定制。
- `ElementPlus()`（unplugin-element-plus）负责对显式 `import { ElMessage } from 'element-plus'` 这类非组件导出补全样式。
- 生成文件放到 `src/types/` 下，可被 `tsconfig.app.json` 的 `src/**/*` 自动包含。
- `vite` 的 `defineConfig` 结构、`@` 别名、`vueDevTools()` 均不改动。

### 3. `src/main.ts` — 接入持久化插件与图标

```ts
import './assets/main.css'

import { createApp } from 'vue'
import { createPinia } from 'pinia'
import piniaPluginPersistedstate from 'pinia-plugin-persistedstate'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'

import App from './App.vue'
import router from './router'

const app = createApp(App)
const pinia = createPinia()
pinia.use(piniaPluginPersistedstate)

app.use(pinia)
app.use(router)

for (const [key, component] of Object.entries(ElementPlusIconsVue))
  app.component(key, component)

app.mount('#app')
```

说明：Element Plus 组件通过 unplugin 按需自动导入，因此**不**调用 `app.use(ElementPlus)`；图标没有对应 resolver（未安装 `unplugin-icons`），采用官方推荐的全局注册方式。现有 `stores/counter.ts` 不改动（未要求持久化任何具体 store，后续在 store 里加 `persist: true` 即可生效）。

### 4. 新建 `src/utils/request.ts` — alova 实例

依据 alova v3 客户端用法（`alova/vue` statesHook + `alova/fetch` 适配器）：

```ts
import { createAlova } from 'alova'
import VueHook from 'alova/vue'
import adapterFetch from 'alova/fetch'

export const alovaInstance = createAlova({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  statesHook: VueHook,
  requestAdapter: adapterFetch(),
  timeout: 10000,
  responded: {
    onSuccess: async (response) => {
      if (!response.ok)
        throw new Error(`[${response.status}] ${response.statusText}`)

      return await response.json()
    },
    onError: (error) => {
      console.error('[alova] request error:', error)
      throw error
    },
  },
})
```

后续接口函数（如 `src/api/*.ts`）直接使用 `alovaInstance.Get<T>(...)` 等创建 method 实例，组件内用 `useRequest` / `useWatcher`（从 `alova/client` 导入）。本次不新增接口文件，避免臆造业务端点。

### 5. `env.d.ts` — 补充环境变量类型

```ts
/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
```

### 6. `eslint.config.ts` — 切换为 @antfu/eslint-config

```ts
import antfu from '@antfu/eslint-config'
import pluginOxlint from 'eslint-plugin-oxlint'

export default antfu(
  {
    vue: true,
    typescript: true,
    formatters: false,
    ignores: ['**/dist/**', '**/dist-ssr/**', '**/coverage/**'],
  },
  ...pluginOxlint.buildFromOxlintConfigFile('.oxlintrc.json'),
)
```

要点：`@antfu/eslint-config` 自动启用 Vue、TypeScript 规则，并自带 `eslint-plugin-vue` / `vue-eslint-parser` 依赖；`gitignore` 忽略默认开启。antfu 默认风格（2 空格、单引号、无分号）与现有代码风格一致，但可能对导入顺序等做自动修正，执行 `pnpm lint` 后需检查 diff。

### 7. `src/router/index.ts` — 简化为单条内联路由

```ts
import { defineComponent, h } from 'vue'
import { createRouter, createWebHistory } from 'vue-router'

const HomeView = defineComponent({
  name: 'HomeView',
  render: () => h('div', { class: 'home-view' }, 'Oi'),
})

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'home',
      component: HomeView,
    },
  ],
})
```

移除对 `../views/HomeView.vue`、`../views/AboutView.vue` 的引用与 `/about` 路由；`src/views` 空目录保留（不创建任何视图文件）。

## 假设与决策

- 按需自动导入（你已确认）：Element Plus 组件与 API 全部走 unplugin；不使用 `app.use(ElementPlus)` 与全量 CSS。
- ESLint 采用 @antfu/eslint-config 整体替换（你已确认），并保留 oxlint 作为独立脚本。
- 路由简化（你已确认）：不补建视图文件，`/` 使用内联组件，删除 `/about`。
- alova 使用内置 `alova/fetch` 适配器，不额外安装 `@alova/adapter-xhr`；baseURL 读取 `VITE_API_BASE_URL`，未配置时回退 `/api`（后续可用 `.env.development` 覆盖）。
- Element Plus 图标全局注册，不做按需图标 resolver（未安装 `unplugin-icons`）。
- `qs` 只按要求安装（项目暂无代码使用）；`qs` 不自带类型声明，若后续在 TS 中使用需另行添加 `@types/qs`（本次不安装）。
- 生成物 `src/types/auto-imports.d.ts`、`src/types/components.d.ts` 保留在工作区（便于 IDE 类型提示），暂不加入 `.gitignore`。
- 不修改 `App.vue`、`stores/counter.ts`、`.oxlintrc.json`、`tsconfig*.json`、`scripts`。

## 验证步骤

1. `pnpm install`（确认 lock 更新、无 peer 冲突；`pinia-plugin-persistedstate` 对 pinia 4 的 peer 校验应通过）。
2. `pnpm lint`：确认 antfu 配置加载成功、oxlint 配置仍可被 `eslint-plugin-oxlint` 读取；若自动修正了无关文件，回退与本次无关的格式化改动。
3. `pnpm type-check`：应通过（路由不再引用缺失文件；新代码均显式 import，不依赖尚未生成的 auto-import dts）。
4. `pnpm build`：确认 Vite 插件（auto-import / components / element-plus）正常执行，并在 `src/types` 下生成两个 `.d.ts`。
5. `pnpm dev` 启动后访问 `http://localhost:5173/`：页面正常渲染、控制台无报错；确认 `src/types/components.d.ts` 已生成。可临时在组件中使用 `<el-button>` 验证按需样式（验证后删除临时改动）。
6. 复核 `package.json` 中新增/移除项与本方案一致，且 `pnpm-lock.yaml` 已同步。
