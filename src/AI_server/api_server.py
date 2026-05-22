import os
import tempfile
import subprocess
import uvicorn
from fastapi import FastAPI, UploadFile, File, HTTPException
from fastapi.responses import JSONResponse
import torch
import clip
from PIL import Image
import cv2
import numpy as np
import time
import whisper

# ========== 硬编码 ffmpeg 路径（请修改为你自己的路径）==========
FFMPEG_PATH = r"C:\Users\时轮冉然\video-checker\ffmpeg\bin\ffmpeg.exe"

# ========== 原有配置 ==========
DEVICE = "cuda" if torch.cuda.is_available() else "cpu"
MODEL_NAME = "ViT-B/32"
NUM_FRAMES = 16

CLASSES = [
    "a real video filmed by a camera, with natural movement and consistent shapes",
    "a video with visual glitches: objects warping, limbs bending unnaturally, textures flickering, or shapes melting",
    "a video containing physical violence, fighting, blood, or weapon"
]

RISK_WEIGHTS = {
    "a video with visual glitches: objects warping, limbs bending unnaturally, textures flickering, or shapes melting": 0.7,
    "a video containing physical violence, fighting, blood, or weapon": 0.3
}

# 文本风险关键词（可根据需要扩展）
TEXT_RISK_KEYWORDS = [
    "诈骗", "欺诈", "非法", "暴力", "杀人", "抢劫", "毒品", "赌博","投资返利","转账","返利"
    "fake", "scam", "violence", "kill", "rob", "drug", "gamble"
]

# ========== 加载模型（全局只加载一次）==========
print(f"加载 CLIP 模型到 {DEVICE}...")
clip_model, preprocess = clip.load(MODEL_NAME, device=DEVICE)
clip_model.eval()

# 预计算文本特征
text_inputs = torch.cat([clip.tokenize(c) for c in CLASSES]).to(DEVICE)
with torch.no_grad():
    text_features = clip_model.encode_text(text_inputs)
    text_features = text_features / text_features.norm(dim=-1, keepdim=True)

# 加载 Whisper 模型（使用 base 模型，可换为 tiny/small 等）
print("加载 Whisper 模型...")
whisper_model = whisper.load_model("base", device=DEVICE)

# ========== 辅助函数 ==========
def extract_audio_from_bytes(video_bytes: bytes, output_audio_path: str) -> bool:
    """从视频字节流提取音频，使用硬编码 ffmpeg 路径"""
    # 将视频字节流写入临时文件
    with tempfile.NamedTemporaryFile(delete=False, suffix=".mp4") as tmp_video:
        tmp_video.write(video_bytes)
        tmp_video_path = tmp_video.name
    try:
        cmd = [
            FFMPEG_PATH,
            "-i", tmp_video_path,
            "-vn",                     # 不处理视频
            "-acodec", "pcm_s16le",    # 音频编码
            "-ar", "16000",            # 采样率 16kHz
            "-ac", "1",                # 单声道
            "-y",                      # 覆盖输出文件
            output_audio_path
        ]
        subprocess.run(cmd, check=True, capture_output=True, timeout=30)
        return True
    except subprocess.CalledProcessError as e:
        print(f"ffmpeg 错误: {e.stderr.decode()}")
        return False
    except Exception as e:
        print(f"音频提取异常: {e}")
        return False
    finally:
        # 删除临时视频文件
        if os.path.exists(tmp_video_path):
            os.unlink(tmp_video_path)

def transcribe_audio(audio_path: str) -> str:
    """使用 Whisper 转录音频"""
    result = whisper_model.transcribe(audio_path, language="zh", task="transcribe")
    return result["text"].strip()

def contains_risk_keywords(text: str) -> bool:
    """检查文本是否包含风险关键词（中英文）"""
    if not text:
        return False
    text_lower = text.lower()
    for kw in TEXT_RISK_KEYWORDS:
        if kw.lower() in text_lower:
            return True
    return False

