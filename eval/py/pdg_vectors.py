"""`pdg` 고정 벡터를 Python hashlib 으로 독립 계산해 기대값과 비교한다(P08 교차검증용).

정본(SPEC §9.1): permCode 중복 제거 → 코드포인트 정렬 → catalogRevision 과 함께 "\\n" 연결 → UTF-8 → SHA-256 소문자 16진.
입력 위반(빈 revision, revision 의 CR·LF, 빈 permCode·공백 포함 permCode)은 생성 거부한다.
기대값은 벡터 파일의 고정값이며 이 도구는 그 값을 덮어쓰지 않는다. 불일치·음성 미거부 = exit 1, 인자·입력 오류 = exit 2.
"""
import argparse
import hashlib
import json
import re
import sys


class Rejected(Exception):
    pass


class Parser(argparse.ArgumentParser):
    def error(self, message):
        print("ARGUMENT_MISSING", file=sys.stderr)
        sys.exit(2)


def compute(catalog_revision, perms):
    if not isinstance(catalog_revision, str) or not catalog_revision or re.search(r"[\r\n]", catalog_revision):
        raise Rejected("REVISION_INVALID")
    if not isinstance(perms, list):
        raise Rejected("PERMS_INVALID")
    for perm in perms:
        if not isinstance(perm, str) or not perm or re.search(r"\s", perm):
            raise Rejected("PERM_INVALID")
    serialized = catalog_revision + "\n" + "\n".join(sorted(set(perms)))
    try:
        return hashlib.sha256(serialized.encode("utf-8")).hexdigest()
    except UnicodeEncodeError:
        raise Rejected("UTF8_INVALID") from None


def main(argv=None):
    parser = Parser(add_help=False)
    parser.add_argument("--vectors", required=True)
    parser.add_argument("--check", action="store_true", required=True)
    args = parser.parse_args(sys.argv[1:] if argv is None else argv)
    try:
        with open(args.vectors, "rb") as handle:
            data = json.loads(handle.read().decode("utf-8"))
        vectors, negatives = data["vectors"], data.get("negatives", [])
        if not isinstance(vectors, list) or not vectors:
            raise ValueError
    except (OSError, ValueError, KeyError, TypeError):
        print("VECTORS_INVALID", file=sys.stderr)
        return 2
    failed = 0
    for vector in vectors:
        expected = vector.get("expectedPdg")
        try:
            actual = compute(vector.get("catalogRevision"), vector.get("perms"))
            ok = isinstance(expected, str) and re.fullmatch(r"[0-9a-f]{64}", expected) is not None and actual == expected
        except Rejected:
            ok = False
        failed += 0 if ok else 1
        print(str(vector.get("id")) + " " + ("PASS" if ok else "FAIL"))
    for negative in negatives:
        try:
            compute(negative.get("catalogRevision"), negative.get("perms"))
            ok = False
        except Rejected:
            ok = True
        failed += 0 if ok else 1
        print(str(negative.get("id")) + " " + ("REJECTED" if ok else "NOT_REJECTED"))
    return 0 if failed == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
