#!/usr/bin/env python3
"""
短视频反诈后端 — 集成测试 (unittest + Mock AI)

覆盖:
  - 三层黑名单 CRUD (authority / global / temp)
  - 黑名单优先级 (authority > global > temp)
  - 黑名单短路检测 (每层独立验证 + source 字段)
  - Temp 黑名单 TTL 验证 + HIGH 风险自动加入
  - 风险评分 (关键词命中 / 高 / 中 / 安全 / 边界值)
  - 异常场景 (缺少参数)

用法:
  pip install flask requests
  python -m pytest test_aiscanner.py -v    # pytest
  python test_aiscanner.py                  # unittest

Spring Boot 必须先启动:
  ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
"""

import sys
import threading
import time
import unittest
from unittest import mock

# ---------------------------------------------------------------------------
# dependency checks
# ---------------------------------------------------------------------------
try:
    from flask import Flask, request, jsonify
except ImportError:
    print("[FATAL] 缺少 flask →  pip install flask")
    sys.exit(1)

try:
    import requests as http
except ImportError:
    print("[FATAL] 缺少 requests →  pip install requests")
    sys.exit(1)

# ---------------------------------------------------------------------------
# config
# ---------------------------------------------------------------------------
MOCK_AI_PORT = 8000
SPRING_BOOT_URL = "http://localhost:8080"
VIDEO_BYTES = b"\x00\x00\x00\x1cftypmp42\x00\x00\x00\x00isommp42"  # 最小合法 MP4

BL = f"{SPRING_BOOT_URL}/api/v1/blacklist"
DETECT = f"{SPRING_BOOT_URL}/api/v1/detect"

# ---------------------------------------------------------------------------
# mock AI service
# ---------------------------------------------------------------------------
_next_ai_response: dict = {
    "aiGlitchProb": 0.1,
    "violenceProb": 0.1,
    "transcription": "mock transcription",
    "keywordHit": False,
}

flask_app = Flask("mock-ai")


@flask_app.route("/v1/analyze/video", methods=["POST"])
def analyze_video():
    if "file" not in request.files:
        return jsonify({"error": "no file"}), 400
    return jsonify(_next_ai_response)


@flask_app.route("/test/ai-response", methods=["POST"])
def set_ai_response():
    global _next_ai_response
    _next_ai_response = request.get_json(force=True)
    return jsonify({"status": "ok", "next": _next_ai_response})


@flask_app.route("/test/ai-response/reset", methods=["POST"])
def reset_ai_response():
    global _next_ai_response
    _next_ai_response = {
        "aiGlitchProb": 0.1,
        "violenceProb": 0.1,
        "transcription": "mock transcription",
        "keywordHit": False,
    }
    return jsonify({"status": "reset"})


def _run_mock_ai():
    flask_app.run(host="127.0.0.1", port=MOCK_AI_PORT, use_reloader=False)


# ---------------------------------------------------------------------------
# helpers
# ---------------------------------------------------------------------------

def _set_ai_response(body: dict):
    r = http.post(
        f"http://127.0.0.1:{MOCK_AI_PORT}/test/ai-response", json=body, timeout=3
    )
    assert r.status_code == 200, f"设置 AI 响应失败: {r.status_code}"


def _reset_ai():
    http.post(
        f"http://127.0.0.1:{MOCK_AI_PORT}/test/ai-response/reset", timeout=3
    )


def _detect(device_id: str, author_id: str) -> dict:
    r = http.post(
        DETECT,
        data={"deviceId": device_id, "authorId": author_id},
        files={"video": ("test.mp4", VIDEO_BYTES, "video/mp4")},
        timeout=15,
    )
    return r.json()


def _api_post(path: str, body: dict) -> dict:
    r = http.post(f"{BL}{path}", json=body, timeout=10)
    return r.json()


def _api_get(path: str) -> dict:
    r = http.get(f"{BL}{path}", timeout=10)
    return r.json()


def _api_delete(path: str) -> dict:
    r = http.delete(f"{BL}{path}", timeout=10)
    return r.json()


def _assert_ok(data: dict, msg: str = ""):
    assert data["code"] == 200, f"code={data['code']} msg={data.get('message')} {msg}"


# ---------------------------------------------------------------------------
# base test case
# ---------------------------------------------------------------------------

