"""
Mock 后端 + AI 服务 — 一站式替代 Spring Boot 后端和 Python AI 服务。

启动:
  pip install flask
  python docs/server_fake/mock_ai_server.py

默认监听 8080 端口，可通过 PORT 环境变量修改。
APP 端只需将后端地址指向此服务即可。

## 场景切换方法

### 方式 1: 通过 authorId 前缀（检测接口）

| authorId 前缀        | 场景                         |
|----------------------|------------------------------|
| `safe_`              | 正常检测 → SAFE              |
| `medium_`            | 正常检测 → MEDIUM            |
| `high_`              | 正常检测 → HIGH（综合评分）  |
| `keyword_`           | 正常检测 → HIGH（关键词命中）|
| `bl_authority_`      | 黑名单命中 — authority       |
| `bl_global_`         | 黑名单命中 — global          |
| `bl_temp_`           | 黑名单命中 — temp            |

其他 authorId 默认返回 SAFE。

### 方式 2: 通过 X-Mock-Status Header（所有接口）

| Header 值           | 效果                          |
|---------------------|-------------------------------|
| `401`               | 认证失败（无视 API Key）      |
| `429`               | 限流 + Retry-After 头         |
| `500`               | 服务器内部错误                |
| `502`               | AI 服务不可用                 |
| `503`               | 数据库/缓存异常               |

### 方式 3: 过一些列特殊的URL参数（仅限流相关接口）

无。所有场景可通过前两种方式覆盖。

## 支持的接口

  业务接口:
    POST   /api/v1/detect                  同步检测
    POST   /api/v1/detect/async            异步检测
    GET    /api/v1/detect/<taskId>/status  查询异步任务

    GET    /api/v1/history                 历史查询（支持过滤+分页+游标）

    GET    /api/v1/blacklist/check/<id>    综合黑名单查询
    GET    /api/v1/blacklist/authority     列出权威黑名单
    GET    /api/v1/blacklist/authority/<id> 检查权威黑名单
    POST   /api/v1/blacklist/authority     添加权威黑名单
    DELETE /api/v1/blacklist/authority/<id> 移除权威黑名单
    GET    /api/v1/blacklist/global        列出全局黑名单
    GET    /api/v1/blacklist/global/<id>   检查全局黑名单
    POST   /api/v1/blacklist/global        添加全局黑名单
    DELETE /api/v1/blacklist/global/<id>   移除全局黑名单
    GET    /api/v1/blacklist/temp          列出临时黑名单
    GET    /api/v1/blacklist/temp/<id>     检查临时黑名单(含TTL)
    POST   /api/v1/blacklist/temp          添加临时黑名单
    DELETE /api/v1/blacklist/temp/<id>     移除临时黑名单

    POST   /api/v1/keys/register           设备注册
    GET    /api/v1/keys                    列出所有Key(管理员)
    GET    /api/v1/keys/<id>               查看Key详情
    POST   /api/v1/keys                    创建Key
    PUT    /api/v1/keys/<id>               更新Key
    POST   /api/v1/keys/<id>/revoke        撤销Key
    DELETE /api/v1/keys/<id>               删除Key

    GET    /api/v1/stats                   仪表盘统计

  基础设施:
    GET    /actuator/health                健康检查
"""

import os
import json
import uuid
import time
import random
import threading
from datetime import datetime, timezone, timedelta
from flask import Flask, request, jsonify, g

app = Flask("mock-backend")

# ---------------------------------------------------------------------------
# 配置
# ---------------------------------------------------------------------------
TZ = timezone(timedelta(hours=8))  # Asia/Shanghai
API_KEY = os.environ.get("API_KEY", "changeme")
TEMP_TTL = 86400  # 24h

# ---------------------------------------------------------------------------
# 内存存储
# ---------------------------------------------------------------------------
_lock = threading.Lock()

# 异步任务
_tasks: dict = {}

# 黑名单
_authority_blacklist: set = set()
_global_blacklist: set = set()
_temp_blacklist: dict = {}  # authorId -> {"reason": str, "expires_at": float}

# API Key 管理
_next_key_id = 1
_api_keys: dict = {}  # id -> key_info

