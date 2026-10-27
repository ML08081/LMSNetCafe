"""Optional public OpenCV sample photos for integration tests; never enroll real users."""
from pathlib import Path
from urllib.request import urlopen

root = Path(__file__).parent / "fixtures"
root.mkdir(exist_ok=True)
for name in ("lena.jpg", "messi5.jpg"):
    url = f"https://raw.githubusercontent.com/opencv/opencv/4.10.0/samples/data/{name}"
    (root / name).write_bytes(urlopen(url, timeout=30).read())
    print(f"Downloaded public OpenCV test fixture: {name}")