class BaseTestCase(unittest.TestCase):
    """所有测试的基类 — Mock AI 服务生命周期由 run_tests() 管理"""

    def setUp(self):
        _reset_ai()


# ---------------------------------------------------------------------------
# 1. Authority 黑名单 CRUD
# ---------------------------------------------------------------------------

class TestAuthorityBlacklistCrud(BaseTestCase):
    """权威黑名单 (permanent Set) CRUD"""

    def test_add_and_check(self):
        """POST /authority 添加 → GET /authority/{id} 确认已拉黑"""
        aid = "test_auth_crud"
        self.addCleanup(_api_delete, f"/authority/{aid}")

        data = _api_post("/authority", {"authorId": aid})
        _assert_ok(data)
        self.assertEqual(200, data["code"])

        data = _api_get(f"/authority/{aid}")
        _assert_ok(data)
        self.assertTrue(data["data"]["blacklisted"])

    def test_check_nonexistent(self):
        """GET /authority/{id} — 未加入的作者返回 false"""
        data = _api_get("/authority/nonexistent_xyz_123")
        _assert_ok(data)
        self.assertFalse(data["data"]["blacklisted"])

    def test_list_contains_added(self):
        """GET /authority — 列表包含已添加的作者"""
        aid = "test_auth_list"
        self.addCleanup(_api_delete, f"/authority/{aid}")

        _api_post("/authority", {"authorId": aid})
        data = _api_get("/authority")
        _assert_ok(data)
        self.assertIn(aid, data["data"])

    def test_remove_and_verify(self):
        """DELETE /authority/{id} 移除后查询确认为 false"""
        aid = "test_auth_remove"
        _api_post("/authority", {"authorId": aid})

        data = _api_delete(f"/authority/{aid}")
        _assert_ok(data)

        data = _api_get(f"/authority/{aid}")
        _assert_ok(data)
        self.assertFalse(data["data"]["blacklisted"])

    def test_empty_author_id(self):
        """POST /authority — 空 authorId 返回 400"""
        data = _api_post("/authority", {"authorId": ""})
        self.assertEqual(400, data["code"])


# ---------------------------------------------------------------------------
# 2. Global 黑名单 CRUD
# ---------------------------------------------------------------------------

class TestGlobalBlacklistCrud(BaseTestCase):
    """全局黑名单 (permanent Set) CRUD"""

    def test_add_and_check(self):
        """POST /global 添加 → GET /global/{id} 确认已拉黑"""
        aid = "test_global_crud"
        self.addCleanup(_api_delete, f"/global/{aid}")

        data = _api_post("/global", {"authorId": aid})
        _assert_ok(data)

        data = _api_get(f"/global/{aid}")
        _assert_ok(data)
        self.assertTrue(data["data"]["blacklisted"])

    def test_check_nonexistent(self):
        """GET /global/{id} — 未加入的作者返回 false"""
        data = _api_get("/global/nonexistent_xyz_123")
        _assert_ok(data)
        self.assertFalse(data["data"]["blacklisted"])

    def test_list_contains_added(self):
        """GET /global — 列表包含已添加的作者"""
        aid = "test_global_list"
        self.addCleanup(_api_delete, f"/global/{aid}")

        _api_post("/global", {"authorId": aid})
        data = _api_get("/global")
        _assert_ok(data)
        self.assertIn(aid, data["data"])

    def test_remove_and_verify(self):
        """DELETE /global/{id} 移除后查询确认为 false"""
        aid = "test_global_remove"
        _api_post("/global", {"authorId": aid})

        _api_delete(f"/global/{aid}")
        data = _api_get(f"/global/{aid}")
        _assert_ok(data)
        self.assertFalse(data["data"]["blacklisted"])

    def test_empty_author_id(self):
        """POST /global — 空 authorId 返回 400"""
        data = _api_post("/global", {"authorId": ""})
        self.assertEqual(400, data["code"])


# ---------------------------------------------------------------------------
# 3. Temp 黑名单 CRUD (含 TTL)
# ---------------------------------------------------------------------------

