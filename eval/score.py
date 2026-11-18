#!/usr/bin/env python3
"""JobFit 요건 추출 채점 + 오류 원인 분류.

사용법 (레포 루트에서):
    python eval/score.py eval/predictions/v0.json
    python eval/score.py eval/predictions/v0.json --stage raw     # 후처리 전 LLM 원본 채점
    python eval/score.py eval/predictions/v1.json --compare eval/predictions/v0.json

결과:
    eval/results/<이름>.md          요약 리포트
    eval/results/<이름>_errors.csv  오류 한 줄씩 (원인 분류 포함)
"""
import argparse
import csv
import json
import re
from collections import Counter, defaultdict
from pathlib import Path

HERE = Path(__file__).resolve().parent

# ---------------------------------------------------------------- 사전/정규화

def load_vocab(path):
    v = json.loads(Path(path).read_text(encoding="utf-8"))
    aliases = {k.lower(): val for k, val in v["aliases"].items()}
    concepts = {c.lower() for c in v["concepts"]}
    certs = [re.compile(p, re.I) for p in v["cert_patterns"]]
    return aliases, concepts, certs


def strict_key(name):
    """대소문자·공백만 맞춘 키 (별칭 적용 전)."""
    return re.sub(r"\s+", " ", name.strip()).lower()


class Normalizer:
    def __init__(self, aliases, gt_names):
        self.aliases = aliases
        # 정답에 나온 표기를 정규형 표시 이름으로 사용
        self.display = {strict_key(n): n for n in gt_names}
        for target in aliases.values():
            self.display.setdefault(strict_key(target), target)

    def key(self, name):
        k = strict_key(name)
        if k in self.aliases:
            k = strict_key(self.aliases[k])
        return k

    def show(self, key):
        return self.display.get(key, key)

    def surface_forms(self, key):
        """본문 검색용: 정규형 + 그 정규형으로 매핑되는 별칭들."""
        forms = {key}
        forms |= {a for a, t in self.aliases.items() if strict_key(t) == key}
        return forms

# ---------------------------------------------------------------- 공고 본문 분석

REQ_HDR = re.compile(r"자격|필수|필요기술|필요지식|지원자격")
PREF_HDR = re.compile(r"우대|가점")
OTHER_HDR = re.compile(r"업무|기술|스택|환경|주요|직무|담당")


def section_kind(header):
    # "자격 및 우대사항"처럼 섞인 헤더는 mixed
    r, p = bool(REQ_HDR.search(header)), bool(PREF_HDR.search(header))
    if r and p:
        return "mixed"
    if p:
        return "preferred"
    if r:
        return "required"
    if OTHER_HDR.search(header):
        return "duty_stack"
    return "other"


def mention_pattern(form):
    # 앞뒤가 영숫자가 아닐 때만 매칭 (SQL이 MySQL 안에서 잡히지 않도록)
    return re.compile(r"(?<![A-Za-z0-9])" + re.escape(form) + r"(?![A-Za-z0-9+#])", re.I)


def find_sections(text, forms):
    """기술이 언급된 섹션 종류들. 헤더 [..] 기준으로 줄 단위 판정."""
    kinds = []
    current = "other"
    pats = [mention_pattern(f) for f in forms if f]
    for line in text.splitlines():
        m = re.match(r"\s*\[([^\]]+)\]", line)
        if m:
            current = section_kind(m.group(1))
        if any(p.search(line) for p in pats):
            kinds.append(current)
    return kinds

# ---------------------------------------------------------------- 입력 로딩

def load_predictions(path, stage):
    data = json.loads(Path(path).read_text(encoding="utf-8"))
    meta = {}
    if isinstance(data, dict):
        meta = {k: v for k, v in data.items() if k != "items"}
        data = data["items"]
    preds = {}
    for item in data:
        pid = item.get("jobPostingId", item.get("job_posting_id"))
        src = item.get("raw") if stage == "raw" else item
        if src is None:
            raise SystemExit(f"--stage raw인데 id {pid}에 raw 필드가 없습니다")
        preds[int(pid)] = {
            "required": src.get("required") or src.get("requiredSkills") or [],
            "preferred": src.get("preferred") or src.get("preferredSkills") or [],
            "error": item.get("error"),
        }
    return preds, meta


def prf(tp, fp, fn):
    p = tp / (tp + fp) if tp + fp else 0.0
    r = tp / (tp + fn) if tp + fn else 0.0
    f = 2 * p * r / (p + r) if p + r else 0.0
    return p, r, f

