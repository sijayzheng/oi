import type { Method, RequestBody } from 'alova'
import { createAlova } from 'alova'
import adapterFetch from 'alova/fetch'
import VueHook from 'alova/vue'
/**
 * 方法级请求配置；通过各 API 方法的最后一个可选参数传入，随 method meta 透传至拦截器。
 */
export interface RequestConfig {
  /**
   * 全局 loading 提示文案；传入后请求期间显示全屏遮罩（计数器支持并发请求嵌套，全部结束才关闭）。
   */
  loading?: string
  /**
   * 响应返回完整 Result 结构（含 code/message/data/total）；默认仅返回 data 字段。
   */
  full?: boolean
  /**
   * 静默模式；业务失败不弹全局错误提示，由调用方 catch 后自行处理。
   */
  silent?: boolean
  /**
   * 请求超时毫秒数；不传使用实例默认值。
   */
  timeout?: number
}

/**
 * 业务错误；code 为后端 Result.code（400 参数校验失败 / 6XXXXX 业务错误码 / 5xx 系统异常）。
 */
export class BusinessError extends Error {
  /**
   * 业务错误码；与后端 Result.code 一致，可用于精确分支处理。
   */
  code: number

  constructor(code: number, message: string) {
    super(message)
    this.name = 'BusinessError'
    this.code = code
  }
}

/**
 * 后端统一响应结构（仅拦截器内部解析用，对外类型见 @/types/common 的 Result）。
 */
interface RawResult {
  code: number
  message: string
  data: unknown
  total?: string | null
}

/**
 * 实例默认超时时间（毫秒）；方法级可通过 RequestConfig.timeout 覆盖。
 */
const DEFAULT_TIMEOUT = 15_000

/* ==================== 全局 loading（计数器支持并发请求嵌套） ==================== */

let loadingCount = 0
let loadingInstance: ReturnType<typeof ElLoading.service> | null = null

function showLoading(text: string) {
  loadingCount++
  if (!loadingInstance)
    loadingInstance = ElLoading.service({ lock: true, text, background: 'rgba(0, 0, 0, 0.3)' })
}

function hideLoading() {
  loadingCount = Math.max(0, loadingCount - 1)
  if (loadingCount === 0 && loadingInstance) {
    loadingInstance.close()
    loadingInstance = null
  }
}

/* ==================== 请求取消（进行中的方法实例登记表） ==================== */

const pendingMethods = new Set<Method>()

/**
 * 取消单个进行中的请求。
 *
 * @param methodInstance API 方法返回的 Method 实例（未 await 前均可取消）
 */
export function cancelRequest(methodInstance: Method) {
  methodInstance.abort()
}

/**
 * 取消全部进行中的请求（典型场景：路由切换时清理页面遗留请求）。
 */
export function cancelAllRequests() {
  pendingMethods.forEach(methodInstance => methodInstance.abort())
  pendingMethods.clear()
}

/**
 * 判断是否为主动取消产生的中断错误（静默处理，不提示不打印）。
 */
function isAbortError(error: unknown) {
  return error instanceof DOMException && error.name === 'AbortError'
}

/**
 * alova 实例；baseURL 优先取环境变量 VITE_API_BASE_URL，开发环境经 vite 代理转发至后端。
 */
