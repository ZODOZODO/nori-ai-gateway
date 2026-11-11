"""읽기 전용 clone의 원시 Git 오류는 메모리에서 분류하고 기록하지 않습니다."""
import os
import re
import subprocess
import sys


def execute(arguments, failure):
    # 상속된 trace 파일 경로도 자격을 기록할 수 있어 자식 환경에서 제거합니다.
    environment = {key: value for key, value in os.environ.items()
                   if not key.startswith('GIT_TRACE') and key != 'GIT_CURL_VERBOSE'}
    result = subprocess.run(['git', *arguments], capture_output=True, env=environment)
    if result.returncode:
        if re.search(rb'401|403|[Ff]orbidden|Authentication failed', result.stderr + result.stdout):
            sys.exit('EXEC_READ_FORBIDDEN')
        sys.exit(failure)
    return result.stdout.decode('ascii', errors='replace').strip()


directory, sha = sys.argv[1:]
execute(['clone', '--filter=blob:none', '--no-checkout',
         'https://gitlab.noricloud.org/norisystem/nori-tc/nori-tc-exec.git', directory], 'EXEC_READ_FAILED')
execute(['-C', directory, 'checkout', '--detach', sha], 'EXEC_CHECKOUT_FAILED')
actual = execute(['-C', directory, 'rev-parse', 'HEAD'], 'EXEC_CHECKOUT_FAILED')
if actual != sha:
    sys.exit('EXEC_SHA_MISMATCH')
