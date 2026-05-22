# 短视频反诈后端 — APP 端 API 参考文档

> 面向 APP 开发者。所有接口 base URL: `http://<host>:8080`

---

## 1. 通用约定

### 1.1 统一响应格式

所有接口返回 `ApiResponse<T>`：

```json
{
  "code": 200,
  "message": "success",
  "data": { ... }
}
```

- `code=200` 成功，其他值表示错误
- `message` 仅在错误时有意义
- `data` 为 `null` 时字段不出现（`@JsonInclude(NON_NULL)`）

### 1.2 HTTP 错误码

| HTTP status | 场景 |
|-------------|------|
| 400 | 参数缺失 / 参数校验失败 / 文件超限 |
| 500 | 服务器内部错误（AI 服务异常等） |

---

## 2. 接口清单

| 方法 | 路径 | 用途 |
|------|------|------|
| POST | `/api/v1/detect` | 上传视频并检测 |
| GET | `/api/v1/history` | 查询检测历史（支持增量同步 + 分页） |
| GET | `/api/v1/blacklist/check/{authorId}` | 综合查询三层黑名单 |
| POST | `/api/v1/blacklist/authority` | 添加到权威黑名单 |
| GET | `/api/v1/blacklist/authority/{id}` | 查询权威黑名单 |
| DELETE | `/api/v1/blacklist/authority/{id}` | 移出权威黑名单 |
| POST | `/api/v1/blacklist/global` | 添加到全局黑名单 |
| GET | `/api/v1/blacklist/global/{id}` | 查询全局黑名单 |
| DELETE | `/api/v1/blacklist/global/{id}` | 移出全局黑名单 |
| POST | `/api/v1/blacklist/temp` | 添加到临时黑名单 |
| GET | `/api/v1/blacklist/temp/{id}` | 查询临时黑名单（含 TTL） |
| DELETE | `/api/v1/blacklist/temp/{id}` | 移出临时黑名单 |

---

## 3. 检测接口（核心）

### POST /api/v1/detect

上传视频进行反诈分析。

**Content-Type:** `multipart/form-data`

**请求参数：**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `deviceId` | String | 是 | 设备唯一标识 |
| `authorId` | String | 是 | 发布者 ID |
| `video` | File | 是 | 视频文件（≤10MB） |

**响应 data 对象 (DetectResponse)：**

| 字段 | 类型 | 说明 | 出现条件 |
|------|------|------|----------|
| `riskLevel` | String | 风险等级: `HIGH` / `MEDIUM` / `SAFE` | 始终 |
| `reason` | String | 判断依据 | 非 SAFE 时 |
| `score` | Double | 风险分数 [0.0, 1.0] | 非黑名单命中时 |
| `source` | String | 黑名单来源: `authority` / `global` / `temp` | 仅黑名单命中时 |
| `transcription` | String | AI 语音转文字结果 | 正常流程时 |

**三种结果路径：**

```
路径 1 — 黑名单命中（不调 AI，不写库）
  响应: { riskLevel: "HIGH", reason: "...", source: "authority|global|temp" }
  特征: 无 score 字段

路径 2 — 关键词命中
  响应: { riskLevel: "HIGH", reason: "关键词命中", score: 1.0, transcription: "..." }

路径 3 — 综合评分
  响应: { riskLevel: "HIGH|MEDIUM|SAFE", score: 0.xx, reason: "...", transcription: "..." }
  score ≥ 0.6 → HIGH, score ∈ [0.3, 0.6) → MEDIUM, score < 0.3 → SAFE
```

**示例请求 (Android/Kotlin)：**

```kotlin
val body = MultipartBody.Builder()
    .setType(MultipartBody.FORM)
    .addFormDataPart("deviceId", deviceId)
    .addFormDataPart("authorId", authorId)
    .addFormDataPart("video", "video.mp4", videoBytes.toRequestBody("video/mp4".toMediaType()))
    .build()

val response = api.post("http://host:8080/api/v1/detect", body)
```

**示例响应（正常流程-HIGH）：**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "riskLevel": "HIGH",
    "reason": "综合评分过高",
    "score": 0.655,
    "transcription": "转账汇款..."
  }
}
```

**示例响应（黑名单命中）：**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "riskLevel": "HIGH",
    "reason": "权威黑名单发布者",
    "source": "authority"
  }
}
```

---

## 4. 历史查询接口

### GET /api/v1/history

APP 启动时获取检测历史，支持增量同步和分页浏览。

**请求参数：**

| 参数 | 类型 | 必填 | 默认 | 说明 |
|------|------|------|------|------|
| `deviceId` | String | 是 | — | 设备标识 |
| `afterId` | Long | 否 | — | 增量游标，返回此 ID 之后的新记录 |
| `page` | Int | 否 | 1 | 页码（afterId 存在时忽略） |
| `size` | Int | 否 | 20 | 每页条数（上限 100） |

**使用模式：**

```
模式 A — APP 启动增量同步:
  GET /api/v1/history?deviceId=X&afterId=<本地最新id>&size=50
  返回上次同步之后的新记录，用于刷新列表。

模式 B — 首次加载 / 上拉加载更多:
  GET /api/v1/history?deviceId=X&page=1&size=20   ← 首页
  GET /api/v1/history?deviceId=X&page=2&size=20   ← 翻页
```

**响应 data 对象 (HistoryResponse)：**

