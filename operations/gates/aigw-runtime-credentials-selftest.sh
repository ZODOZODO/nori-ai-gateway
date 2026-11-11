#!/usr/bin/env bash
# aigw-runtime-credentials.sh 셀프시험 + aigw-helm.sh deploy 경계(차트 checkout 검사·자격 check·증거 필드). stub kubectl·stub Helm upgrade/status,
# 실제 Helm package/lint/template·실제 git. 실제 Secret·클러스터 접근 0.
set -euo pipefail
set +x
export PYTHONDONTWRITEBYTECODE=1
python3 -B - <<'PY'
import base64,json,os,pathlib,secrets,shutil,subprocess,tempfile
root=pathlib.Path.cwd()
with tempfile.TemporaryDirectory(prefix='aigw-credentials-test-') as temporary:
    work=pathlib.Path(temporary); shutil.copytree(root/'operations',work/'operations')
    binary=work/'bin'; binary.mkdir()
    gate=work/'docs/gates/f1'; gate.mkdir(parents=True)
    (gate/'synthetic.md').write_text('Status: APPROVED\n')
    config=work/'config.json'
    def backup_config(kind='s3'):
        config.write_text(json.dumps(dict(purpose='deploy',profile='main',backup=dict(type=dict(value=kind,approvalRef='docs/gates/f1/synthetic.md')))))
    backup_config()
    real_helm=shutil.which('helm')
    sentinel=base64.b64encode(secrets.token_bytes(32)).decode()
    env=dict(os.environ,PATH=str(binary)+os.pathsep+os.environ['PATH'],SENTINEL=sentinel,TEST_LOG=str(work/'calls.jsonl'),SECRET_TEST='present',PYTHONDONTWRITEBYTECODE='1',AIGW_RUNCONFIG=str(config),TMPDIR=str(work),BACKUP_FIELDS='s3',REAL_HELM=real_helm)
    def stub(name,body):
        file=binary/name; file.write_text('#!/usr/bin/env python3\n'+body); file.chmod(0o700)
    stub('kubectl',r'''
import json,os,sys
args=sys.argv[1:]
with open(os.environ['TEST_LOG'],'a') as log: log.write(json.dumps(dict(tool='kubectl',args=args))+'\n')
if 'rollout' in args:
    if os.environ.get('ROLLOUT_TEST')=='fail': sys.exit(1)
elif 'wait' in args:
    if os.environ.get('JOB_TEST')=='wait-fail': sys.exit(1)
elif 'get' in args and 'secret' in args:
    assert '-o' not in args or args[args.index('-o')+1].startswith('go-template='), args   # 키 이름만(값 조회 0)
    if os.environ['SECRET_TEST']=='error': sys.exit(1)
    if os.environ['SECRET_TEST']=='absent': sys.exit(0)
    if args[4]=='ai-gateway-backup-destination': print('access-key\nsecret-key' if os.environ['BACKUP_FIELDS']=='s3' else 'ssh-private-key\nknown-hosts')
    elif args[4]=='ai-gateway-config':
        keys=['ai-gateway-db.properties','AI_GATEWAY_DB_USER','AI_GATEWAY_DB_PASSWORD','token','access-key','secret-key','service-tokens.json','jwks.json']
        drop=os.environ.get('CONFIG_DROP')
        print('\n'.join(k for k in keys if k!=drop))
else: sys.exit(1)
''')
    checks=[]
    def run(arguments,code,error=None,extra=None):
        result=subprocess.run(['bash',*arguments],cwd=work,env=env|dict(extra or {}),capture_output=True,text=True)
        assert result.returncode==code, (arguments,result.returncode,result.stdout,result.stderr)
        assert sentinel not in result.stdout+result.stderr
        if error: assert error in result.stderr,(error,result.stderr)
        checks.append(dict(command=arguments,exit=code,expectedError=error))
        return result
    records=lambda:[json.loads(line) for line in (work/'calls.jsonl').read_text().splitlines()] if (work/'calls.jsonl').exists() else []
    script='operations/gates/aigw-runtime-credentials.sh'
    # DB 역할 Secret provision·회전 명령은 없다(Plan U6 ③).
    for removed in ('provision-db','rotate-db'):
        run([script,removed],2,'COMMAND_INVALID'); assert records()==[]
    config_keys=['ai-gateway-db.properties','AI_GATEWAY_DB_USER','AI_GATEWAY_DB_PASSWORD','token']
    for stage in ['F1','F2','F3','F4','RESTORE']:
        run([script,'check','--stage',stage],0)
        evidence=sorted((work/'evidence'/stage/'gates').glob('*.json'))[-1]
        items={r['name']:r['fields'] for r in json.loads(evidence.read_text())['items']}
        # 추론 자격은 어느 단계에도 없다(SPEC §26.1). SeaweedFS 읽기 키는 F3·F4·RESTORE, 백업 목적지는 RESTORE 밖.
        assert items['ai-gateway-config']==config_keys+(['access-key','secret-key'] if stage in ['F3','F4','RESTORE'] else []),(stage,items)
        assert set(items)=={'ai-gateway-config'}|({'ai-gateway-backup-destination'} if stage!='RESTORE' else set()),(stage,items)
        run([script,'check','--stage',stage],13,extra={'SECRET_TEST':'absent'})
        run([script,'check','--stage',stage],13,extra={'CONFIG_DROP':'AI_GATEWAY_DB_PASSWORD'})
        run([script,'check','--stage',stage],13,extra={'SECRET_TEST':'error'})
    run([script,'check','--stage','F3'],13,extra={'CONFIG_DROP':'secret-key'})
    run([script,'check','--stage','F1'],0,extra={'CONFIG_DROP':'secret-key'})
    for kind,fields in [('s3',['access-key','secret-key']),('sftp',['ssh-private-key','known-hosts'])]:
        backup_config(kind)
        for stage in ['F1','F2','F3','F4']:
            run([script,'check','--stage',stage],0,extra={'BACKUP_FIELDS':kind})
            evidence=max((work/'evidence'/stage/'gates').glob('*.json'),key=lambda p:p.stat().st_mtime_ns)
            item=next(r for r in json.loads(evidence.read_text())['items'] if r['name']=='ai-gateway-backup-destination')
            assert item['fields']==fields and item['status']=='PRESENT'
            run([script,'check','--stage',stage],13,extra={'BACKUP_FIELDS':'sftp' if kind=='s3' else 's3'})
    run([script,'check','--stage','F1'],2,'RUNCONFIG_INVALID',{'AIGW_RUNCONFIG':str(work/'absent.json')})
    run([script,'check','--stage','F1'],2,'RUNCONFIG_MISSING:backup.type',{'AIGW_RUNCONFIG':''})
    backup_config('invalid'); run([script,'check','--stage','F1'],2,'RUNCONFIG_INVALID:backup.type')
    config.write_text('{'); run([script,'check','--stage','F1'],2,'RUNCONFIG_INVALID')
    config.write_text('{}'); run([script,'check','--stage','F1'],2,'RUNCONFIG_NOT_APPROVED:purpose')
    config.write_text('{"purpose":"deploy","profile":"main"}')
    run([script,'check','--stage','F1'],2,'RUNCONFIG_MISSING:backup.type')
    backup_config(); config.write_text(config.read_text().replace('main','restore'))
    run([script,'check','--stage','F1'],2,'RUNCONFIG_PROFILE_MISMATCH')
    run([script,'check','--stage','RESTORE'],0,extra={'AIGW_RUNCONFIG':''})
    backup_config(); config.write_text(config.read_text().replace('deploy','lint'))
    run([script,'check','--stage','F1'],2,'RUNCONFIG_NOT_APPROVED:purpose')
    backup_config(); (gate/'synthetic.md').write_text('Status: BLOCKED\n')
    run([script,'check','--stage','F1'],2,'RUNCONFIG_NOT_APPROVED:backup.type')
    (gate/'synthetic.md').write_text('Status: APPROVED\n')
    backup_config('s3')

    # ── aigw-helm.sh deploy: 차트 checkout(operations/ 미커밋 0·HEAD = 이미지 gitSha) → 런타임 자격 check → helm upgrade(stub) → 증거(chartRepoSha) ──
    stub('helm',r'''
import json,os,subprocess,sys
args=sys.argv[1:]
with open(os.environ['TEST_LOG'],'a') as log: log.write(json.dumps(dict(tool='helm',args=args[:2]))+'\n')
if args[0]=='upgrade': sys.exit(int(os.environ.get('UPGRADE_RC','0')))
if args[0]=='status': print(json.dumps(dict(info=dict(status='deployed'),version=1))); sys.exit(0)
sys.exit(subprocess.call([os.environ['REAL_HELM'],*args]))
''')
    deploy_data=json.loads((work/'operations/runconfig/fixtures/lint-verify.json').read_text())
    deploy_data['purpose']='deploy'
    deploy_data['backup']['enabled']=dict(value=True,approvalRef='x')
    def approve(node):
        for value in node.values():
            if isinstance(value,dict):
                if 'value' in value: value['approvalRef']='docs/gates/f1/synthetic.md'
                else: approve(value)
    approve(deploy_data)
    (work/'deploy-input.json').write_text(json.dumps(deploy_data))
    restore_data=dict(purpose='deploy',profile='restore',pgImage=deploy_data['pgImage'],restore=deploy_data['restore'])
    (work/'restore-input.json').write_text(json.dumps(restore_data))
    for name in ['postgres-image','corpus-inventory','backup-destination','netpol-targets']:
        (gate/(name+'.md')).write_text('Status: APPROVED\n')
    git=lambda *a: subprocess.run(['git','-c','user.name=selftest','-c','user.email=selftest@invalid',*a],cwd=work,check=True,capture_output=True,text=True).stdout.strip()
    db=['operations/deploy/aigw-helm.sh','--release','ai-gateway-db-restore','--mode','deploy','--runconfig','restore-input.json']
    # git checkout 이 아니면 부작용 전 exit 2.
    (work/'calls.jsonl').unlink()
    run(db,2,'CHART_CHECKOUT_UNREADABLE'); assert not any(r['tool'] in ('kubectl','helm') for r in records())
    git('init','-q'); git('add','operations'); git('commit','-q','-m','selftest checkout')
    head=git('rev-parse','HEAD')
    digests=json.loads((work/'operations/runconfig/fixtures/lint-image-digests.json').read_text())
    (work/'digests.json').write_text(json.dumps(dict(digests,buildIdentity=dict(digests['buildIdentity'],gitSha=head))))
    (work/'other-digests.json').write_text(json.dumps(dict(digests,buildIdentity=dict(digests['buildIdentity'],gitSha='f'*40))))
    app=['operations/deploy/aigw-helm.sh','--release','ai-gateway','--mode','deploy','--runconfig','deploy-input.json','--image-digests','digests.json','--suite','none']
    for command,expected in ((db,'ai-gateway-db-restore'),(app,'ai-gateway')):
        # operations/ 의 수정·미추적 파일 → exit 2·클러스터 호출 0.
        target=work/'operations/deploy/helm/ai-gateway/values-verify.yaml'; original=target.read_text()
        target.write_text(original+'# local edit\n'); (work/'calls.jsonl').unlink(missing_ok=True)
        run(command,2,'CHART_CHECKOUT_DIRTY'); assert not any(r['tool']=='kubectl' or r['args'][:1]==['upgrade'] for r in records())
        target.write_text(original)
        extra_file=work/'operations/deploy/helm/ai-gateway-db-restore/templates/extra.yaml'; extra_file.write_text('kind: ConfigMap\n')
        run(command,2,'CHART_CHECKOUT_DIRTY'); extra_file.unlink()
        # 정상: 자격 check → upgrade → rollout → 증거에 chartRepoSha = HEAD·chartDigest, execSha 없음.
        (work/'calls.jsonl').unlink(missing_ok=True)
        run(command,0)
        calls=records()
        assert [r['args'][:2] for r in calls if r['tool']=='helm' and r['args'][0]=='upgrade']==[['upgrade','--install']]
        assert any(r['tool']=='kubectl' and r['args'][2:4]==['rollout','status'] for r in calls)
        evidence=max((work/'evidence/F1/gates').glob(f'deploy-{expected}-*.json'),key=lambda p:p.stat().st_mtime_ns)
        record=json.loads(evidence.read_text())
        assert record['chartRepoSha']==head and len(record['chartDigest'])==64 and 'execSha' not in record and record['helmRevision']==1,record
        # 런타임 자격 미준비 → exit 13·upgrade 0.
        (work/'calls.jsonl').unlink()
        run(command,13,extra={'CONFIG_DROP':'AI_GATEWAY_DB_USER'}); assert not any(r['tool']=='helm' and r['args'][0]=='upgrade' for r in records())
        # rollout 실패 → FAIL 증거(chartRepoSha 포함)·exit 1.
        run(command,1,'ROLLOUT_FAILED',{'ROLLOUT_TEST':'fail'})
        evidence=max((work/'evidence/F1/gates').glob(f'deploy-{expected}-*.json'),key=lambda p:p.stat().st_mtime_ns)
        assert json.loads(evidence.read_text())['status']=='FAIL' and json.loads(evidence.read_text())['chartRepoSha']==head
    run(db,1,'BOOTSTRAP_FAILED',{'JOB_TEST':'wait-fail'})
    # 앱 release: HEAD ≠ 이미지 gitSha → exit 2·클러스터 호출 0. lint/render 는 checkout 검사 없음(로컬 시험 경로).
    (work/'calls.jsonl').unlink()
    run(app[:-4]+['--image-digests','other-digests.json','--suite','none'],2,'CHART_CHECKOUT_SHA_MISMATCH'); assert not any(r['tool']=='kubectl' or r['args'][:1]==['upgrade'] for r in records())
    (work/'operations/deploy/helm/ai-gateway/values-verify.yaml').write_text((work/'operations/deploy/helm/ai-gateway/values-verify.yaml').read_text()+'# local edit\n')
    run(['operations/deploy/aigw-helm.sh','--release','ai-gateway','--mode','lint','--runconfig','operations/runconfig/fixtures/lint-verify.json','--image-digests','other-digests.json','--suite','none'],0)
    for file in work.rglob('*'):
        if file.is_file(): assert sentinel.encode() not in file.read_bytes()
    print(json.dumps(dict(status='PASS',boundary='stub kubectl/Helm upgrade+status; actual Helm package/lint/template and git; 실제 Secret/클러스터 접근 없음',count=len(checks)),ensure_ascii=False))
PY
