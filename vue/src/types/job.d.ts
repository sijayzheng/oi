/**
 * 任务调度模块类型定义，与后端 cn.sijay.oi.sys.model.job / entity 严格对应。
 *
 * <p>序列化约定：Long 统一序列化为字符串；时间字段为 yyyy-MM-dd HH:mm:ss 字符串。
 */

/**
 * 触发类型；对应后端 JobTriggerType 枚举。
 */
export type JobTriggerType = 'CRON' | 'FIXED_RATE' | 'FIXED_DELAY'

/**
 * 重试策略；对应后端 JobRetryPolicy 枚举。
 */
export type JobRetryPolicy = 'FIXED_INTERVAL' | 'EXPONENTIAL_BACKOFF'

/**
 * 并发冲突策略；对应后端 JobConflictStrategy 枚举。
 */
export type JobConflictStrategy = 'DISCARD' | 'QUEUE'

/**
 * 执行状态；对应后端 JobExecuteStatus 枚举。
 */
export type JobExecuteStatus = 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED' | 'TIMEOUT' | 'DEAD' | 'SKIPPED'

/**
 * 触发来源；对应后端 SysJobLog.TriggerSource 枚举。
 */
export type JobTriggerSource = 'SCHEDULE' | 'MANUAL' | 'REPLAY'

/**
 * 死信处理状态；对应后端 SysJobDeadLetter.Status 枚举。
 */
export type DeadLetterStatus = 'PENDING' | 'RESOLVED' | 'REPLAY_FAILED'

/**
 * 任务定义视图对象；对应后端 SysJobVO。
 */
export interface SysJobVO {
  /** 任务 id */
  id: string
  /** 任务业务键；点分命名，全局唯一 */
  jobKey: string
  /** 任务名称 */
  name: string
  /** 触发类型 */
  triggerType: JobTriggerType
  /** 触发类型中文描述 */
  triggerTypeDesc?: string
  /** 触发值；CRON 表达式或毫秒周期 */
  triggerValue: string
  /** 超时毫秒；0=不限时 */
  timeoutMillis: string
  /** 重试策略 */
  retryPolicy: JobRetryPolicy
  /** 重试策略中文描述 */
  retryPolicyDesc?: string
  /** 最大重试次数 */
  maxRetryCount: number
  /** 初始重试间隔毫秒 */
  retryInitialInterval: string
  /** 最大重试间隔毫秒 */
  retryMaxInterval: string
  /** 可重试异常白名单（类型模式，逗号分隔） */
  retryInclude?: string | null
  /** 不可重试异常黑名单 */
  retryExclude?: string | null
  /** 并发冲突策略 */
  conflictStrategy: JobConflictStrategy
  /** 并发冲突策略中文描述 */
  conflictStrategyDesc?: string
  /** 连续失败告警阈值；0=不启用 */
  alertFailureThreshold: number
  /** 备注 */
  remark?: string | null
  /** 启用状态；true=启用中 / false=已暂停 */
  enabled: boolean
  /** 创建时间 */
  createTime?: string
  /** 更新时间 */
  updateTime?: string
}

/**
 * 创建任务入参；对应后端 SysJobCreateCmd。
 */
export interface SysJobCreateCmd {
  /** 任务业务键 */
  jobKey: string
  /** 任务名称 */
  name: string
  /** 触发类型 */
  triggerType: JobTriggerType
  /** 触发值 */
  triggerValue: string
  /** 超时毫秒 */
  timeoutMillis?: string
  /** 重试策略 */
  retryPolicy?: JobRetryPolicy
  /** 最大重试次数 */
  maxRetryCount?: number
  /** 初始重试间隔毫秒 */
  retryInitialInterval?: string
  /** 最大重试间隔毫秒 */
  retryMaxInterval?: string
  /** 可重试异常白名单 */
  retryInclude?: string
  /** 不可重试异常黑名单 */
  retryExclude?: string
  /** 并发冲突策略 */
  conflictStrategy?: JobConflictStrategy
  /** 连续失败告警阈值 */
  alertFailureThreshold?: number
  /** 备注 */
  remark?: string
}