const alovaInstance = createAlova({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: DEFAULT_TIMEOUT,
  statesHook: VueHook,
  requestAdapter: adapterFetch(),
  cacheFor: null,
  /**
   * 请求前拦截：应用方法级超时、按需开启全局 loading、登记待取消请求。
   */
  beforeRequest(method) {
    const config = (method.config.meta ?? {}) as RequestConfig
    if (config.timeout)
      method.config.timeout = config.timeout
    if (config.loading)
      showLoading(config.loading)
    pendingMethods.add(method)
  },
  responded: {
    /**
     * 响应成功拦截：HTTP 层错误（网络/网关返回非 2xx）与业务层错误（Result.code !== 200）
     * 统一转换为 BusinessError 抛出，默认弹出错误提示，silent 模式跳过提示由调用方处理。
     */
    onSuccess: async (response, method) => {
      const config = (method.config.meta ?? {}) as RequestConfig
      if (!response.ok) {
        const message = response.status === 401
          ? '登录状态已失效，请重新登录'
          : `请求失败 [${response.status}] ${response.statusText}`
        if (!config.silent)
          ElMessage.error(message)
        throw new BusinessError(response.status, message)
      }
      const result = await response.json() as RawResult
      if (result.code !== 200) {
        if (!config.silent)
          ElMessage.error(result.message)
        // 令牌失效（未登录/过期/被踢下线）：清除本地认证态并整页回登录页；
        // 不经 router/store 以避免 request → store → api → request 循环依赖
        if (result.code === 401) {
          localStorage.removeItem('auth')
          window.location.assign('/login')
        }
        throw new BusinessError(result.code, result.message)
      }
      // full 模式返回完整 Result（分页需读取 total），默认仅返回业务数据
      return config.full ? result : result.data
    },
    /**
     * 请求异常拦截：主动取消静默处理；网络异常统一提示；
     * 业务错误（已在 onSuccess 中提示过）与网络异常均继续抛出，保证调用方可捕获。
     */
    onError: (error) => {
      if (!isAbortError(error)) {
        if (!(error instanceof BusinessError))
          ElMessage.error('网络异常，请稍后重试')
        console.error('[request] 请求异常：', error)
      }
      throw error
    },
    /**
     * 请求完成拦截（成功/失败/取消均触发）：关闭 loading、移出待取消登记表。
     */
    onComplete: (method) => {
      const config = (method.config.meta ?? {}) as RequestConfig
      if (config.loading)
        hideLoading()
      pendingMethods.delete(method)
    },
  },
})

/**
 * 查询参数类型（GET 查询串）；object 兼容 interface 类型定义（interface 无隐式索引签名），
 * 传参时统一转换为 alova 的 Arg（Record<string, any>）。
 */
type QueryParams = object

/**
 * 统一请求封装；全部返回 alova Method 实例（PromiseLike，可直接 await，未 await 前可 abort）。
 */
const request = {
  /**
   * 分页查询；返回完整 Result 结构（含 total）。泛型传 Result<T[]>，
   * 如 request.page<Result<SysUserVO[]>>('/user/page', data)。
   */
  page<T = unknown>(url: string, data?: QueryParams, config?: RequestConfig) {
    return alovaInstance.Get<T>(url, {
      params: data as Record<string, any> | undefined,
      meta: { full: true, ...config },
    })
  },
  /**
   * GET 查询；默认仅返回 data 字段。
   */
  get<T = unknown>(url: string, params?: QueryParams, config?: RequestConfig) {
    return alovaInstance.Get<T>(url, {
      params: params as Record<string, any> | undefined,
      meta: { ...config },
    })
  },
  /**
   * POST 创建；默认仅返回 data 字段（创建类接口为 void）。
   */
  post<T = void>(url: string, data?: unknown, config?: RequestConfig) {
    return alovaInstance.Post<T>(url, data as RequestBody | undefined, {
      meta: { ...config },
    })
  },
  /**
   * PUT 更新；默认仅返回 data 字段（更新类接口为 void）。
   */
  put<T = void>(url: string, data?: unknown, config?: RequestConfig) {
    return alovaInstance.Put<T>(url, data as RequestBody | undefined, {
      meta: { ...config },
    })
  },
  /**
   * DELETE 删除；默认仅返回 data 字段（删除类接口为 void）。
   */
  delete<T = void>(url: string, config?: RequestConfig) {
    return alovaInstance.Delete<T>(url, undefined, {
      meta: { ...config },
    })
  },
  /**
   * PATCH 局部更新；默认仅返回 data 字段。
   */
  patch<T = void>(url: string, data?: unknown, config?: RequestConfig) {
    return alovaInstance.Patch<T>(url, data as RequestBody | undefined, {
      meta: { ...config },
    })
  },
}

export default request