class TestTempBlacklistCrud(BaseTestCase):
    """临时黑名单 (String with 24h TTL) CRUD"""

    def test_add_and_check(self):
        """POST /temp 添加 → GET /temp/{id} 确认已拉黑且 TTL > 0"""
        aid = "test_temp_crud"
        reason = "测试原因_违规内容"
        self.addCleanup(_api_delete, f"/temp/{aid}")

        data = _api_post("/temp", {"authorId": aid, "reason": reason})
        _assert_ok(data)

        data = _api_get(f"/temp/{aid}")
        _assert_ok(data)
        self.assertTrue(data["data"]["blacklisted"])
        self.assertGreater(data["data"]["ttlSeconds"], 0)
        self.assertLessEqual(data["data"]["ttlSeconds"], 86400)

    def test_check_nonexistent(self):
        """GET /temp/{id} — 未加入的作者返回 false, ttl=0"""
        data = _api_get("/temp/nonexistent_xyz_123")
        _assert_ok(data)
        self.assertFalse(data["data"]["blacklisted"])
        self.assertEqual(0, data["data"]["ttlSeconds"])

    def test_list_contains_added(self):
        """GET /temp — 列表包含已添加的临时黑名单作者"""
        aid = "test_temp_list"
        self.addCleanup(_api_delete, f"/temp/{aid}")

        _api_post("/temp", {"authorId": aid, "reason": "test"})
        data = _api_get("/temp")
        _assert_ok(data)
        self.assertIn(aid, data["data"])

    def test_remove_and_verify(self):
        """DELETE /temp/{id} 移除后查询确认为 false"""
        aid = "test_temp_remove"
        _api_post("/temp", {"authorId": aid, "reason": "test"})

        _api_delete(f"/temp/{aid}")
        data = _api_get(f"/temp/{aid}")
        _assert_ok(data)
        self.assertFalse(data["data"]["blacklisted"])

    def test_empty_author_id(self):
        """POST /temp — 空 authorId 返回 400"""
        data = _api_post("/temp", {"authorId": ""})
        self.assertEqual(400, data["code"])

    def test_ttl_is_24_hours(self):
        """新加入的 temp 黑名单 TTL 约为 86400 秒"""
        aid = "test_temp_ttl"
        self.addCleanup(_api_delete, f"/temp/{aid}")

        _api_post("/temp", {"authorId": aid, "reason": "TTL测试"})
        data = _api_get(f"/temp/{aid}")
        _assert_ok(data)
        # 允许几秒误差（请求往返时间）
        self.assertAlmostEqual(data["data"]["ttlSeconds"], 86400, delta=10)

    @mock.patch("time.time")
    def test_reason_stored(self, _mock_time):
        """添加时传入的 reason 暂存在黑名单值中，通过 /check 接口验证"""
        aid = "test_temp_reason"
        self.addCleanup(_api_delete, f"/temp/{aid}")

        _api_post("/temp", {"authorId": aid, "reason": "手动审核违规"})
        data = _api_get(f"/check/{aid}")
        _assert_ok(data)
        self.assertTrue(data["data"]["hit"])
        self.assertEqual("temp", data["data"]["source"])
        self.assertIn("手动审核违规", data["data"]["reason"])


# ---------------------------------------------------------------------------
# 4. 综合查询 + 优先级
# ---------------------------------------------------------------------------

