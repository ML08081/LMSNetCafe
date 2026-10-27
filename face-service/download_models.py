"""Download pinned OpenCV Zoo models; validate bytes before making them available."""
import hashlib
import json
from pathlib import Path
from urllib.request import urlopen


def main():
    root = Path(__file__).resolve().parent / "models"
    manifest = json.loads((root / "manifest.json").read_text(encoding="utf-8"))
    for item in manifest["files"]:
        target = root / item["name"]
        if target.exists() and hashlib.sha256(target.read_bytes()).hexdigest() == item["sha256"]:
            print(f"OK {target.name}")
            continue
        url = (f"https://media.githubusercontent.com/media/opencv/opencv_zoo/{manifest['revision']}"
               f"/models/{item['folder']}/{item['name']}")
        print(f"Downloading {target.name}", flush=True)
        with urlopen(url, timeout=120) as response:
            content = response.read()
        if hashlib.sha256(content).hexdigest() != item["sha256"]:
            raise RuntimeError(f"Checksum mismatch: {target.name}")
        temporary = target.with_suffix(".download")
        temporary.write_bytes(content)
        temporary.replace(target)
        print(f"OK {target.name}")


if __name__ == "__main__":
    main()