# ---------------------------------------------------------------- 채점

def score(gt, preds, postings, norm, concepts, certs):
    rows, errors = [], []
    for g in gt:
        pid = g["jobPostingId"]
        post = postings.get(g["externalId"], {})
        text = post.get("description", "") or ""
        keywords = post.get("keywords", "") or ""
        p = preds.get(pid)
        missing = p is None or p.get("error")

        G_req = {norm.key(s) for s in g["required"]}
        G_pref = {norm.key(s) for s in g["preferred"]} - G_req
        P_req = {norm.key(s) for s in (p["required"] if p else [])}
        P_pref = ({norm.key(s) for s in p["preferred"]} if p else set()) - P_req
        G, P = G_req | G_pref, P_req | P_pref

        # 별칭 없이 대소문자만 맞췄을 때의 일치 (정규화 덕에 맞은 것 찾기)
        P_raw = {strict_key(s) for s in (p["required"] + p["preferred"] if p else [])}
        G_raw = {strict_key(s) for s in g["required"] + g["preferred"]}

        row = {
            "id": pid, "company": g["company"], "ambiguous": g["ambiguous"], "missing": bool(missing),
            "m_tp": len(G & P), "m_fp": len(P - G), "m_fn": len(G - P),
            "r_tp": len(G_req & P_req), "r_fp": len(P_req - G_req), "r_fn": len(G_req - P_req),
            "p_tp": len(G_pref & P_pref), "p_fp": len(P_pref - G_pref), "p_fn": len(G_pref - P_pref),
            "strict_tp": len(G_raw & P_raw),
            "swap": len(G_req & P_pref) + len(G_pref & P_req),
        }
        rows.append(row)

        orig_name = {norm.key(x): x for x in (p["required"] + p["preferred"] if p else [])}

        def add(kind, skill_key, cause, detail=""):
            name = norm.show(skill_key) if skill_key in norm.display else orig_name.get(skill_key, skill_key)
            errors.append({
                "id": pid, "company": g["company"], "ambiguous": g["ambiguous"],
                "type": kind, "skill": name, "cause": cause, "detail": detail,
            })

        if missing:
            for s in sorted(G):
                add("FN", s, "MISSING_PREDICTION", (p or {}).get("error") or "예측 없음")
            continue

        # 오탐 (기술 자체가 정답에 없음)
        for s in sorted(P - G):
            forms = norm.surface_forms(s)
            kinds = find_sections(text, forms)
            if s in concepts:
                add("FP", s, "CONCEPT_WORD")
            elif any(c.search(s) for c in certs):
                add("FP", s, "CERTIFICATE")
            elif not kinds:
                in_kw = any(mention_pattern(f).search(keywords) for f in forms)
                add("FP", s, "KEYWORDS_LEAK" if in_kw else "NOT_IN_TEXT",
                    "keywords 필드에만 있음" if in_kw else "본문·keywords 어디에도 없음")
            else:
                add("FP", s, "RULE_EXCLUDED", "본문에는 있으나 정답 규칙상 제외 (섹션: " + ",".join(sorted(set(kinds))) + ")")

        # 누락 (정답 기술을 아예 못 뽑음)
        for s in sorted(G - P):
            kinds = find_sections(text, norm.surface_forms(s))
            if not kinds:
                cause = "MISSED_DERIVED"   # RDBMS→SQL처럼 규칙으로 만든 정답
            elif "required" in kinds or "mixed" in kinds:
                cause = "MISSED_REQ_SECTION"
            elif "preferred" in kinds:
                cause = "MISSED_PREF_SECTION"
            else:
                cause = "MISSED_DUTY_STACK"
            add("FN", s, cause, ",".join(sorted(set(kinds))))

        # 기술은 맞췄는데 필수/우대를 반대로 넣음
        for s in sorted(G_req & P_pref):
            add("SWAP", s, "REQ_AS_PREF", ",".join(sorted(set(find_sections(text, norm.surface_forms(s))))))
        for s in sorted(G_pref & P_req):
            add("SWAP", s, "PREF_AS_REQ", ",".join(sorted(set(find_sections(text, norm.surface_forms(s))))))

        # 표기만 달라서 정규화로 맞은 것 (오류는 아니지만 기록)
        for s in sorted(G & P):
            if s not in P_raw:
                orig = [x for x in p["required"] + p["preferred"] if norm.key(x) == s]
                add("NORM", s, "ALIAS_MATCH", f"예측 표기: {', '.join(orig)}")
    return rows, errors


