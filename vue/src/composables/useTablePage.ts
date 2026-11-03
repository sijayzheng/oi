/**
 * 分页查询函数签名：接收业务查询条件与分页参数，返回可等待的 Result（alova Method 实例为其子类型）。
 */
type TablePageFetcher<Q extends object, Row> = (query: Q & PageQuery) => PromiseLike<Result<Row[]>>

/**
 * 排序变更事件参数（el-table sort-change 回调的兼容子集；
 * 字段类型放宽至 string|null 以匹配 el-table 的回调签名）。
 */
interface SortChangeParam {
  prop?: string | null
  order?: string | null
}

/**
 * 标准列表页组合式函数：封装分页查询、搜索/重置、后端排序、加载状态与数据容器，
 * 供各管理页复用，保证列表交互行为一致。
 *
 * <p>约定：fetcher 传入完整查询参数对象（业务条件 + pageNum/pageSize/sortField/desc）；
 * sortField 未设置时不携带排序参数，由后端按自然顺序返回。
 *
 * @param fetcher  分页查询函数（通常为 xxxApi.page）
 * @param getDefaultQuery 业务查询条件的初始值工厂（重置时重新生成，避免残留脏值）
 */
export function useTablePage<Q extends object, Row>(fetcher: TablePageFetcher<Q, Row>, getDefaultQuery: () => Q) {
  /** 业务查询条件（响应式，双向绑定搜索表单） */
  const query = reactive(getDefaultQuery()) as Q

  /** 当前页码；从 1 开始 */
  const pageNum = ref(1)
  /** 每页条数；上限 100（后端 PageQuery 约定） */
  const pageSize = ref(10)
  /** 总记录数（后端 Long 序列化为字符串，此处转回数字） */
  const total = ref(0)
  /** 排序字段；未设置为自然顺序 */
  const sortField = shallowRef<string>()
  /** 是否降序；默认 true（后台列表惯例：最新数据在前） */
  const desc = ref(true)
  /** 列表加载状态 */
  const loading = ref(false)
  /** 当前页数据行 */
  const rows = shallowRef<Row[]>([])

  /**
   * 加载当前页数据；加载中不重复发起。
   */
  async function load() {
    if (loading.value)
      return
    loading.value = true
    try {
      const params = { ...query } as Record<string, unknown>
      // 排序参数仅在显式指定时携带，避免 undefined 参与查询串
      if (sortField.value) {
        params.sortField = sortField.value
        params.desc = desc.value
      }
      params.pageNum = pageNum.value
      params.pageSize = pageSize.value
      const result = await fetcher(params as Q & PageQuery)
      rows.value = result.data ?? []
      total.value = Number(result.total ?? 0)
    } finally {
      loading.value = false
    }
  }

  /**
   * 搜索：重置页码后加载（筛选条件变化时调用）。
   */
  function search() {
    pageNum.value = 1
    load()
  }

  /**
   * 重置：恢复初始查询条件并重新搜索。
   */
  function reset() {
    Object.assign(query, getDefaultQuery())
    sortField.value = undefined
    search()
  }

  /**
   * 分页变更（页码/条数）：加载新页。
   */
  function pageChange() {
    load()
  }

  /**
   * 排序变更：记录排序字段并重新加载；取消排序时恢复自然顺序。
   */
  function sortChange({ prop, order }: SortChangeParam) {
    sortField.value = order && prop
      ? prop
      : undefined
    desc.value = order !== 'ascending'
    load()
  }

  return {
    query,
    pageNum,
    pageSize,
    total,
    sortField,
    desc,
    loading,
    rows,
    load,
    search,
    reset,
    pageChange,
    sortChange,
  }
}