# 设备注册 (deviceId -> key_id)
_device_keys: dict = {}

# 检测记录 (用于 history 查询)
_next_record_id = 1
_records: list = []  # list of dicts

# 统计
_stats = {
    "detection_count": 0,
    "by_risk_level": {"HIGH": 0, "MEDIUM": 0, "SAFE": 0, "BLACKLISTED": 0},
    "blacklist_hits": 0,
    "rate_limit_exceeded": 0,
    "ai_calls": 0,
    "ai_total_ms": 0,
}


# ---------------------------------------------------------------------------
# 工具函数
# ---------------------------------------------------------------------------

def _now():
    return datetime.now(TZ).isoformat()


def _now_dt():
    return datetime.now(TZ)


def _ok(data=None, code=200, message="success"):
    body = {"code": code, "message": message}
    if data is not None:
        body["data"] = data
    return jsonify(body), code if code >= 400 else 200


def _err(code, message):
    return jsonify({"code": code, "message": message}), code


def _mock_status():
    """Check X-Mock-Status header for error simulation."""
    return request.headers.get("X-Mock-Status", "").strip()


def _require_api_key():
    """Check API key auth. Returns None if OK, error response if not."""
    if request.path.startswith("/actuator/"):
        return None
    if request.path == "/api/v1/keys/register" and request.method == "POST":
        return None  # Registration is auth-free

    # Mock status override
    ms = _mock_status()
    if ms == "401":
        return _err(401, "Missing or invalid API key")

    key = request.headers.get("X-API-Key", "")
    if not key:
        return _err(401, "Missing or invalid API key")
    if key != API_KEY:
        # Check dynamic keys
        valid = False
        for k in _api_keys.values():
            if k.get("keyValue") == key and k.get("status") == "ACTIVE":
                exp = k.get("expiredAt")
                if exp is not None:
                    if isinstance(exp, str):
                        exp = datetime.fromisoformat(exp)
                    if exp < _now_dt():
                        continue
                valid = True
                break
        if not valid:
            return _err(401, "Missing or invalid API key")
    return None


def _check_rate_limit():
    """Simple rate limit simulation. Returns None if OK."""
    ms = _mock_status()
    if ms == "429":
        resp = jsonify({"code": 429, "message": "请求过于频繁，请稍后再试"})
        resp.headers["Retry-After"] = "30"
        resp.headers["X-RateLimit-Limit"] = "20"
        resp.headers["X-RateLimit-Remaining"] = "0"
        resp.headers["X-RateLimit-Reset"] = str(int(time.time()) + 30)
        resp.status_code = 429
        return resp
    return None


def _add_rate_limit_headers(response):
    response.headers["X-RateLimit-Limit"] = "20"
    response.headers["X-RateLimit-Remaining"] = "19"
    response.headers["X-RateLimit-Reset"] = str(int(time.time()) + 60)
    return response


def _detect_scenario(author_id):
    """Determine detection scenario from authorId."""
    if author_id.startswith("bl_authority_"):
        return "blacklist", "authority"
    if author_id.startswith("bl_global_"):
        return "blacklist", "global"
    if author_id.startswith("bl_temp_"):
        return "blacklist", "temp"
    if author_id.startswith("safe_"):
        return "normal", "SAFE"
    if author_id.startswith("medium_"):
        return "normal", "MEDIUM"
    if author_id.startswith("high_"):
        return "normal", "HIGH"
    if author_id.startswith("keyword_"):
        return "keyword", "HIGH"
    return "normal", "SAFE"


def _build_detect_response(scenario_type, scenario_value):
    """Build DetectResponse based on scenario."""
    if scenario_type == "blacklist":
        source = scenario_value
        reason = {
            "authority": "权威黑名单发布者",
            "global": "全局黑名单发布者",
            "temp": "临时黑名单发布者: 触发高危检测",
        }.get(source, "黑名单发布者")
        return {
            "riskLevel": "HIGH",
            "reason": reason,
            "source": source,
        }
    elif scenario_type == "keyword":
        return {
            "riskLevel": "HIGH",
            "reason": "关键词命中",
            "score": 1.0,
            "transcription": "mock transcription: 转账汇款诈骗信息...",
        }
    else:
        level = scenario_value
        if level == "HIGH":
            return {
                "riskLevel": "HIGH",
                "reason": "综合评分过高",
                "score": 0.75,
                "transcription": "mock transcription: 可疑内容检测到风险词汇...",
            }
        elif level == "MEDIUM":
            return {
                "riskLevel": "MEDIUM",
                "reason": "综合评分中等",
                "score": 0.43,
                "transcription": "mock transcription: 内容部分可疑...",
            }
        else:
            return {
                "riskLevel": "SAFE",
                "score": 0.12,
                "transcription": "mock transcription: 正常内容...",
            }


