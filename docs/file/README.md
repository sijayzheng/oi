# 统一文件管理服务：接口文档与调用规范

> 模块：`cn.sijay.oi.file`（表 `sys_file`，错误码段 606000~606999）
> 生效日期：2026-11-03（全系统强制，新增业务场景必须遵循）

## 一、核心规范（全系统强制约定）

1. **入口唯一**：系统内所有业务场景的文件上传、存储、查询、删除，统一经文件管理服务
   （`FileService` / `FileController`）处理；业务模块**禁止**自行处理文件实体或存储路径。
2. **只存文件 ID**：业务上传成功后，只接收并持久化返回的**文件唯一 id**（`SysFileVO.id`）；
   **禁止**在业务表保存文件 URL、存储路径或文件名。
3. **凭 ID 访问**：业务模块需要访问文件时，仅凭存储的文件 id 调用文件服务的查询接口
   （`GET /file/{id}/url`）换取带签名与有效期的临时访问地址，**禁止**直接操作文件存储介质
   （后端已移除 `/uploads/**` 静态映射，存储目录不对外暴露）。
4. **删除须评估引用**：删除文件（`DELETE /file/{id}`）后，所有引用该文件 id 的业务数据
   将无法再换取访问地址；调用方删除前须自行确认引用关系。

## 二、接口清单

### 1. 上传文件（需登录）

```
POST /file/upload
Content-Type: multipart/form-data
参数：file（必填，multipart 文件）；businessKey（可选，业务标识，如 avatar）
响应：Result<SysFileVO>
```

- 校验链：空文件 → 扩展名白名单 → 大小限制，失败码见"错误码"节。
- `SysFileVO`：`id`（唯一标识）、`originalName`、`extension`、`contentType`、`size`、
  `businessKey`、`accessUrl`（临时访问地址）、`createTime`。
- 纯图片业务（如头像）走 `POST /auth/avatar`（`FileService.uploadImage`，图片白名单收窄）。

### 2. 查询文件元数据（需登录）

```
GET /file/{id}
响应：Result<SysFileVO>（含新签发的 accessUrl）
```

### 3. 分页条件查询（需登录，管理端检索）

```
GET /file/page?originalName=&businessKey=&extension=&pageNum=1&pageSize=10
响应：Result<List<SysFileVO>>（total 携带总记录数）
```

### 4. 签发临时访问地址（需登录）

```
GET /file/{id}/url
响应：Result<String>
地址格式：/file/access/{id}?expires={epoch秒}&sign={HMAC-SHA256(id:expires)}
```

- 地址带有效期（默认 30 分钟，`file.access.ttl-minutes` 可配）与签名，过期后重新调用本接口换取。

### 5. 公开访问文件流（免登录，凭有效签名）

```
GET /file/access/{id}?expires=...&sign=...
响应：文件流；图片类 inline 内联展示，其余 attachment 下载（文件名 RFC 5987 编码）
```

- 签名无效或已过期 → `606005`；文件不存在 → `606004`。

### 6. 删除文件（需登录）

```
DELETE /file/{id}
响应：Result<Void>
```

- 软删 `sys_file` 记录 + 尽力清理物理文件；重复删除返回 `606004`。

## 三、前端调用约定

- API 封装：`vue/src/api/file.ts`（`fileApi.upload/get/page/getUrl/remove`）。
- `img src` 等场景使用 `toFileSrc(accessUrl)` 补齐 API 前缀（与 `request.ts` 的 baseURL 约定一致）。
- 临时地址过期后重新调用 `fileApi.getUrl(id)`，**不要缓存长期使用**。

## 四、业务接入示例（保存文件 ID）

```ts
// 上传：只拿 id
const file = await fileApi.upload(rawFile, 'avatar')
await saveBusiness({ avatarFileId: file.id })   // 业务表只存 file.id

// 展示：凭 id 换临时地址
const url = await fileApi.getUrl(fileId)        // /file/access/1?expires=...&sign=...
imgSrc.value = toFileSrc(url)
```

后端业务模块同理：注入 `FileService`，上传取 `SysFileVO.getId()` 落库，展示调 `issueAccessUrl(id)`。
参考实现：`AuthService.uploadAvatar`（auth → file 的委托改造）。

## 五、错误码（606 段）

| 错误码 | 场景 | 消息 |
|---|---|---|
| 606001 | 未选择文件或 0 字节 | 上传文件不能为空 |
| 606002 | 扩展名不在白名单 | 不支持的文件类型 |
| 606003 | 超过大小上限（业务级或 multipart 硬上限） | 文件大小超出限制 |
| 606004 | 文件不存在或已删除 | 文件不存在或已删除 |
| 606005 | 访问签名无效或已过期 | 文件访问链接已失效 |
| 606006 | 落盘 IO 异常 | 文件上传失败，请稍后重试 |

## 六、配置项（application.yaml）

| 配置 | 默认值 | 说明 |
|---|---|---|
| `file.upload.storage-path` | `uploads` | 存储根目录（工作目录相对路径），文件按 `yyyy/MM` 月份分桶落盘 |
| `file.upload.allowed-extensions` | `jpg,...,7z` | 全局默认扩展名白名单 |
| `file.upload.max-size` | `10MB` | 单文件大小上限（multipart 硬上限 `spring.servlet.multipart.max-file-size` 之内） |
| `file.access.secret` | `oi-file-access-secret` | 访问地址 HMAC 签名密钥，**生产环境务必更换** |
| `file.access.ttl-minutes` | `30` | 访问地址默认有效期（分钟） |

## 七、存储与唯一标识

- 唯一标识：文件名落盘为 `UUID + 原扩展名`，元数据主键 `sys_file.id` 为业务引用凭据。
- 路径映射规则（收敛于 `FileService`，对外不暴露）：`{storage-path}/yyyy/MM/{uuid}.{ext}`。
- 物理路径不出服务层；`SysFileVO` 不含 `storageName`。
