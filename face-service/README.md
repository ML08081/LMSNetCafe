# Face Service

FastAPI + OpenCV 人脸服务，提供真实的人脸检测、质量评估、特征录入、指定用户验证和 1:N 身份识别。

```powershell
python -m pip install -r requirements.txt
python download_models.py
python -m uvicorn app.main:app --host 127.0.0.1 --port 9000
```

采用 OpenCV YuNet 检测、关键点对齐和 SFace 特征提取。模型清单固定到官方仓库提交并校验 SHA-256，模型文件下载后可离线使用，不提交到 Git。首次安装或迁移电脑须执行模型下载脚本；项目启动脚本固定调用本目录 `.venv` 的 Python。

特征以压缩向量保存在 `data/features`，带模型版本标记，不保存每次验证原图。旧算法档案需要本人重新录入，不能混用。验证阈值为余弦相似度 0.363，1:N 识别阈值 0.40，并要求最佳与次佳候选至少相差 0.05；这些是演示基线，尚未在门店数据集上标定。没有活体检测，不应当作抗照片攻击方案。

健康接口会加载模型并返回 `ready`、`profilesRequiringEnrollment`；缺少或损坏模型会显示未就绪，不再只返回固定的 UP。

回归测试使用公共 OpenCV 样例，不接触实际人脸档案：

```powershell
.\.venv\Scripts\python.exe tests/download_fixtures.py
.\.venv\Scripts\python.exe -m unittest discover -s tests -v
```

Health endpoint:

```text
GET http://127.0.0.1:9000/face-service/health
```