# ---------------------------------------------------------------------------
# Health Check
# ---------------------------------------------------------------------------
@app.route("/actuator/health", methods=["GET"])
def health():
    return jsonify({
        "status": "UP",
        "components": {
            "db": {"status": "UP"},
            "redis": {"status": "UP"},
            "aiService": {"status": "UP"},
        },
    })


# ---------------------------------------------------------------------------
# Auth & Rate Limit — applied via before_request
# ---------------------------------------------------------------------------
@app.before_request
def before_request():
    auth_err = _require_api_key()
    if auth_err is not None:
        return auth_err

    rate_err = _check_rate_limit()
    if rate_err is not None:
        return rate_err

    # Global 500/503 mock
    ms = _mock_status()
    if ms == "500":
        return _err(500, "服务器内部错误")
    if ms == "503":
        return _err(503, "服务暂时不可用，请稍后重试")

    g.request_start = time.time()


@app.after_request
def after_request(response):
    if request.path.startswith("/actuator/"):
        return response
    return _add_rate_limit_headers(response)


# ---------------------------------------------------------------------------
# POST /api/v1/detect  (同步检测)
# ---------------------------------------------------------------------------
@app.route("/api/v1/detect", methods=["POST"])
def detect():
    device_id = (request.form.get("deviceId") or "").strip()
    author_id = (request.form.get("authorId") or "").strip()
    video = request.files.get("video")

    # --- 参数校验 ---
    if not device_id:
        return _err(400, "缺少必要参数: deviceId")
    if not author_id:
        return _err(400, "缺少必要参数: authorId")
    if video is None or video.filename == "":
        return _err(400, "请求必须包含视频文件")

    # Read video bytes to simulate processing
    video_bytes = video.read()
    vid_size = len(video_bytes)

    # Size check (10MB)
    if vid_size > 10 * 1024 * 1024:
        return _err(400, "视频文件过大，最大支持 10MB")

    # --- Determine scenario ---
    scenario_type, scenario_value = _detect_scenario(author_id)

    # --- AI Service error mock ---
    ms = _mock_status()
    if ms == "502":
        return _err(502, "AI 分析服务暂时不可用，请稍后重试")

    # --- Blacklist seed: if authorId prefix is bl_*, add to appropriate blacklist ---
    if scenario_type == "blacklist":
        source = scenario_value
        author_real = author_id[len("bl_authority_"):] if author_id.startswith("bl_authority_") else \
                      author_id[len("bl_global_"):] if author_id.startswith("bl_global_") else \
                      author_id[len("bl_temp_"):]
        with _lock:
            if source == "authority":
                _authority_blacklist.add(author_id)
            elif source == "global":
                _global_blacklist.add(author_id)
            elif source == "temp":
                _temp_blacklist[author_id] = {
                    "reason": "触发高危检测",
                    "expires_at": time.time() + TEMP_TTL,
                }
            _stats["blacklist_hits"] += 1
            _stats["by_risk_level"]["BLACKLISTED"] += 1

        result = _build_detect_response(scenario_type, scenario_value)
        print(f"[detect] deviceId={device_id} authorId={author_id}  → BLACKLIST ({source})")
        return _ok(result)

    # --- Normal detection ---
    with _lock:
        _stats["detection_count"] += 1
        _stats["by_risk_level"][scenario_value] += 1
        _stats["ai_calls"] += 1
        _stats["ai_total_ms"] += random.randint(500, 2000)

    result = _build_detect_response(scenario_type, scenario_value)
    level = result["riskLevel"]

    # Save to records (skip SAFE for history cleanliness — or save everything)
    with _lock:
        global _next_record_id
        record = {
            "id": _next_record_id,
            "deviceId": device_id,
            "authorId": author_id,
            "riskLevel": level,
            "score": result.get("score"),
            "createdAt": _now_dt().isoformat(),
        }
        _next_record_id += 1
        _records.append(record)

        # HIGH → auto-add to temp blacklist
        if level == "HIGH" and scenario_type != "blacklist":
            _temp_blacklist[author_id] = {
                "reason": result.get("reason", "触发高危检测"),
                "expires_at": time.time() + TEMP_TTL,
            }

    print(f"[detect] deviceId={device_id} authorId={author_id}  → {level}")
    return _ok(result)


