"""seed/jobs.json의 공고들을 JobPosting 생성 API로 순차 전송한다.

사용법:
    python scripts/seed_jobs.py
    python scripts/seed_jobs.py --url http://localhost:8080/api/jobs --file src/main/resources/seed/jobs.json
"""

import argparse
import json
import sys
import urllib.error
import urllib.request
from pathlib import Path

sys.stdout.reconfigure(encoding="utf-8")

DEFAULT_URL = "http://localhost:8080/api/jobs"
DEFAULT_FILE = Path(__file__).resolve().parent.parent / "src/main/resources/seed/jobs.json"


def post_job(url: str, job: dict) -> tuple[int, str]:
    body = json.dumps(job, ensure_ascii=False).encode("utf-8")
    request = urllib.request.Request(
        url, data=body, method="POST", headers={"Content-Type": "application/json"}
    )
    try:
        with urllib.request.urlopen(request) as response:
            return response.status, response.read().decode("utf-8")
    except urllib.error.HTTPError as error:
        return error.code, error.read().decode("utf-8")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--url", default=DEFAULT_URL)
    parser.add_argument("--file", type=Path, default=DEFAULT_FILE)
    args = parser.parse_args()

    jobs = json.loads(args.file.read_text(encoding="utf-8"))

    success = 0
    failure = 0
    for job in jobs:
        label = f"{job.get('companyName')} - {job.get('title')}"
        try:
            status, body = post_job(args.url, job)
        except urllib.error.URLError as error:
            print(f"[FAIL] {label}: 연결 실패 ({error.reason})")
            failure += 1
            continue

        if status == 201:
            print(f"[OK]   {label}")
            success += 1
        else:
            print(f"[FAIL] {label}: HTTP {status} {body}")
            failure += 1

    print(f"\n총 {len(jobs)}건 중 성공 {success}건, 실패 {failure}건")
    return 0 if failure == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
