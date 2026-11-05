#!/usr/bin/env bash
set -euo pipefail
set +x
export PYTHONDONTWRITEBYTECODE=1
[[ -f ops/deploy/runconfig.py ]] || { printf 'AI_ROOT_REQUIRED\n' >&2; exit 2; }
python3 -B - "$@" <<'PY'
import argparse,collections,datetime,json,os,pathlib,subprocess,sys
sys.path.insert(0,str(pathlib.Path('ops/deploy').resolve()))
from runconfig import InputError,item,load,approval
import yaml
parser=argparse.ArgumentParser()
parser.add_argument('--runconfig',required=True); parser.add_argument('--bucket',required=True)
parser.add_argument('--out',required=True); parser.add_argument('--keys-from')
args=parser.parse_args()
try:
    config=load(args.runconfig)
    if config.get('purpose')!='deploy': raise InputError('RUNCONFIG_NOT_APPROVED:purpose')
    endpoint=item(config,'seaweed.s3Endpoint',True)
    approved_file=pathlib.Path(item(config,'corpusInventory.keysFile',True)).resolve()
    # YAML 모드도 승인 문서가 지목한 바로 그 파일만 읽습니다.
    if args.keys_from and pathlib.Path(args.keys_from).resolve()!=approved_file:
        raise InputError('CORPUS_SCOPE_MISMATCH')
    if args.bucket!='nori-tc': raise InputError('CORPUS_BUCKET_INVALID')
    from urllib.parse import urlsplit
    uri=urlsplit(endpoint)
    if uri.scheme not in ('http','https') or not uri.hostname or uri.username or uri.password or uri.query or uri.fragment:
        raise InputError('RUNCONFIG_INVALID:seaweed.s3Endpoint')
    if args.keys_from:
        source=yaml.safe_load(approved_file.read_text(encoding='utf-8'))
        docs=source.get('documents') if isinstance(source,dict) else None
        if not isinstance(source,dict) or source.get('ownerSystem')!='TC' or not isinstance(docs,list) or any(not isinstance(d,dict) or d.get('owner')!=source['ownerSystem'] or not isinstance(d.get('sourceRef'),str) for d in docs):
            raise InputError('CORPUS_KEYS_INVALID')
        keys=[]
        for document in docs:
            reference=document['sourceRef']
            if reference.startswith('s3://'):
                parsed=urlsplit(reference)
                if parsed.netloc!=args.bucket or parsed.query or parsed.fragment:
                    raise InputError('CORPUS_SCOPE_MISMATCH')
                keys.append(parsed.path.lstrip('/'))
            elif '://' in reference:
                raise InputError('CORPUS_SOURCE_REF_UNSUPPORTED')
            else:
                keys.append(reference)
    else:
        keys=approved_file.read_text(encoding='utf-8').splitlines()
    if not keys or len(keys)!=len(set(keys)) or any(not k or k.startswith('/') or '..' in k.split('/') or any(ord(c)<32 for c in k) for k in keys):
        raise InputError('CORPUS_KEYS_INVALID')
    # 외부 자격의 생성·저장 없이 기존 읽기 전용 환경만 전달합니다.
    if not os.environ.get('AWS_ACCESS_KEY_ID') or not os.environ.get('AWS_SECRET_ACCESS_KEY'):
        raise InputError('CORPUS_CREDENTIAL_MISSING')
    count=total=maximum=0; extensions=collections.Counter(); mimes=collections.Counter()
    for key in keys:
        process=subprocess.run(['aws','s3api','head-object','--endpoint-url',endpoint,'--bucket',args.bucket,'--key',key,'--query','{len:ContentLength,type:ContentType}','--output','json'],capture_output=True,text=True)
        if process.returncode: raise InputError('CORPUS_HEAD_FAILED')
        try: metadata=json.loads(process.stdout); size=metadata['len']; mime=metadata['type'] or 'unknown'
        except (ValueError,KeyError,TypeError): raise InputError('CORPUS_METADATA_INVALID')
        if type(size) is not int or size<0 or not isinstance(mime,str): raise InputError('CORPUS_METADATA_INVALID')
        # key의 일부가 출력에 새지 않도록 확장자·MIME도 형식을 제한합니다.
        import re
        extension=pathlib.PurePosixPath(key).suffix.lower()
        extension=extension if re.fullmatch(r'\.[a-z0-9]{1,10}',extension) else 'other'
        mime=mime if re.fullmatch(r'[a-zA-Z0-9.+-]+/[a-zA-Z0-9.+-]+',mime) else 'unknown'
        count+=1; total+=size; maximum=max(maximum,size); extensions[extension]+=1; mimes[mime]+=1
    result=dict(status='MEASURED',recordedAt=datetime.datetime.now(datetime.timezone.utc).isoformat(),count=count,totalBytes=total,maxBytes=maximum,extensions=dict(extensions),mimeTypes=dict(mimes))
    out=pathlib.Path(args.out); out.parent.mkdir(parents=True,exist_ok=True)
    out.write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(result))
except (InputError,OSError) as error:
    print(str(error) if isinstance(error,InputError) else 'CORPUS_INPUT_UNAVAILABLE',file=sys.stderr)
    sys.exit(2)
PY