# ---------------------------------------------------------------------------
# POST /api/v1/detect/async  (异步检测)
# ---------------------------------------------------------------------------
@app.route("/api/v1/detect/async", methods=["POST"])
def detect_async():
    device_id = (request.form.get("deviceId") or "").strip()
    author_id = (request.form.get("authorId") or "").strip()
    video = request.files.get("video")

    if not device_id:
        return _err(400, "缺少必要参数: deviceId")
    if not author_id:
        return _err(400, "缺少必要参数: authorId")
    if video is None or video.filename == "":
        return _err(400, "请求必须包含视频文件")

    video_bytes = video.read()
    if len(video_bytes) > 10 * 1024 * 1024:
        return _err(400, "视频文件过大，最大支持 10MB")

    task_id = uuid.uuid4().hex
    now = _now()
    now_dt = _now_dt()

    scenario_type, scenario_value = _detect_scenario(author_id)
    result = _build_detect_response(scenario_type, scenario_value)

    with _lock:
        _tasks[task_id] = {
            "taskId": task_id,
            "status": "DONE",
            "result": result,
            "createdAt": now,
            "updatedAt": now,
        }

    print(f"[detect/async] deviceId={device_id} authorId={author_id}  taskId={task_id} → DONE")

    return jsonify({
        "code": 202,
        "message": "任务已提交",
        "data": {
            "taskId": task_id,
            "status": "PENDING",
            "createdAt": now,
        },
    }), 202


# ---------------------------------------------------------------------------
# GET /api/v1/detect/<taskId>/status  (查询异步结果)
# ---------------------------------------------------------------------------
@app.route("/api/v1/detect/<task_id>/status", methods=["GET"])
def task_status(task_id):
    with _lock:
        task = _tasks.get(task_id)
    if task is None:
        return _err(404, "任务不存在或已过期")
    return _ok(task)


# ---------------------------------------------------------------------------
# GET /api/v1/history  (历史查询)
# ---------------------------------------------------------------------------
@app.route("/api/v1/history", methods=["GET"])
def history():
    device_id = request.args.get("deviceId", "").strip()
    if not device_id:
        return _err(400, "deviceId 不能为空")

    after_id = request.args.get("afterId")
    if after_id is not None:
        after_id = int(after_id)
    page = max(1, int(request.args.get("page", 1)))
    size = int(request.args.get("size", 20))
    size = max(1, min(100, size))

    # Filters
    author_id_filter = request.args.get("authorId")
    risk_level_filter = request.args.get("riskLevel")
    start_date = request.args.get("startDate")
    end_date = request.args.get("endDate")

    with _lock:
        # Filter records
        filtered = [r for r in _records if r["deviceId"] == device_id]

        if author_id_filter:
            filtered = [r for r in filtered if r["authorId"] == author_id_filter]
        if risk_level_filter:
            filtered = [r for r in filtered if r["riskLevel"] == risk_level_filter]
        if start_date:
            sd = datetime.fromisoformat(start_date)
            filtered = [r for r in filtered if datetime.fromisoformat(r["createdAt"]) >= sd]
        if end_date:
            ed = datetime.fromisoformat(end_date).replace(hour=23, minute=59, second=59)
            filtered = [r for r in filtered if datetime.fromisoformat(r["createdAt"]) <= ed]

        total = len(filtered)
        # Sort by id descending
        filtered.sort(key=lambda r: r["id"], reverse=True)

        if after_id is not None:
            # Cursor-based: records after afterId
            filtered = [r for r in filtered if r["id"] > after_id]
            total_filtered = len(filtered)
            records_page = filtered[:size + 1]
            has_more = len(records_page) > size
            if has_more:
                records_page = records_page[:size]
            effective_page = 1
        else:
            # Offset-based pagination
            total_filtered = len(filtered)
            start = (page - 1) * size
            end = start + size
            records_page = filtered[start:end]
            has_more = end < total_filtered
            effective_page = page

        latest_id = records_page[0]["id"] if records_page else None

    return _ok({
        "records": records_page,
        "total": total_filtered if after_id is not None else total,
        "page": effective_page,
        "size": size,
        "hasMore": has_more,
        "latestId": latest_id,
    })