class TestBlacklistCheckAll(BaseTestCase):
    """GET /check/{id} — 综合查询三层黑名单，验证优先级"""

    def _cleanup_all(self, aid: str):
        _api_delete(f"/authority/{aid}")
        _api_delete(f"/global/{aid}")
        _api_delete(f"/temp/{aid}")

    def test_not_blacklisted(self):
        """未在任何黑名单中 → hit=false"""
        aid = "test_check_none"
        self.addCleanup(self._cleanup_all, aid)
        self._cleanup_all(aid)

        data = _api_get(f"/check/{aid}")
        _assert_ok(data)
        self.assertFalse(data["data"]["hit"])
        self.assertIsNone(data["data"]["source"])
        self.assertIsNone(data["data"]["reason"])

    def test_authority_only(self):
        """仅在 authority 中 → source='authority'"""
        aid = "test_check_auth"
        self.addCleanup(self._cleanup_all, aid)
        self._cleanup_all(aid)
        _api_post("/authority", {"authorId": aid})

        data = _api_get(f"/check/{aid}")
        _assert_ok(data)
        self.assertTrue(data["data"]["hit"])
        self.assertEqual("authority", data["data"]["source"])

    def test_global_only(self):
        """仅在 global 中 → source='global'"""
        aid = "test_check_global"
        self.addCleanup(self._cleanup_all, aid)
        self._cleanup_all(aid)
        _api_post("/global", {"authorId": aid})

        data = _api_get(f"/check/{aid}")
        _assert_ok(data)
        self.assertTrue(data["data"]["hit"])
        self.assertEqual("global", data["data"]["source"])

    def test_temp_only(self):
        """仅在 temp 中 → source='temp'"""
        aid = "test_check_temp"
        self.addCleanup(self._cleanup_all, aid)
        self._cleanup_all(aid)
        _api_post("/temp", {"authorId": aid, "reason": "测试"})

        data = _api_get(f"/check/{aid}")
        _assert_ok(data)
        self.assertTrue(data["data"]["hit"])
        self.assertEqual("temp", data["data"]["source"])

    def test_authority_wins_over_global(self):
        """同时在 authority 和 global → authority 优先"""
        aid = "test_priority_ag"
        self.addCleanup(self._cleanup_all, aid)
        self._cleanup_all(aid)
        _api_post("/authority", {"authorId": aid})
        _api_post("/global", {"authorId": aid})

        data = _api_get(f"/check/{aid}")
        _assert_ok(data)
        self.assertEqual("authority", data["data"]["source"])

    def test_global_wins_over_temp(self):
        """同时在 global 和 temp → global 优先"""
        aid = "test_priority_gt"
        self.addCleanup(self._cleanup_all, aid)
        self._cleanup_all(aid)
        _api_post("/global", {"authorId": aid})
        _api_post("/temp", {"authorId": aid, "reason": "测试"})

        data = _api_get(f"/check/{aid}")
        _assert_ok(data)
        self.assertEqual("global", data["data"]["source"])

    def test_authority_wins_over_both(self):
        """同时在三层黑名单 → authority 优先"""
        aid = "test_priority_all"
        self.addCleanup(self._cleanup_all, aid)
        self._cleanup_all(aid)
        _api_post("/authority", {"authorId": aid})
        _api_post("/global", {"authorId": aid})
        _api_post("/temp", {"authorId": aid, "reason": "测试"})

        data = _api_get(f"/check/{aid}")
        _assert_ok(data)
        self.assertEqual("authority", data["data"]["source"])


# ---------------------------------------------------------------------------
# 5. 检测 — 黑名单短路 (每层独立)
# ---------------------------------------------------------------------------

class TestDetectionBlacklistShortCircuit(BaseTestCase):
    """检测接口 — 黑名单命中后直接返回 HIGH，不调用 AI，不写 DB"""

    def _cleanup_all(self, aid: str):
        _api_delete(f"/authority/{aid}")
        _api_delete(f"/global/{aid}")
        _api_delete(f"/temp/{aid}")

    def test_authority_blacklist_short_circuit(self):
        """authority 黑名单作者 → 直接 HIGH, source='authority', 不调用 AI"""
        aid = "detect_authority_short"
        self.addCleanup(self._cleanup_all, aid)
        self._cleanup_all(aid)
        _api_post("/authority", {"authorId": aid})

        # 设置一个会触发 SAFE 的 AI 响应 — 如果走了 AI 就不会是 HIGH
        _set_ai_response({
            "aiGlitchProb": 0.0,
            "violenceProb": 0.0,
            "transcription": "",
            "keywordHit": False,
        })
        data = _detect("device-auth", aid)
        _assert_ok(data)
        self.assertEqual("HIGH", data["data"]["riskLevel"])
        self.assertEqual("authority", data["data"]["source"])
        self.assertIsNone(data["data"]["score"])  # 短路时无 score

    def test_global_blacklist_short_circuit(self):
        """global 黑名单作者 → 直接 HIGH, source='global'"""
        aid = "detect_global_short"
        self.addCleanup(self._cleanup_all, aid)
        self._cleanup_all(aid)
        _api_post("/global", {"authorId": aid})

        _set_ai_response({
            "aiGlitchProb": 0.0,
            "violenceProb": 0.0,
            "transcription": "",
            "keywordHit": False,
        })
        data = _detect("device-global", aid)
        _assert_ok(data)
        self.assertEqual("HIGH", data["data"]["riskLevel"])
        self.assertEqual("global", data["data"]["source"])
        self.assertIsNone(data["data"]["score"])

    def test_temp_blacklist_short_circuit(self):
        """temp 黑名单作者 → 直接 HIGH, source='temp'"""
        aid = "detect_temp_short"
        self.addCleanup(self._cleanup_all, aid)
        self._cleanup_all(aid)
        _api_post("/temp", {"authorId": aid, "reason": "先前触发高危"})

        _set_ai_response({
            "aiGlitchProb": 0.0,
            "violenceProb": 0.0,
            "transcription": "",
            "keywordHit": False,
        })
        data = _detect("device-temp", aid)
        _assert_ok(data)
        self.assertEqual("HIGH", data["data"]["riskLevel"])
        self.assertEqual("temp", data["data"]["source"])
        self.assertIsNone(data["data"]["score"])