/**
 * 更新任务入参；对应后端 SysJobUpdateCmd（jobKey 不可修改）。
 */
export interface SysJobUpdateCmd extends Omit<SysJobCreateCmd, 'jobKey'> {
}

/**
 * 任务查询条件；对应后端 SysJobQuery。
 */
export interface SysJobQuery {
  /** 任务业务键过滤（模糊） */
  jobKey?: string
  /** 任务名称过滤（模糊） */
  name?: string
  /** 触发类型过滤（精确） */
  triggerType?: JobTriggerType
  /** 启用状态过滤（精确） */
  enabled?: boolean
}

/**
 * 单轮重试明细；对应后端 SysJobLogVO.AttemptDetailVO。
 */
export interface JobAttemptDetailVO {
  /** 尝试轮次；0=首次 */
  attemptNo: number
  /** 本轮状态 */
  attemptStatus: string
  /** 本轮异常类型 */
  errorType?: string | null
  /** 本轮异常信息 */
  errorStack?: string | null
  /** 本轮开始时间 */
  attemptStartTime: string
  /** 本轮结束时间 */
  attemptEndTime?: string | null
  /** 本轮耗时毫秒 */
  attemptDurationMillis?: string | null
  /** 下次重试时刻 */
  nextRetryAt?: string | null
}

/**
 * 任务执行记录视图对象；对应后端 SysJobLogVO。
 */
export interface SysJobLogVO {
  /** 执行记录 id */
  id: string
  /** 任务 id */
  jobId: string
  /** 任务业务键 */
  jobKey: string
  /** 任务名称快照 */
  jobName: string
  /** 触发来源 */
  triggerSource: JobTriggerSource
  /** 触发来源中文描述 */
  triggerSourceDesc?: string
  /** 执行状态 */
  executeStatus: JobExecuteStatus
  /** 执行状态中文描述 */
  executeStatusDesc?: string
  /** 当前轮次 */
  attemptNo: number
  /** 累计重试次数 */
  retryCount: number
  /** 异常类型 */
  errorType?: string | null
  /** 异常信息（含堆栈） */
  errorStack?: string | null
  /** 执行开始时间 */
  startTime: string
  /** 执行结束时间 */
  endTime?: string | null
  /** 执行耗时毫秒 */
  durationMillis?: string | null
  /** 执行机标识 */
  executor?: string | null
  /** 逐轮重试明细；仅详情接口返回 */
  attempts?: JobAttemptDetailVO[]
}

/**
 * 死信视图对象；对应后端 SysJobDeadLetterVO。
 */
export interface SysJobDeadLetterVO {
  /** 死信 id */
  id: string
  /** 任务 id */
  jobId: string
  /** 任务业务键 */
  jobKey: string
  /** 任务名称快照 */
  jobName: string
  /** 死信来源执行记录 id（跳转执行详情） */
  sourceLogId: string
  /** 任务配置快照（JSON） */
  jobConfig?: string | null
  /** 最终异常类型 */
  errorType?: string | null
  /** 最终异常信息 */
  errorStack?: string | null
  /** 累计重试次数 */
  totalRetryCount: number
  /** 处理状态 */
  status: DeadLetterStatus
  /** 处理状态中文描述 */
  statusDesc?: string
  /** 最近重放时间 */
  replayTime?: string | null
  /** 死信产生时间 */
  createTime: string
}

/**
 * 手动触发/死信重放的执行结果；对应后端 Map 返回结构。
 */
export interface JobTriggerResult {
  /** 执行记录 id */
  logId: string
  /** 终态状态名 */
  status: JobExecuteStatus
  /** 是否成功 */
  success: boolean
}