# ---------------------------------------------------------------------------
# Blacklist — Check All
# ---------------------------------------------------------------------------
@app.route("/api/v1/blacklist/check/<author_id>", methods=["GET"])
def blacklist_check(author_id):
    with _lock:
        # Clean expired temp entries
        _clean_temp()

        if author_id in _authority_blacklist:
            return _ok({"hit": True, "source": "authority", "reason": "权威黑名单发布者"})
        if author_id in _global_blacklist:
            return _ok({"hit": True, "source": "global", "reason": "全局黑名单发布者"})
        if author_id in _temp_blacklist:
            entry = _temp_blacklist[author_id]
            return _ok({"hit": True, "source": "temp", "reason": "临时黑名单发布者: " + entry["reason"]})
    return _ok({"hit": False, "source": None, "reason": None})


def _clean_temp():
    now = time.time()
    expired = [k for k, v in _temp_blacklist.items() if v["expires_at"] <= now]
    for k in expired:
        del _temp_blacklist[k]


# ── Authority ──────────────────────────────────────────────────

@app.route("/api/v1/blacklist/authority", methods=["GET"])
def blacklist_authority_list():
    with _lock:
        return _ok(sorted(list(_authority_blacklist)))


@app.route("/api/v1/blacklist/authority/<author_id>", methods=["GET"])
def blacklist_authority_check(author_id):
    with _lock:
        blacklisted = author_id in _authority_blacklist
    return _ok({"blacklisted": blacklisted})


@app.route("/api/v1/blacklist/authority", methods=["POST"])
def blacklist_authority_add():
    body = request.get_json(silent=True) or {}
    author_id = (body.get("authorId") or "").strip()
    if not author_id:
        return _err(400, "authorId 不能为空")
    with _lock:
        _authority_blacklist.add(author_id)
    return _ok()


@app.route("/api/v1/blacklist/authority/<author_id>", methods=["DELETE"])
def blacklist_authority_remove(author_id):
    with _lock:
        _authority_blacklist.discard(author_id)
    return _ok()


# ── Global ─────────────────────────────────────────────────────

@app.route("/api/v1/blacklist/global", methods=["GET"])
def blacklist_global_list():
    with _lock:
        return _ok(sorted(list(_global_blacklist)))


@app.route("/api/v1/blacklist/global/<author_id>", methods=["GET"])
def blacklist_global_check(author_id):
    with _lock:
        blacklisted = author_id in _global_blacklist
    return _ok({"blacklisted": blacklisted})


@app.route("/api/v1/blacklist/global", methods=["POST"])
def blacklist_global_add():
    body = request.get_json(silent=True) or {}
    author_id = (body.get("authorId") or "").strip()
    if not author_id:
        return _err(400, "authorId 不能为空")
    with _lock:
        _global_blacklist.add(author_id)
    return _ok()


@app.route("/api/v1/blacklist/global/<author_id>", methods=["DELETE"])
def blacklist_global_remove(author_id):
    with _lock:
        _global_blacklist.discard(author_id)
    return _ok()


# ── Temp ────────────────────────────────────────────────────────

@app.route("/api/v1/blacklist/temp", methods=["GET"])
def blacklist_temp_list():
    with _lock:
        _clean_temp()
        return _ok(sorted(list(_temp_blacklist.keys())))