# ---------------------------------------------------------------------------
# 6. 检测 — HIGH 风险自动加入 temp 黑名单
# ---------------------------------------------------------------------------

class TestDetectionAutoAddTemp(BaseTestCase):
    """HIGH 风险检测后，自动将作者加入 temp 黑名单 (24h TTL)"""

    def _cleanup(self, aid: str):
        _api_delete(f"/authority/{aid}")
        _api_delete(f"/global/{aid}")
        _api_delete(f"/temp/{aid}")

    def test_high_risk_auto_adds_temp(self):
        """HIGH 风险检测 → 作者自动进入 temp 黑名单"""
        aid = "detect_auto_temp"
        self.addCleanup(self._cleanup, aid)
        self._cleanup(aid)

        # 触发 HIGH (关键词命中)
        _set_ai_response({
            "aiGlitchProb": 0.1,
            "violenceProb": 0.1,
            "transcription": "诈骗内容",
            "keywordHit": True,
        })
        data = _detect("device-auto", aid)
        _assert_ok(data)
        self.assertEqual("HIGH", data["data"]["riskLevel"])

        # 验证已进入 temp 黑名单
        data = _api_get(f"/temp/{aid}")
        _assert_ok(data)
        self.assertTrue(data["data"]["blacklisted"])
        self.assertGreater(data["data"]["ttlSeconds"], 0)

    def test_high_risk_auto_add_then_short_circuit(self):
        """第一次 HIGH → 自动入 temp，第二次请求直接从 temp 短路"""
        aid = "detect_auto_twice"
        self.addCleanup(self._cleanup, aid)
        self._cleanup(aid)

        # 第一次: 触发 HIGH
        _set_ai_response({
            "aiGlitchProb": 0.9,
            "violenceProb": 0.1,
            "transcription": "",
            "keywordHit": False,
        })
        data = _detect("device-twice", aid)
        _assert_ok(data)
        self.assertEqual("HIGH", data["data"]["riskLevel"])
        self.assertIsNotNone(data["data"]["score"])

        # 第二次: 应该从 temp 短路
        _set_ai_response({
            "aiGlitchProb": 0.0,
            "violenceProb": 0.0,
            "transcription": "",
            "keywordHit": False,
        })
        data = _detect("device-twice", aid)
        _assert_ok(data)
        self.assertEqual("HIGH", data["data"]["riskLevel"])
        self.assertEqual("temp", data["data"]["source"])

    def test_high_score_auto_adds_temp(self):
        """综合评分 >= 0.6 → HIGH → 自动进入 temp 黑名单"""
        aid = "detect_auto_score"
        self.addCleanup(self._cleanup, aid)
        self._cleanup(aid)

        _set_ai_response({
            "aiGlitchProb": 0.85,
            "violenceProb": 0.20,
            "transcription": "",
            "keywordHit": False,
        })
        data = _detect("device-score", aid)
        _assert_ok(data)
        self.assertEqual("HIGH", data["data"]["riskLevel"])

        data = _api_get(f"/temp/{aid}")
        _assert_ok(data)
        self.assertTrue(data["data"]["blacklisted"])

    def test_medium_risk_does_not_auto_add_temp(self):
        """MEDIUM 风险 → 不自动加入 temp 黑名单"""
        aid = "detect_medium_no_temp"
        self.addCleanup(self._cleanup, aid)
        self._cleanup(aid)

        _set_ai_response({
            "aiGlitchProb": 0.50,
            "violenceProb": 0.20,
            "transcription": "",
            "keywordHit": False,
        })
        data = _detect("device-med", aid)
        _assert_ok(data)
        self.assertEqual("MEDIUM", data["data"]["riskLevel"])

        data = _api_get(f"/temp/{aid}")
        _assert_ok(data)
        self.assertFalse(data["data"]["blacklisted"])

    def test_safe_risk_does_not_auto_add_temp(self):
        """SAFE → 不自动加入 temp 黑名单"""
        aid = "detect_safe_no_temp"
        self.addCleanup(self._cleanup, aid)
        self._cleanup(aid)

        _set_ai_response({
            "aiGlitchProb": 0.10,
            "violenceProb": 0.10,
            "transcription": "正常",
            "keywordHit": False,
        })
        data = _detect("device-safe", aid)
        _assert_ok(data)
        self.assertEqual("SAFE", data["data"]["riskLevel"])

        data = _api_get(f"/temp/{aid}")
        _assert_ok(data)
        self.assertFalse(data["data"]["blacklisted"])


