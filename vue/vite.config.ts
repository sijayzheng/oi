import { fileURLToPath, URL } from 'node:url'

import vue from '@vitejs/plugin-vue'
import vueJsx from '@vitejs/plugin-vue-jsx'
import AutoImport from 'unplugin-auto-import/vite'
import ElementPlus from 'unplugin-element-plus/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import Components from 'unplugin-vue-components/vite'
import { defineConfig } from 'vite'
import vueDevTools from 'vite-plugin-vue-devtools'

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
  server: {
    proxy: {
      // 开发环境代理：/api 前缀请求转发到后端服务（生产环境由网关/反向代理承担）
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        rewrite: url => url.replace(/^\/api/, ''),
      },
      // 头像等上传文件静态资源（后端 uploads/ 目录映射）
      '/uploads': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