@app.route("/api/v1/blacklist/temp/<author_id>", methods=["GET"])
def blacklist_temp_check(author_id):
    with _lock:
        _clean_temp()
        if author_id in _temp_blacklist:
            entry = _temp_blacklist[author_id]
            ttl = max(0, int(entry["expires_at"] - time.time()))
            return _ok({"blacklisted": True, "ttlSeconds": ttl})
    return _ok({"blacklisted": False, "ttlSeconds": 0})


@app.route("/api/v1/blacklist/temp", methods=["POST"])
def blacklist_temp_add():
    body = request.get_json(silent=True) or {}
    author_id = (body.get("authorId") or "").strip()
    if not author_id:
        return _err(400, "authorId 不能为空")
    reason = body.get("reason", "手动添加")
    with _lock:
        _temp_blacklist[author_id] = {
            "reason": reason,
            "expires_at": time.time() + TEMP_TTL,
        }
    return _ok()


@app.route("/api/v1/blacklist/temp/<author_id>", methods=["DELETE"])
def blacklist_temp_remove(author_id):
    with _lock:
        _temp_blacklist.pop(author_id, None)
    return _ok()


# ---------------------------------------------------------------------------
# API Key Management
# ---------------------------------------------------------------------------

def _generate_key():
    return uuid.uuid4().hex


@app.route("/api/v1/keys/register", methods=["POST"])
def keys_register():
    body = request.get_json(silent=True) or {}
    device_id = (body.get("deviceId") or "").strip()
    if not device_id:
        return _err(400, "deviceId 不能为空")
    device_name = body.get("deviceName", device_id)

    with _lock:
        # Return existing if already registered
        if device_id in _device_keys:
            kid = _device_keys[device_id]
            key_info = _api_keys.get(kid)
            if key_info:
                return _ok({
                    "apiKey": key_info["keyValue"],
                    "expiresAt": key_info["expiredAt"],
                })

        global _next_key_id
        key_value = _generate_key()
        now_str = _now_dt().isoformat()
        key_info = {
            "id": _next_key_id,
            "keyValue": key_value,
            "keyName": device_name,
            "deviceId": device_id,
            "permissions": "DETECT,HISTORY",
            "status": "ACTIVE",
            "rateLimit": 20,
            "lastUsedAt": None,
            "expiredAt": None,
            "createdAt": now_str,
            "revokedAt": None,
        }
        _api_keys[_next_key_id] = key_info
        _device_keys[device_id] = _next_key_id
        _next_key_id += 1

    print(f"[keys/register] deviceId={device_id}  keyId={key_info['id']}")
    return _ok({
        "apiKey": key_value,
        "expiresAt": None,
    })


@app.route("/api/v1/keys", methods=["GET"])
def keys_list():
    with _lock:
        result = []
        for k in sorted(_api_keys.values(), key=lambda x: x["id"]):
            result.append({
                "id": k["id"],
                "keyName": k["keyName"],
                "deviceId": k["deviceId"],
                "permissions": k["permissions"],
                "status": k["status"],
                "rateLimit": k["rateLimit"],
                "lastUsedAt": k["lastUsedAt"],
                "expiredAt": k["expiredAt"],
                "createdAt": k["createdAt"],
            })
    return _ok(result)


@app.route("/api/v1/keys/<int:key_id>", methods=["GET"])
def keys_get(key_id):
    with _lock:
        k = _api_keys.get(key_id)
        if k is None:
            return _err(404, "Key not found")
        return _ok({
            "id": k["id"],
            "keyValue": k["keyValue"],
            "keyName": k["keyName"],
            "deviceId": k["deviceId"],
            "permissions": k["permissions"],
            "status": k["status"],
            "rateLimit": k["rateLimit"],
            "lastUsedAt": k["lastUsedAt"],
            "expiredAt": k["expiredAt"],
            "createdAt": k["createdAt"],
            "revokedAt": k["revokedAt"],
        })