# ---------------------------------------------------------------------------
# 7. 风险评分测试 (保留原有覆盖)
# ---------------------------------------------------------------------------

class TestDetectionRiskScoring(BaseTestCase):
    """检测 — 关键词命中 / 高 / 中 / 安全 / 边界值"""

    def test_keyword_hit_high(self):
        """keywordHit=true → HIGH, reason='关键词命中'"""
        _set_ai_response({
            "aiGlitchProb": 0.1,
            "violenceProb": 0.1,
            "transcription": "包含敏感词",
            "keywordHit": True,
        })
        data = _detect("device-kw", "author-kw")
        _assert_ok(data)
        self.assertEqual("HIGH", data["data"]["riskLevel"])
        self.assertEqual("关键词命中", data["data"]["reason"])
        self.assertEqual(1.0, data["data"]["score"])

    def test_high_score(self):
        """score >= 0.6 → HIGH, reason='综合评分过高'"""
        _set_ai_response({
            "aiGlitchProb": 0.85,
            "violenceProb": 0.20,
            "transcription": "",
            "keywordHit": False,
        })
        data = _detect("device-hi", "author-hi")
        _assert_ok(data)
        self.assertEqual("HIGH", data["data"]["riskLevel"])
        self.assertEqual("综合评分过高", data["data"]["reason"])
        self.assertAlmostEqual(data["data"]["score"], 0.655, places=3)

    def test_medium_score(self):
        """0.3 <= score < 0.6 → MEDIUM"""
        _set_ai_response({
            "aiGlitchProb": 0.50,
            "violenceProb": 0.20,
            "transcription": "",
            "keywordHit": False,
        })
        data = _detect("device-md", "author-md")
        _assert_ok(data)
        self.assertEqual("MEDIUM", data["data"]["riskLevel"])
        self.assertEqual("综合评分中等", data["data"]["reason"])
        self.assertAlmostEqual(data["data"]["score"], 0.41, places=3)

    def test_safe_score(self):
        """score < 0.3 → SAFE, reason 为 null"""
        _set_ai_response({
            "aiGlitchProb": 0.10,
            "violenceProb": 0.10,
            "transcription": "正常内容",
            "keywordHit": False,
        })
        data = _detect("device-sf", "author-sf")
        _assert_ok(data)
        self.assertEqual("SAFE", data["data"]["riskLevel"])
        self.assertAlmostEqual(data["data"]["score"], 0.10, places=3)

    def test_boundary_06_is_high(self):
        """score == 0.6 → HIGH (边界)"""
        _set_ai_response({
            "aiGlitchProb": 0.60,
            "violenceProb": 0.60,
            "transcription": "",
            "keywordHit": False,
        })
        data = _detect("device-b1", "author-b1")
        _assert_ok(data)
        self.assertEqual("HIGH", data["data"]["riskLevel"])
        self.assertAlmostEqual(data["data"]["score"], 0.60, places=3)

    def test_boundary_03_is_medium(self):
        """score == 0.3 → MEDIUM (边界)"""
        _set_ai_response({
            "aiGlitchProb": 0.30,
            "violenceProb": 0.30,
            "transcription": "",
            "keywordHit": False,
        })
        data = _detect("device-b2", "author-b2")
        _assert_ok(data)
        self.assertEqual("MEDIUM", data["data"]["riskLevel"])
        self.assertAlmostEqual(data["data"]["score"], 0.30, places=3)

    def test_boundary_029_is_safe(self):
        """score == 0.29 → SAFE (低于 MEDIUM 阈值)"""
        _set_ai_response({
            "aiGlitchProb": 0.29,
            "violenceProb": 0.29,
            "transcription": "",
            "keywordHit": False,
        })
        data = _detect("device-b3", "author-b3")
        _assert_ok(data)
        self.assertEqual("SAFE", data["data"]["riskLevel"])
        self.assertAlmostEqual(data["data"]["score"], 0.29, places=3)


