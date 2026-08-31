# Face Service

FastAPI service for face enrollment, verification and identification.

```powershell
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 9000 --reload
```

Health endpoint:

```text
GET http://127.0.0.1:9000/face-service/health
```