@app.route("/api/v1/keys", methods=["POST"])
def keys_create():
    body = request.get_json(silent=True) or {}
    key_name = body.get("keyName", "手动创建")
    permissions = body.get("permissions", "DETECT,HISTORY")
    rate_limit = body.get("rateLimit", 20)
    expired_at = body.get("expiredAt")  # ISO string or None

    with _lock:
        global _next_key_id
        key_value = _generate_key()
        now_str = _now_dt().isoformat()
        key_info = {
            "id": _next_key_id,
            "keyValue": key_value,
            "keyName": key_name,
            "deviceId": None,
            "permissions": permissions,
            "status": "ACTIVE",
            "rateLimit": rate_limit,
            "lastUsedAt": None,
            "expiredAt": expired_at,
            "createdAt": now_str,
            "revokedAt": None,
        }
        _api_keys[_next_key_id] = key_info
        _next_key_id += 1

    print(f"[keys/create] id={key_info['id']} name={key_name}")
    return _ok({
        "apiKey": key_value,
        "expiresAt": expired_at,
    })


@app.route("/api/v1/keys/<int:key_id>", methods=["PUT"])
def keys_update(key_id):
    body = request.get_json(silent=True) or {}
    with _lock:
        k = _api_keys.get(key_id)
        if k is None:
            return _err(404, "Key not found")

        if "keyName" in body:
            k["keyName"] = body["keyName"]
        if "permissions" in body:
            k["permissions"] = body["permissions"]
        if "rateLimit" in body:
            k["rateLimit"] = body["rateLimit"]
        if "expiredAt" in body:
            k["expiredAt"] = body["expiredAt"]

    print(f"[keys/update] id={key_id}")
    return _ok()


@app.route("/api/v1/keys/<int:key_id>/revoke", methods=["POST"])
def keys_revoke(key_id):
    with _lock:
        k = _api_keys.get(key_id)
        if k is None:
            return _err(404, "Key not found")
        k["status"] = "REVOKED"
        k["revokedAt"] = _now_dt().isoformat()
    print(f"[keys/revoke] id={key_id}")
    return _ok()


@app.route("/api/v1/keys/<int:key_id>", methods=["DELETE"])
def keys_delete(key_id):
    with _lock:
        k = _api_keys.pop(key_id, None)
        if k is None:
            return _err(404, "Key not found")
        # Also remove from device registry
        for did, kid in list(_device_keys.items()):
            if kid == key_id:
                del _device_keys[did]
                break
    print(f"[keys/delete] id={key_id}")
    return _ok()


# ---------------------------------------------------------------------------
# Stats
# ---------------------------------------------------------------------------
@app.route("/api/v1/stats", methods=["GET"])
def stats():
    with _lock:
        _clean_temp()
        ai_avg = (_stats["ai_total_ms"] / _stats["ai_calls"]) if _stats["ai_calls"] > 0 else 0.0
        return _ok({
            "totalDetections": _stats["detection_count"],
            "byRiskLevel": {
                "HIGH": _stats["by_risk_level"]["HIGH"],
                "MEDIUM": _stats["by_risk_level"]["MEDIUM"],
                "SAFE": _stats["by_risk_level"]["SAFE"],
            },
            "blacklistHits": _stats["blacklist_hits"],
            "aiAvgDurationMs": round(ai_avg, 1),
            "aiCallCount": _stats["ai_calls"],
            "blacklistCounts": {
                "authority": len(_authority_blacklist),
                "global": len(_global_blacklist),
                "temp": len(_temp_blacklist),
            },
        })


# ---------------------------------------------------------------------------
# main
# ---------------------------------------------------------------------------
if __name__ == "__main__":
    port = int(os.environ.get("PORT", 8080))
    print(f"")
    print(f"  === Mock 后端 + AI 服务 ===")
    print(f"  监听: http://0.0.0.0:{port}")
    print(f"  Root API Key: {API_KEY}")
    print(f"")
    print(f"  场景切换:")
    print(f"    authorId 前缀: safe_ / medium_ / high_ / keyword_ / bl_authority_ / bl_global_ / bl_temp_")
    print(f"    X-Mock-Status:  401 / 429 / 500 / 502 / 503")
    print(f"")
    print(f"  注册设备: POST /api/v1/keys/register  body: {{\"deviceId\":\"xxx\"}}")
    print(f"  接口数量: 26 个端点")
    print(f"")
    app.run(host="0.0.0.0", port=port, debug=True)
