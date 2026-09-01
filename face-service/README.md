# Face Service

FastAPI + OpenCV 人脸服务，提供真实的人脸检测、质量评估、特征录入、指定用户验证和 1:N 身份识别。

```powershell
python -m pip install -r requirements.txt
python -m uvicorn app.main:app --host 127.0.0.1 --port 9000
```

特征以压缩向量保存在 `data/features`，不保存每次验证原图。生产环境应把该目录迁移到加密存储，并使用经过业务数据集校准的专业人脸模型替换当前 OpenCV 基线算法。

Health endpoint:

```text
GET http://127.0.0.1:9000/face-service/health
```