def sample_frames_from_bytes(video_bytes, num_frames=NUM_FRAMES):
    """从视频字节流中均匀采样帧，返回 PIL Image 列表（原有函数未改动）"""
    with tempfile.NamedTemporaryFile(delete=False, suffix=".mp4") as tmp:
        tmp.write(video_bytes)
        tmp_path = tmp.name
    try:
        cap = cv2.VideoCapture(tmp_path)
        total_frames = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
        if total_frames == 0:
            raise ValueError("视频无有效帧")
        indices = np.linspace(0, total_frames-1, num_frames, dtype=int)
        frames = []
        for idx in indices:
            cap.set(cv2.CAP_PROP_POS_FRAMES, idx)
            ret, frame = cap.read()
            if not ret:
                continue
            frame_rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
            frames.append(Image.fromarray(frame_rgb))
        cap.release()
        return frames
    finally:
        os.unlink(tmp_path)

def classify_frames(frames):
    """对帧列表进行分类，返回 (各类概率, 风险分数, 风险等级)"""
    if not frames:
        raise ValueError("无有效帧")
    frame_probs = []
    for img in frames:
        image_input = preprocess(img).unsqueeze(0).to(DEVICE)
        with torch.no_grad():
            image_features = clip_model.encode_image(image_input)
            image_features = image_features / image_features.norm(dim=-1, keepdim=True)
            logits = image_features @ text_features.T
            probs = logits.softmax(dim=-1).cpu().numpy()[0]
            frame_probs.append(probs)
    avg_probs = np.mean(frame_probs, axis=0)
    result = dict(zip(CLASSES, avg_probs))

    risk_score = 0.0
    for cls, w in RISK_WEIGHTS.items():
        risk_score += result.get(cls, 0) * w

    
    risk_score = float(risk_score)
    result = {k: float(v) for k, v in result.items()}
    

    if risk_score > 0.6:
        risk_level = "高危"
    elif risk_score > 0.3:
        risk_level = "中危"
    else:
        risk_level = "低危"

    return result, risk_score, risk_level
# ========== FastAPI 应用 ==========
app = FastAPI(title="视频内容安全分析API", description="实时检测AI翻车、暴力内容 + 音频文本风险筛查")

@app.post("/v1/analyze/video")
async def analyze_video(file: UploadFile = File(...)):
    # 1. 校验文件格式
    if not file.filename.lower().endswith(('.mp4', '.avi', '.mov')):
        raise HTTPException(400, "仅支持 mp4/avi/mov 格式")

    try:
        # 2. 读取上传的视频字节流
        video_bytes = await file.read()
        if len(video_bytes) == 0:
            raise HTTPException(400, "空文件")

        # 3. 提取音频并转文字（如果 ffmpeg 失败，忽略音频分析）
        audio_path = None
        transcript = ""
        try:
            with tempfile.NamedTemporaryFile(delete=False, suffix=".wav") as tmp_audio:
                audio_path = tmp_audio.name
            if extract_audio_from_bytes(video_bytes, audio_path):
                transcript = transcribe_audio(audio_path)
        except Exception as e:
            print(f"音频处理失败: {e}")
        finally:
            if audio_path and os.path.exists(audio_path):
                os.unlink(audio_path)

        # 4. 关键词检测
        keyword_hit = contains_risk_keywords(transcript)

        # 5. CLIP 帧分析
        frames = sample_frames_from_bytes(video_bytes)
        result, risk_score, risk_level = classify_frames(frames)

        ai_glitch_prob = round(result[CLASSES[1]], 4)
        violence_prob = round(result[CLASSES[2]], 4)

        return JSONResponse({
            "aiGlitchProb": ai_glitch_prob,
            "violenceProb": violence_prob,
            "transcription": transcript,
            "keywordHit": keyword_hit
        })
    except Exception as e:
        raise HTTPException(500, f"推理失败: {str(e)}")

@app.get("/health")
def health():
    return {"status": "ok"}

if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=8000)