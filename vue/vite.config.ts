import { existsSync, readdirSync } from 'node:fs'
import { fileURLToPath, URL } from 'node:url'

import vue from '@vitejs/plugin-vue'
import vueJsx from '@vitejs/plugin-vue-jsx'
import AutoImport from 'unplugin-auto-import/vite'
import ElementPlus from 'unplugin-element-plus/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import Components from 'unplugin-vue-components/vite'
import { defineConfig } from 'vite'
import vueDevTools from 'vite-plugin-vue-devtools'

// element-plus 按需组件的 style 深层入口清单：配置加载期从包目录自动生成（118 个组件）
// 不用 optimizeDeps.include 的 glob 语法——实测在当前 Vite + element-plus exports 结构下
// 展开为空（exportsValueGlobRe 以 "./" 前缀匹配 globSync 返回的裸路径，全部被过滤），
// 故以文件系统枚举显式列出，组件库升级自动跟随，无需手工维护
const elementPlusComponentsDir = fileURLToPath(new URL('./node_modules/element-plus/es/components', import.meta.url))
const elementPlusStyleDeps = existsSync(elementPlusComponentsDir)
  ? readdirSync(elementPlusComponentsDir)
      .filter(name => existsSync(`${elementPlusComponentsDir}/${name}/style/css.mjs`))
      .map(name => `element-plus/es/components/${name}/style/css`)
  : []

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    vue(),
    vueJsx(),
    vueDevTools(),
    AutoImport({
      imports: [
        'vue',
        'vue-router',
        'pinia',
        '@vueuse/core',
        // Element Plus 图标组件：按需登记项目内使用的图标（新增图标须同步补充）
        {
          '@element-plus/icons-vue': [
            'AlarmClock',
            'ArrowDown',
            'ChatDotRound',
            'Close',
            'Delete',
            'Download',
            'Edit',
            'Expand',
            'Fold',
            'Lock',
            'MagicStick',
            'MessageBox',
            'Plus',
            'Refresh',
            'RefreshLeft',
            'Search',
            'Setting',
            'Share',
            'Tickets',
            'User',
            'UserFilled',
            'View',
          ],
        },
      ],
      dirs: [
        'src/api/**',
        'src/composables/**',
        'src/utils/**',
        // 类型定义目录：interface/type 仅在类型位置自动导入（types: true）
        { glob: 'src/types/**', types: true },
      ],
      // 排除插件自身生成的声明文件，避免类型导出被重复扫描（Duplicated imports 告警）
      dirsScanOptions: {
        fileFilter: file => !file.includes('auto-imports.d.ts') && !file.includes('components.d.ts'),
      },
      resolvers: [
        ElementPlusResolver(),
      ],
      dts: 'src/types/auto-imports.d.ts',
      vueTemplate: true,
    }),
    Components({
      dirs: ['src/components'],
      resolvers: [
        ElementPlusResolver(),
      ],
      dts: 'src/types/components.d.ts',
    }),
    ElementPlus({}),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  optimizeDeps: {
    // 预加载项目必需的组件/依赖：开发期一次性预构建，避免首屏逐模块转换抖动
    include: [
      'vue',
      'vue-router',
      'pinia',
      'pinia-plugin-persistedstate',
      'alova',
      '@vueuse/core',
      'dayjs',
      'qs',
      'sm-crypto',
      // element-plus：根入口（源码直引）+ es 入口（ElementPlusResolver 按需组件统一从这里导入）
      'element-plus',
      'element-plus/es',
      // 按需组件的 style 深层入口（见顶部 elementPlusStyleDeps 生成逻辑）
      ...elementPlusStyleDeps,
      '@element-plus/icons-vue',
    ],
  },
  server: {
    port: 29977,
    proxy: {
      // 开发环境代理：/api 前缀请求转发到后端服务（生产环境由网关/反向代理承担）
      '/api': {
        target: 'http://localhost:29979',
        changeOrigin: true,
        rewrite: url => url.replace(/^\/api/, ''),
      },
      // 头像等上传文件静态资源（后端 uploads/ 目录映射）
      '/uploads': {
        target: 'http://localhost:29979',
        changeOrigin: true,
      },
    },
  },
})
