项目名称：短视频反诈后端（Spring Boot + MyBatis + Redis + RocketMQ）

功能描述：

    客户端通过 POST /api/v1/detect 上传一个最长 5 秒的 MP4 视频文件，同时提供 deviceId 和 authorId（抖音作者号）。

    后端先查询 Redis 缓存的高危发布者黑名单，如果 authorId 在黑名单中，直接返回 { "riskLevel": "HIGH", "reason": "黑名单发布者" }，不调用 AI。

    若不在黑名单，则调用 Python AI 服务的 /v1/analyze/video 接口（HTTP POST，multipart 上传视频），获取分析结果（包含 aiGlitchProb, violenceProb, transcription, keywordHit）。

    后端根据风险融合规则计算最终风险等级：若 keywordHit==true 则直接 HIGH；否则 score = 0.7*aiGlitchProb + 0.3*violenceProb，score>=0.6 为 HIGH，0.3~0.6 为 MEDIUM，<0.3 为 SAFE。

    将检测记录（deviceId, authorId, riskLevel, score, rawAiResult 等）通过 MyBatis 存入 MySQL 数据库。

    返回给客户端 JSON 格式结果。

    额外提供黑名单管理 API（增删改查）。

技术栈：Spring Boot , MyBatis, MySQL, Redis, RocketMQ , 使用 Maven 构建。
技术框架需要使用最擅长，最可靠的版本。