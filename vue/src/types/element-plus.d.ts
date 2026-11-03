/**
 * Element Plus 类型再导出（供 unplugin-auto-import 的 dirs types 扫描）。
 *
 * <p>约定：项目内使用的 Element Plus TS 类型统一在此 re-export，
 * 配合 vite.config.ts 中 AutoImport dirs 的 { dir: 'src/types/**', types: true }，
 * 业务代码无需手动 import type 即可使用这些类型；
 * 新增使用的 Element Plus 类型须同步补充到本文件。
 */
export type { FormInstance, FormRules, UploadRequestOptions } from 'element-plus'