| 字段 | 类型 | 说明 |
|------|------|------|
| `records` | Array | 检测记录列表（不含 rawAiResult 原始数据） |
| `total` | Long | 该设备总记录数 |
| `page` | Int | 当前页码 |
| `size` | Int | 当前页大小 |
| `hasMore` | Boolean | 是否还有更多数据（用于判断继续翻页/增量拉取） |
| `latestId` | Long | 当前批次最大 ID，APP 应持久化此值作为下次 `afterId` |

**records 元素 (HistoryItem)：**

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | Long | 记录 ID，递增，用于增量同步游标 |
| `deviceId` | String | 设备 ID |
| `authorId` | String | 发布者 ID |
| `riskLevel` | String | `HIGH` / `MEDIUM` / `SAFE` |
| `score` | Double | 风险评分 |
| `createdAt` | String | 检测时间 (ISO 8601) |

**示例响应：**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "records": [
      {
        "id": 142,
        "deviceId": "device-abc",
        "authorId": "author-xyz",
        "riskLevel": "HIGH",
        "score": 0.85,
        "createdAt": "2026-05-20T14:30:00"
      },
      {
        "id": 140,
        "deviceId": "device-abc",
        "authorId": "author-pqr",
        "riskLevel": "SAFE",
        "score": 0.12,
        "createdAt": "2026-05-20T13:15:00"
      }
    ],
    "total": 142,
    "page": 1,
    "size": 20,
    "hasMore": true,
    "latestId": 142
  }
}
```

**APP 增量同步流程：**

```
1. APP 启动
2. 读取本地持久化的 lastSyncedId (若首次则为 null)
3. GET /api/v1/history?deviceId=X&afterId=<lastSyncedId>&size=50
4. 将返回的 records 合并到本地列表头部
5. 如果 hasMore=true，可继续用 lastSyncedId=response.latestId 再拉一次直到 hasMore=false
6. 持久化 latestId 为新的 lastSyncedId
```

---

## 5. 黑名单接口

### 5.1 综合查询 — GET /api/v1/blacklist/check/{authorId}

一次查询三层黑名单，按优先级返回命中结果。

优先级: **authority > global > temp**（命中即停止）

**响应 data：**

```json
// 命中
{ "hit": true, "source": "authority", "reason": "权威黑名单发布者" }

// 未命中
{ "hit": false, "source": null, "reason": null }
```

### 5.2 各层 CRUD 接口

三层黑名单端点结构一致，以 authority 为例：

```
POST   /api/v1/blacklist/authority              body: {"authorId": "xxx"}
GET    /api/v1/blacklist/authority/{authorId}   → {"data": {"blacklisted": true}}
DELETE /api/v1/blacklist/authority/{authorId}
GET    /api/v1/blacklist/authority              → {"data": ["id1","id2",...]}
```

Temp 层差异：
- `POST /temp` body 多一个可选 `reason` 字段：`{"authorId": "xxx", "reason": "原因"}`
- `GET /temp/{id}` 返回多一个 `ttlSeconds` 字段：`{"blacklisted": true, "ttlSeconds": 86352}`
- Temp 黑名单 24 小时后自动过期
- HIGH 风险检测后系统自动加入 temp

---

## 6. 枚举定义

### RiskLevel

| 值 | 含义 |
|----|------|
| `HIGH` | 高风险（黑名单 / 关键词命中 / 综合分 ≥ 0.6） |
| `MEDIUM` | 中风险（综合分 [0.3, 0.6)） |
| `SAFE` | 安全（综合分 < 0.3） |

### 黑名单 source

| 值 | 含义 | TTL |
|----|------|-----|
| `authority` | 权威黑名单（官方平台发布） | 永久 |
| `global` | 全局黑名单（管理员添加） | 永久 |
| `temp` | 临时黑名单（系统自动或手动添加） | 24 小时 |

---

## 7. 完整业务流程

### 用户拍摄/上传视频

```
1. APP 调用 POST /api/v1/detect 上传视频
2. 解析响应:
   - riskLevel=HIGH → 高风险提示，阻止发布
   - riskLevel=MEDIUM → 警告，建议人工审核
   - riskLevel=SAFE → 通过
3. 本地缓存本次检测记录 id，更新 lastSyncedId
```

### APP 冷启动

```
1. APP 启动
2. GET /api/v1/history?deviceId=X&afterId=<本地lastSyncedId>&size=50
3. 合并新记录到本地列表
4. 持久化 latestId
```

### 历史列表上拉加载更多

```
1. 用户滑动到列表底部
2. GET /api/v1/history?deviceId=X&page=<下一页>&size=20
3. 追加到列表尾部
4. hasMore=false 时停止加载
```

---

## 8. 注意事项

1. **视频大小限制**: 最大 10MB，超出返回 400
2. **设备 ID 稳定性**: `deviceId` 应使用设备唯一标识（如 Android 的 `ANDROID_ID` 或自生成 UUID），卸载重装后会变化，历史数据将无法关联
3. **增量同步游标**: `latestId` 是全局递增的，跨设备共享同一个 ID 序列。务必持久化最新的 `latestId`
4. **黑名单 source 字段**: 仅在黑名单命中时返回。正常检测流程中此字段不出现，不要依赖其存在
5. **score 字段**: 黑名单命中时无此字段（不经过 AI 评分），其他情况均有
6. **Temp 黑名单自动过期**: 24 小时后自动清除，`ttlSeconds` 表示剩余秒数
7. **幂等性**: 同一视频多次上传会产生多条检测记录，APP 侧建议做去重