def summarize(rows, only_clear=False):
    rs = [r for r in rows if not (only_clear and r["ambiguous"])]
    out = {"n": len(rs)}
    for k in ("m", "r", "p"):
        tp = sum(r[f"{k}_tp"] for r in rs)
        fp = sum(r[f"{k}_fp"] for r in rs)
        fn = sum(r[f"{k}_fn"] for r in rs)
        out[k] = (tp, fp, fn, *prf(tp, fp, fn))
    m_tp = out["m"][0]
    out["level_acc"] = (m_tp - sum(r["swap"] for r in rs)) / m_tp if m_tp else 0.0
    st = sum(r["strict_tp"] for r in rs)
    out["strict"] = (st, *prf(st, out["m"][1] + (m_tp - st), out["m"][2] + (m_tp - st)))
    return out

# ---------------------------------------------------------------- 리포트

def fmt(x):
    return f"{x:.3f}"


def render(name, meta, rows, errors, compare=None):
    L = [f"# 추출 평가 결과: {name}", ""]
    if meta:
        L += ["| 항목 | 값 |", "|---|---|"] + [f"| {k} | {v} |" for k, v in meta.items()] + [""]
    missing = [r["id"] for r in rows if r["missing"]]
    if missing:
        L += [f"> 예측이 없거나 실패한 공고 {len(missing)}개: {missing} — 전부 누락으로 계산됨", ""]

    L += ["## 요약", "",
          "| 범위 | 공고 수 | 구분 | Precision | Recall | F1 | TP | FP | FN |",
          "|---|---|---|---|---|---|---|---|---|"]
    for label, s in (("전체", summarize(rows)), ("ambiguous 제외", summarize(rows, True))):
        for k, kname in (("m", "필수+우대 통합"), ("r", "필수"), ("p", "우대")):
            tp, fp, fn, P, R, F = s[k]
            L.append(f"| {label} | {s['n']} | {kname} | {fmt(P)} | {fmt(R)} | {fmt(F)} | {tp} | {fp} | {fn} |")
    s = summarize(rows)
    L += ["",
          f"- 필수/우대 구분 정확도 (맞춘 기술 중 레벨까지 맞은 비율): **{fmt(s['level_acc'])}**",
          f"- 별칭 정규화 없이 채점한 통합 F1: {fmt(s['strict'][3])} (정규화 후 {fmt(s['m'][5])})",
          ""]

    if compare:
        c = summarize(compare)
        L += ["## 이전 버전과 비교 (전체 기준 F1)", "", "| 구분 | 이전 | 현재 | 변화 |", "|---|---|---|---|"]
        for k, kname in (("m", "통합"), ("r", "필수"), ("p", "우대")):
            L.append(f"| {kname} | {fmt(c[k][5])} | {fmt(s[k][5])} | {s[k][5]-c[k][5]:+.3f} |")
        L.append("")

    L += ["## 오류 원인 분류", "", "| 유형 | 원인 | 건수 | 뜻 |", "|---|---|---|---|"]
    desc = {
        ("FP", "CONCEPT_WORD"): "개념어를 기술로 뽑음",
        ("FP", "CERTIFICATE"): "자격증을 기술로 뽑음",
        ("FP", "KEYWORDS_LEAK"): "본문엔 없고 keywords 필드에만 있는 기술",
        ("FP", "NOT_IN_TEXT"): "본문 어디에도 없는 기술 (환각 또는 별칭 누락)",
        ("FP", "RULE_EXCLUDED"): "본문엔 있지만 라벨링 규칙상 제외한 기술",
        ("FN", "MISSED_REQ_SECTION"): "자격요건에 있는 기술을 못 뽑음",
        ("FN", "MISSED_PREF_SECTION"): "우대사항에 있는 기술을 못 뽑음",
        ("FN", "MISSED_DUTY_STACK"): "주요업무/기술스택에만 있는 기술을 못 뽑음",
        ("FN", "MISSED_DERIVED"): "RDBMS→SQL처럼 규칙으로 만든 정답을 못 맞춤",
        ("FN", "MISSING_PREDICTION"): "추출 실패나 예측 누락",
        ("SWAP", "REQ_AS_PREF"): "필수를 우대로 넣음",
        ("SWAP", "PREF_AS_REQ"): "우대를 필수로 넣음",
        ("NORM", "ALIAS_MATCH"): "표기가 달라 별칭으로 맞춤 (오류 아님)",
    }
    cnt = Counter((e["type"], e["cause"]) for e in errors)
    for (t, c), n in sorted(cnt.items(), key=lambda x: (["FP", "FN", "SWAP", "NORM"].index(x[0][0]), -x[1])):
        L.append(f"| {t} | {c} | {n} | {desc.get((t, c), '')} |")
    L.append("")

    L += ["## 공고별 결과 (통합 F1 낮은 순)", "",
          "| id | 회사 | amb | P | R | F1 | FP | FN | SWAP |", "|---|---|---|---|---|---|---|---|---|"]
    for r in sorted(rows, key=lambda r: prf(r["m_tp"], r["m_fp"], r["m_fn"])[2]):
        P, R, F = prf(r["m_tp"], r["m_fp"], r["m_fn"])
        if r["m_tp"] + r["m_fp"] + r["m_fn"] == 0:
            P = R = F = 1.0  # 정답도 예측도 비어 있으면 완벽
        L.append(f"| {r['id']} | {r['company']} | {'Y' if r['ambiguous'] else ''} | {fmt(P)} | {fmt(R)} | {fmt(F)} | {r['m_fp']} | {r['m_fn']} | {r['swap']} |")
    L.append("")

    L += ["## 오류 목록", "", "| id | 회사 | 유형 | 원인 | 기술 | 상세 |", "|---|---|---|---|---|---|"]
    for e in sorted(errors, key=lambda e: (e["id"], e["type"])):
        if e["type"] != "NORM":
            L.append(f"| {e['id']} | {e['company']} | {e['type']} | {e['cause']} | {e['skill']} | {e['detail']} |")
    return "\n".join(L) + "\n"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("predictions")
    ap.add_argument("--gt", default=HERE / "ground_truth.json")
    ap.add_argument("--postings", default=HERE / "postings.json")
    ap.add_argument("--vocab", default=HERE / "vocab.json")
    ap.add_argument("--stage", choices=["final", "raw"], default="final")
    ap.add_argument("--compare", help="이전 버전 predictions 파일")
    ap.add_argument("--out-dir", default=HERE / "results")
    a = ap.parse_args()

    gt = json.loads(Path(a.gt).read_text(encoding="utf-8"))
    postings = {p["externalId"]: p for p in json.loads(Path(a.postings).read_text(encoding="utf-8"))}
    aliases, concepts, certs = load_vocab(a.vocab)
    norm = Normalizer(aliases, [s for g in gt for s in g["required"] + g["preferred"]])

    preds, meta = load_predictions(a.predictions, a.stage)
    extra = sorted(set(preds) - {g["jobPostingId"] for g in gt})
    if extra:
        print(f"[참고] 정답에 없는 id는 무시: {extra}")
    rows, errors = score(gt, preds, postings, norm, concepts, certs)

    cmp_rows = None
    if a.compare:
        cp, _ = load_predictions(a.compare, a.stage)
        cmp_rows, _ = score(gt, cp, postings, norm, concepts, certs)

    name = Path(a.predictions).stem + ("_raw" if a.stage == "raw" else "")
    out = Path(a.out_dir)
    out.mkdir(parents=True, exist_ok=True)
    (out / f"{name}.md").write_text(render(name, meta, rows, errors, cmp_rows), encoding="utf-8")
    with open(out / f"{name}_errors.csv", "w", newline="", encoding="utf-8-sig") as f:
        w = csv.DictWriter(f, fieldnames=["id", "company", "ambiguous", "type", "skill", "cause", "detail"])
        w.writeheader()
        w.writerows(sorted(errors, key=lambda e: (e["id"], e["type"])))

    s = summarize(rows)
    print(f"[{name}] 공고 {s['n']}개")
    for k, kname in (("m", "통합"), ("r", "필수"), ("p", "우대")):
        tp, fp, fn, P, R, F = s[k]
        print(f"  {kname:4s} P={fmt(P)} R={fmt(R)} F1={fmt(F)}  (TP {tp} / FP {fp} / FN {fn})")
    print(f"  레벨 정확도 {fmt(s['level_acc'])}")
    print("  오류:", dict(Counter(f"{e['type']}:{e['cause']}" for e in errors if e["type"] != "NORM")))
    print(f"  → {out / (name + '.md')}")


if __name__ == "__main__":
    main()
