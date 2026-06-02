import os
import tempfile
import subprocess
import uvicorn
from fastapi import FastAPI, UploadFile, File, HTTPException
from fastapi.responses import JSONResponse
import torch
import clip
import joblib
from PIL import Image
import cv2
import numpy as np
import time
import whisper

# ========== ffmpeg/ffprobe 路径（环境变量优先，否则从 PATH 中查找）==========
import shutil as _shutil
FFMPEG_PATH = os.environ.get("FFMPEG_PATH", _shutil.which("ffmpeg"))
if not FFMPEG_PATH:
    raise RuntimeError("未找到 ffmpeg，请设置环境变量 FFMPEG_PATH 或将 ffmpeg 加入系统 PATH")
# 从 ffmpeg 路径推导 ffprobe 路径（同目录下）
_FFMPEG_DIR = os.path.dirname(FFMPEG_PATH)
FFPROBE_PATH = os.environ.get("FFPROBE_PATH", os.path.join(_FFMPEG_DIR, "ffprobe" + (".exe" if os.name == "nt" else "")))
if not os.path.isfile(FFPROBE_PATH):
    FFPROBE_PATH = _shutil.which("ffprobe")  # 回退到 PATH 查找

# ========== 配置 ==========
DEVICE = "cuda" if torch.cuda.is_available() else "cpu"
NUM_FRAMES = 16
TEXT_RISK_KEYWORDS = [
    "诈骗", "欺诈", "非法", "暴力", "杀人", "抢劫", "毒品", "赌博",
    "投资返利", "转账", "返利", "fake", "scam", "violence", "kill", "rob", "drug", "gamble"
]

# ========== 加载模型（全局一次）==========
print(f"加载 CLIP 模型到 {DEVICE}...")
clip_model, preprocess = clip.load("ViT-B/32", device=DEVICE)
clip_model.eval()

print("加载 Whisper 模型...")
whisper_model = whisper.load_model("base", device=DEVICE)

# 加载训练好的逻辑回归分类器
print("加载暴力检测模型...")
clf = joblib.load("violence_clf.pkl")
print("模型加载完成。")

# ========== 音频处理函数 ==========
def _has_audio_stream(video_path: str) -> bool:
    """使用 ffprobe 检查视频是否包含音频流"""
    if not FFPROBE_PATH:
        return True  # 无法探测时回退到尝试提取
    try:
        cmd = [
            FFPROBE_PATH,
            "-v", "error",
            "-select_streams", "a",
            "-show_entries", "stream=codec_type",
            "-of", "csv=p=0",
            video_path
        ]
        result = subprocess.run(cmd, check=False, capture_output=True, timeout=10)
        return result.returncode == 0 and result.stdout.strip() != b""
    except Exception:
        return True  # 探测失败时回退到尝试提取

def extract_audio_from_bytes(video_bytes: bytes, output_audio_path: str) -> bool:
    with tempfile.NamedTemporaryFile(delete=False, suffix=".mp4") as tmp_video:
        tmp_video.write(video_bytes)
        tmp_video.flush()
        os.fsync(tmp_video.fileno())
        tmp_video_path = tmp_video.name

    if os.path.getsize(tmp_video_path) == 0:
        print("音频提取失败: 视频文件为空")
        os.unlink(tmp_video_path)
        return False

    try:
        if not _has_audio_stream(tmp_video_path):
            print("视频无音频轨道，跳过音频提取")
            return False
        cmd = [
            FFMPEG_PATH,
            "-i", tmp_video_path,
            "-vn", "-acodec", "pcm_s16le", "-ar", "16000", "-ac", "1",
            "-y", output_audio_path
        ]
        subprocess.run(cmd, check=True, capture_output=True, timeout=30)
        return True
    except subprocess.CalledProcessError as e:
        print(f"ffmpeg 错误: {e.stderr.decode(errors='replace')}")
        return False
    except subprocess.TimeoutExpired:
        print("音频提取超时 (30s)")
        return False
    except Exception as e:
        print(f"音频提取异常: {e}")
        return False
    finally:
        if os.path.exists(tmp_video_path):
            os.unlink(tmp_video_path)

def transcribe_audio(audio_path: str) -> str:
    result = whisper_model.transcribe(audio_path, language="zh", task="transcribe")
    return result["text"].strip()

def contains_risk_keywords(text: str) -> bool:
    if not text:
        return False
    text_lower = text.lower()
    for kw in TEXT_RISK_KEYWORDS:
        if kw.lower() in text_lower:
            return True
    return False

# ========== 特征提取函数（与训练时一致）==========
def sample_frames_from_bytes(video_bytes, num_frames=NUM_FRAMES):
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

def extract_video_feature(video_bytes):
    frames = sample_frames_from_bytes(video_bytes)
    if not frames:
        return None
    features = []
    for img in frames:
        image_input = preprocess(img).unsqueeze(0).to(DEVICE)
        with torch.no_grad():
            feat = clip_model.encode_image(image_input).cpu().numpy().flatten()
            features.append(feat)
    return np.mean(features, axis=0) if features else None

# ========== FastAPI 应用 ==========
app = FastAPI(title="视频内容安全分析API (训练模型版)")

@app.post("/v1/analyze/video")
async def analyze_video(file: UploadFile = File(...)):
    if not file.filename.lower().endswith(('.mp4', '.avi', '.mov')):
        raise HTTPException(400, "仅支持 mp4/avi/mov 格式")

    try:
        video_bytes = await file.read()
        if len(video_bytes) == 0:
            raise HTTPException(400, "空文件")

        # 音频转文字及风险关键词检测
        transcript = ""
        audio_path = None
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

        keyword_hit = contains_risk_keywords(transcript)

        # 使用训练好的模型进行视觉分析
        feat = extract_video_feature(video_bytes)
        if feat is not None:
            violence_prob = float(clf.predict_proba([feat])[0][1])
        else:
            violence_prob = 0.0

        ai_glitch_prob = 0.0  # 当前模型未输出 AI 翻车概率

        return JSONResponse({
            "aiGlitchProb": round(ai_glitch_prob, 4),
            "violenceProb": round(violence_prob, 4),
            "transcription": transcript,
            "keywordHit": keyword_hit
        })
    except Exception as e:
        raise HTTPException(500, f"推理失败: {str(e)}")

@app.get("/")
def root():
    return {"status": "ok"}

@app.get("/health")
def health():
    return {"status": "ok"}

if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=8001)