# ---------------------------------------------------------------------------
# 8. 异常场景
# ---------------------------------------------------------------------------

class TestDetectionErrors(BaseTestCase):
    """检测 — 参数校验"""

    def test_missing_device_id(self):
        """缺少 deviceId → 400"""
        r = http.post(
            DETECT,
            data={"authorId": "x"},
            files={"video": ("t.mp4", VIDEO_BYTES, "video/mp4")},
            timeout=10,
        )
        self.assertEqual(400, r.json()["code"])

    def test_missing_author_id(self):
        """缺少 authorId → 400"""
        r = http.post(
            DETECT,
            data={"deviceId": "x"},
            files={"video": ("t.mp4", VIDEO_BYTES, "video/mp4")},
            timeout=10,
        )
        self.assertEqual(400, r.json()["code"])

    def test_missing_video(self):
        """缺少 video → 400"""
        r = http.post(
            DETECT, data={"deviceId": "x", "authorId": "y"}, timeout=10
        )
        self.assertEqual(400, r.json()["code"])


# ---------------------------------------------------------------------------
# 9. Mock 框架演示 — 使用 unittest.mock 验证请求-响应链路
# ---------------------------------------------------------------------------

class TestDetectionWithMockFramework(BaseTestCase):
    """使用 @mock.patch 验证请求格式与响应结构"""

    def _cleanup_all(self, aid: str):
        _api_delete(f"/authority/{aid}")
        _api_delete(f"/global/{aid}")
        _api_delete(f"/temp/{aid}")

    @mock.patch.object(http, 'post', wraps=http.post)
    def test_detect_request_format(self, mock_post):
        """Mock 验证: /api/v1/detect 请求的 multipart data 和 files 字段格式正确"""
        _reset_ai()
        mock_post.reset_mock()

        _detect("device-fmt", "author-fmt")

        # 从所有经过 http.post 的调用中找出对 detect 端点的调用
        detect_calls = [
            c for c in mock_post.call_args_list
            if "/api/v1/detect" in str(c.args[0])
        ]
        self.assertEqual(len(detect_calls), 1, "应该恰好发起一次检测请求")
        kwargs = detect_calls[0].kwargs
        self.assertEqual(kwargs["data"]["deviceId"], "device-fmt")
        self.assertEqual(kwargs["data"]["authorId"], "author-fmt")
        self.assertIn("video", kwargs["files"])
        self.assertEqual(kwargs["files"]["video"][0], "test.mp4")
        self.assertEqual(kwargs["files"]["video"][2], "video/mp4")

    @mock.patch.object(http, 'post', wraps=http.post)
    def test_blacklist_add_request_format(self, mock_post):
        """Mock 验证: 黑名单添加请求的 JSON body 格式正确"""
        aid = "mock_authority_fmt"
        self.addCleanup(_api_delete, f"/authority/{aid}")

        mock_post.reset_mock()
        _api_post("/authority", {"authorId": aid})

        add_calls = [
            c for c in mock_post.call_args_list
            if "/authority" in str(c.args[0])
            and "mock_authority_fmt" in str(c.kwargs.get("json", {}))
        ]
        self.assertEqual(len(add_calls), 1)
        self.assertEqual(add_calls[0].kwargs["json"]["authorId"], aid)


# ---------------------------------------------------------------------------
# main
# ---------------------------------------------------------------------------

def print_banner():
    print("=" * 60)
    print("短视频反诈后端 — 集成测试")
    print(f"Spring Boot: {SPRING_BOOT_URL}")
    print(f"Mock AI   : http://127.0.0.1:{MOCK_AI_PORT}")
    print("=" * 60)


def seed_blacklist():
    """预置一批种子数据到 Redis 三层黑名单"""
    SEEDS = {
        "/authority": [
            "seed_authority_001",
            "seed_authority_002",
        ],
        "/global": [
            "seed_global_001",
            "seed_global_002",
        ],
        "/temp": [
            ("seed_temp_001", "种子临时黑名单_001"),
            ("seed_temp_002", "种子临时黑名单_002"),
        ],
    }

    print("\n[种子] 写入三层黑名单到 Redis ...")
    for path, members in SEEDS.items():
        if path == "/temp":
            for aid, reason in members:
                r = http.post(f"{BL}{path}", json={"authorId": aid, "reason": reason})
                status = "✓" if r.status_code == 200 and r.json()["code"] == 200 else "✗"
                print(f"  {status} {path} → {aid} (reason={reason})")
        else:
            for aid in members:
                r = http.post(f"{BL}{path}", json={"authorId": aid})
                status = "✓" if r.status_code == 200 and r.json()["code"] == 200 else "✗"
                print(f"  {status} {path} → {aid}")

    # 验证
    for path in ["/authority", "/global", "/temp"]:
        r = http.get(f"{BL}{path}")
        items = r.json().get("data", [])
        print(f"  当前 {path} ({len(items)} 条): {items}")


def run_tests():
    """启动 Mock AI → 验证后端 → 种子数据 → 运行测试 → 报告"""

    # 1. 启动 Mock AI
    ai_thread = threading.Thread(target=_run_mock_ai, daemon=True)
    ai_thread.start()
    time.sleep(1.2)

    try:
        r = http.post(
            f"http://127.0.0.1:{MOCK_AI_PORT}/test/ai-response/reset", timeout=3
        )
        r.raise_for_status()
        print(f"Mock AI 已启动 (port {MOCK_AI_PORT})")
    except Exception as e:
        print(f"\n[FATAL] Mock AI 启动失败: {e}")
        sys.exit(1)

    # 2. 验证 Spring Boot 就绪
    try:
        r = http.get(f"{BL}/authority", timeout=5)
        r.raise_for_status()
        print("Spring Boot 已连接\n")
    except Exception as e:
        print(f"\n[FATAL] Spring Boot 未就绪 ({SPRING_BOOT_URL}): {e}")
        print("请先执行: ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev")
        sys.exit(1)

    # 3. 种子数据
    seed_blacklist()

    # 4. 运行测试
    loader = unittest.TestLoader()
    suite = unittest.TestSuite()
    suite.addTests(loader.loadTestsFromTestCase(TestAuthorityBlacklistCrud))
    suite.addTests(loader.loadTestsFromTestCase(TestGlobalBlacklistCrud))
    suite.addTests(loader.loadTestsFromTestCase(TestTempBlacklistCrud))
    suite.addTests(loader.loadTestsFromTestCase(TestBlacklistCheckAll))
    suite.addTests(loader.loadTestsFromTestCase(TestDetectionBlacklistShortCircuit))
    suite.addTests(loader.loadTestsFromTestCase(TestDetectionAutoAddTemp))
    suite.addTests(loader.loadTestsFromTestCase(TestDetectionRiskScoring))
    suite.addTests(loader.loadTestsFromTestCase(TestDetectionErrors))
    suite.addTests(loader.loadTestsFromTestCase(TestDetectionWithMockFramework))

    runner = unittest.TextTestRunner(verbosity=2)
    result = runner.run(suite)

    # 5. 汇总
    print("\n" + "=" * 60)
    print(f"结果: {result.testsRun - len(result.failures) - len(result.errors)}/{result.testsRun} 通过", end="")
    if result.wasSuccessful():
        print("  ✓ 全部通过")
        sys.exit(0)
    else:
        failed = len(result.failures) + len(result.errors)
        print(f", {failed} 失败")
        if result.failures:
            print("\n失败:")
            for test, traceback in result.failures:
                print(f"  [FAIL] {test}")
        if result.errors:
            print("\n错误:")
            for test, traceback in result.errors:
                print(f"  [ERROR] {test}")
        sys.exit(1)


if __name__ == "__main__":
    print_banner()
    run_tests()
