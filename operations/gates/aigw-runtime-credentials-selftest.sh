#!/usr/bin/env bash
set -euo pipefail
set +x
export PYTHONDONTWRITEBYTECODE=1
python3 -B - <<'PY'
import base64,json,os,pathlib,secrets,shutil,subprocess,tempfile
root=pathlib.Path.cwd()
with tempfile.TemporaryDirectory(prefix='aigw-credentials-test-') as temporary:
    work=pathlib.Path(temporary); shutil.copytree(root/'ops',work/'ops')
    binary=work/'bin'; binary.mkdir()
    gate=work/'docs/gates/f1'; gate.mkdir(parents=True)
    (gate/'synthetic.md').write_text('Status: APPROVED\n')
    config=work/'config.json'
    def backup_config(kind='s3'):
        config.write_text(json.dumps(dict(purpose='deploy',profile='main',backup=dict(type=dict(value=kind,approvalRef='docs/gates/f1/synthetic.md')))))
    backup_config()
    exec_dir=pathlib.Path(os.environ.get('AIGW_SELFTEST_EXEC_DIR',str(root.parent/'nori-tc-exec')))
    requested_sha=subprocess.check_output(['git','-C',str(exec_dir),'rev-parse','HEAD'],text=True).strip()
    real_helm=shutil.which('helm')
    sentinel=base64.b64encode(secrets.token_bytes(32)).decode()
    env=dict(os.environ,PATH=str(binary)+os.pathsep+os.environ['PATH'],SENTINEL=sentinel,TEST_LOG=str(work/'calls.jsonl'),GIT_TEST='missing-chart',SECRET_TEST='present',PYTHONDONTWRITEBYTECODE='1',AIGW_RUNCONFIG=str(config),TEST_EXEC_DIR=str(exec_dir),TEST_SHA=requested_sha,TMPDIR=str(work),BACKUP_FIELDS='s3',RELEASE_TEST='both',JOB_TEST='terminal')
    def stub(name,body):
        file=binary/name; file.write_text('#!/usr/bin/env python3\n'+body); file.chmod(0o700)
    stub('openssl','import os; print(os.environ["SENTINEL"])\n')
    stub('kubectl',r'''
import base64,hashlib,json,os,pathlib,sys
args=sys.argv[1:]
with open(os.environ['TEST_LOG'],'a') as log: log.write(json.dumps(dict(tool='kubectl',args=args))+'\n')
if 'exec' in args:
    assert args[:4]==['-n','tc','exec','-i'] and args[4] in ['ai-gateway-db-0','ai-gateway-db-restore-0']
    assert args[5:8]==['--','sh','-c'] and len(args)==9
    assert 'PGPASSWORD="$POSTGRES_PASSWORD"' in args[8]
    assert 'pg_hba_file_rules' in sys.stdin.read()
    if os.environ.get('HBA_TEST')=='sql-error':
        print('SQL ERROR '+os.environ['SENTINEL'],file=sys.stderr); sys.exit(1)
    source=pathlib.Path('ops/postgres/acceptance-pg_hba.conf').read_bytes()
    original='/var/lib/postgresql/18/docker/pg_hba.conf'; overlay='/etc/aigw/postgres/pg_hba.conf'
    rules=[]
    for i,line in enumerate(source.decode().splitlines()[:4]):
        fields=line.split()
        rules.append(dict(rule_number=i+1,file_name=overlay,line_number=i+1,type=fields[0],database=[fields[1]],user_name=[fields[2]],address=None if fields[0]=='local' else fields[3],auth_method=fields[-1],error=None))
    rules.append(dict(rule_number=5,file_name=original,line_number=1,type='local',database=['all'],user_name=['all'],address=None,auth_method='peer',error=None))
    if os.environ.get('HBA_TEST')=='invalid-rule': rules[1]['auth_method']='scram-sha-256'
    if os.environ.get('HBA_TEST')=='missing-include': rules=rules[:4]
    if os.environ.get('HBA_TEST')=='original-nested-include':
        nested=rules[-1].copy(); nested.update(rule_number=6,file_name='/var/lib/postgresql/18/docker/admin-hba.conf')
        rules.append(nested)
    print(json.dumps(dict(dataDirectory='/var/lib/postgresql/18/docker',hbaFile=overlay,overlaySha256=hashlib.sha256(source).hexdigest(),originalHbaSha256='0'*64,rules=rules)))
elif 'rollout' in args and os.environ.get('ROLLOUT_TEST')=='fail': sys.exit(1)
elif 'create' in args and 'secret' in args:
    assert sys.stdin.read().strip()==os.environ['SENTINEL']
    assert len(base64.b64decode(os.environ['SENTINEL']))==32
    if '--dry-run=client' in args: print('{}')
elif 'create' in args or 'apply' in args:
    data=json.load(sys.stdin)
    if 'create' in args:
        assert 'status' not in data and 'selector' not in data['spec']
        assert 'uid' not in data['metadata']
        assert 'controller-uid' not in data['spec']['template']['metadata']['labels']
elif 'wait' in args:
    if os.environ.get('JOB_TEST')=='wait-fail': sys.exit(1)
elif 'get' in args:
    if 'statefulset' in args:
        if os.environ['RELEASE_TEST']=='both' or args[4]==os.environ['RELEASE_TEST']: print('statefulset/'+args[4])
        sys.exit(0)
    if 'job' in args:
        release=args[4].removesuffix('-bootstrap')
        busy=os.environ['JOB_TEST']=='active' and os.environ.get('JOB_BUSY_RELEASE',release)==release
        status={'active':1} if busy else ({'conditions':[{'type':'Complete','status':'True'}]} if os.environ['JOB_TEST']!='nonterminal' else {})
        print(json.dumps(dict(apiVersion='batch/v1',kind='Job',metadata=dict(name=args[4],namespace='tc',uid='old',labels={'aigw.nori/db-instance':release}),spec=dict(selector={},template=dict(metadata=dict(labels={'controller-uid':'old'}),spec={})),status=status)))
        sys.exit(0)
    if 'deployment' in args: print('deployment/'+args[4]); sys.exit(0)
    if os.environ['SECRET_TEST']=='error': sys.exit(1)
    if args[4]=='ai-gateway-db-acceptance-ro' and os.environ.get('ACCEPTANCE_TEST')=='absent': sys.exit(0)
    if os.environ['SECRET_TEST']=='present':
        if args[-1]=='name': print('secret/present')
        elif args[4]=='ai-gateway-backup-destination': print('access-key\nsecret-key' if os.environ['BACKUP_FIELDS']=='s3' else 'ssh-private-key\nknown-hosts')
        else: print('password\ntoken\naccess-key\nsecret-key')
''')
    stub('git',r'''
import json,os,pathlib,shutil,stat,subprocess,sys,time
args=sys.argv[1:]; record=dict(tool='git',args=args)
if args[0]=='clone':
    helper=pathlib.Path(os.environ['GIT_ASKPASS'])
    assert stat.S_IMODE(helper.stat().st_mode)==0o700
    assert os.environ['GIT_TERMINAL_PROMPT']=='0'
    assert os.environ['GIT_CONFIG_VALUE_0']==''
    assert not any(k.startswith('GIT_TRACE') for k in os.environ)
    assert os.environ.get('GIT_CURL_VERBOSE') is None
    assert os.environ['SENTINEL'] not in helper.read_text()
    assert subprocess.check_output([str(helper),'Username']).decode().strip()==os.environ.get('EXPECTED_USER','test-user')
    assert subprocess.check_output([str(helper),'Password']).decode().strip()==os.environ['SENTINEL']
    record['helper']=str(helper)
    if os.environ['GIT_TEST']=='forbidden':
        with open(os.environ['TEST_LOG'],'a') as log: log.write(json.dumps(record)+'\n')
        print('403 Forbidden '+os.environ['SENTINEL'],file=sys.stderr,flush=True)
        time.sleep(0.1)
        for file in pathlib.Path(os.environ['TMPDIR']).rglob('*'):
            if file.is_file(): assert os.environ['SENTINEL'].encode() not in file.read_bytes(), str(file)
        with open(os.environ['TEST_LOG'],'a') as log: log.write(json.dumps(dict(tool='git',args=[],scanVerified=True))+'\n')
        sys.exit(1)
    pathlib.Path(args[-1]).mkdir()
    if os.environ['GIT_TEST']=='chart': shutil.copytree(pathlib.Path(os.environ['TEST_EXEC_DIR'])/'deploy',pathlib.Path(args[-1])/'deploy')
elif 'rev-parse' in args: print('f'*40 if os.environ['GIT_TEST']=='wrong-sha' else os.environ['TEST_SHA'])
with open(os.environ['TEST_LOG'],'a') as log: log.write(json.dumps(record)+'\n')
''')
    checks=[]
    def run(arguments,code,error=None,extra=None):
        result=subprocess.run(['bash',*arguments],cwd=work,env=env|dict(extra or {}),capture_output=True,text=True)
        assert result.returncode==code, (arguments,result.returncode,result.stdout,result.stderr)
        assert sentinel not in result.stdout+result.stderr
        if error: assert error in result.stderr,(error,result.stderr)
        checks.append(dict(command=arguments,exit=code,expectedError=error))
        return result
    script='ops/gates/aigw-runtime-credentials.sh'
    run([script,'provision-db'],0)
    records=lambda:[json.loads(line) for line in (work/'calls.jsonl').read_text().splitlines()]
    assert not any('create' in r['args'] for r in records())
    (work/'calls.jsonl').unlink()
    run([script,'provision-db'],0,extra={'SECRET_TEST':'absent'})
    created=[r for r in records() if 'create' in r['args']]
    assert len(created)==4 and len({r['args'][5] for r in created})==4
    for stage in ['F1','F2','F3','F4','RESTORE']:
        run([script,'check','--stage',stage],0)
        evidence=sorted((work/'evidence'/stage/'gates').glob('*.json'))[-1]
        names={r['name'] for r in json.loads(evidence.read_text())['items']}
        # 추론 자격 Secret 은 어느 단계에도 없다(SPEC §26.1 임차 원칙, Plan §11.9): F2~F4 = F1 + SeaweedFS read(F3~).
        base={'ai-gateway-db-superuser','ai-gateway-db-migrator','ai-gateway-db-app','ai-gateway-db-backup','ai-gateway-gitlab-read'}
        assert names==base|({'ai-gateway-backup-destination'} if stage!='RESTORE' else set())|({'ai-gateway-seaweedfs-read'} if stage in ['F3','F4','RESTORE'] else set()),(stage,names)
        assert ('ai-gateway-seaweedfs-read' in names)==(stage in ['F3','F4','RESTORE'])
        assert ('ai-gateway-backup-destination' in names)==(stage!='RESTORE')
        run([script,'check','--stage',stage],13,extra={'SECRET_TEST':'absent'})
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
    run([script,'provision-db'],1,'SECRET_QUERY_FAILED',{'SECRET_TEST':'error'})
    for state in ['active','nonterminal']:
        (work/'calls.jsonl').unlink()
        run([script,'rotate-db','--role','app'],1,'BOOTSTRAP_JOB_BUSY',{'JOB_TEST':state})
        assert not any('create' in r['args'] or 'apply' in r['args'] or 'delete' in r['args'] for r in records())
    run([script,'rotate-db','--role','app'],1,'BOOTSTRAP_JOB_BUSY',{'JOB_TEST':'active','JOB_BUSY_RELEASE':'ai-gateway-db-restore'})
    assert not any('create' in r['args'] or 'apply' in r['args'] or 'delete' in r['args'] for r in records())
    for release in ['ai-gateway-db','ai-gateway-db-restore','both']:
        for role in ['app','migrator','backup']:
            (work/'calls.jsonl').unlink()
            run([script,'rotate-db','--role',role],0,extra={'RELEASE_TEST':release})
            calls=records(); expected=['ai-gateway-db','ai-gateway-db-restore'] if release=='both' else [release]
            actions=[r['args'] for r in calls if any(k in r['args'] for k in ['delete','create','apply','wait','rollout'])]
            assert actions[:2]==[['-n','tc','create','secret','generic','ai-gateway-db-'+role,'--from-file=password=/dev/stdin','--dry-run=client','-o','yaml'],['-n','tc','apply','-f','-']] or actions[:2]==[['-n','tc','apply','-f','-'],['-n','tc','create','secret','generic','ai-gateway-db-'+role,'--from-file=password=/dev/stdin','--dry-run=client','-o','yaml']]
            for i,target in enumerate(expected):
                assert actions[2+3*i:5+3*i]==[['-n','tc','delete','job',target+'-bootstrap','--wait=true'],['-n','tc','create','-f','-'],['-n','tc','wait','--for=condition=complete','job/'+target+'-bootstrap','--timeout=300s']]
            rest=actions[2+3*len(expected):]
            expected_rest=[]
            if role=='app':
                for target in expected:
                    deployment='ai-gateway-restore' if target.endswith('-restore') else 'ai-gateway'
                    expected_rest.extend([['-n','tc','rollout','restart','deployment/'+deployment],['-n','tc','rollout','status','deployment/'+deployment,'--timeout=300s']])
            assert rest==expected_rest
            assert all('--force' not in a for a in actions)
    (work/'calls.jsonl').unlink()
    run([script,'rotate-db','--role','app'],1,'ROTATION_BOOTSTRAP_FAILED_RECOVERY_REQUIRED',{'JOB_TEST':'wait-fail'})
    assert not any('rollout' in r['args'] for r in records())
    helm=['ops/deploy/aigw-helm.sh','--release','ai-gateway-db','--mode','lint','--exec-sha',requested_sha,'--runconfig','ops/runconfig/fixtures/lint-verify.json']
    # 실제 helm 실행 이전 clone 보안 경계만 stub git으로 검사합니다.
    env.pop('CI_JOB_TOKEN',None); env['CI']='false'; env.pop('AIGW_EXEC_READ_TOKEN',None); env.pop('AIGW_EXEC_READ_USER',None)
    run(helm,2,'EXEC_READ_CREDENTIAL_MISSING')
    auth={'AIGW_EXEC_READ_TOKEN':sentinel,'AIGW_EXEC_READ_USER':'test-user'}
    run(helm,2,'EXEC_CHART_MISSING',auth)
    run(helm,2,'EXEC_READ_FORBIDDEN',auth|{'GIT_TEST':'forbidden'})
    assert any(r.get('scanVerified') for r in records())
    run(helm,2,'EXEC_SHA_MISMATCH',auth|{'GIT_TEST':'wrong-sha'})
    assert requested_sha!='0'*40
    run(helm,0,extra=auth|{'GIT_TEST':'chart','GIT_TRACE':str(work/'trace'),'GIT_TRACE2_EVENT':str(work/'trace2'),'GIT_CURL_VERBOSE':'1'})
    run(helm,2,'EXEC_CHART_MISSING',{'CI':'true','CI_JOB_TOKEN':sentinel,'EXPECTED_USER':'gitlab-ci-token'})
    # 수명 제어는 Secret 참조만 바꾸며 접속 제한은 비활성 시에도 유지합니다.
    run(helm+['--acceptance-role','invalid'],2,'ACCEPTANCE_ROLE_INVALID',auth)
    run(helm+['--acceptance-role',''],2,'ACCEPTANCE_ROLE_INVALID',auth)
    local=['ops/deploy/aigw-helm.sh','--release','ai-gateway-db','--mode','render','--exec-dir',str(exec_dir),'--runconfig','ops/runconfig/fixtures/lint-verify.json']
    import yaml
    for release in ['ai-gateway-db','ai-gateway-db-restore']:
        command=local.copy(); command[2]=release
        enabled=run(command+['--acceptance-role','enabled'],0)
        for repeat in range(2):
            disabled=run(command+['--acceptance-role','disabled'],0)
            documents=list(yaml.safe_load_all(disabled.stdout))
            enabled_documents=list(yaml.safe_load_all(enabled.stdout))
            hba=lambda docs:next(d for d in docs if d['kind']=='ConfigMap' and d['metadata']['name'].endswith('-hba'))
            assert hba(documents)==hba(enabled_documents)
            job=next(d for d in documents if d['kind']=='Job')['spec']['template']['spec']['containers'][0]
            assert 'acceptance_role_enabled=false' in ' '.join(job['args'])
            assert all(e['name']!='AIGW_ACCEPTANCE_PASSWORD' for e in job['env'])
            assert len([e for e in job['env'] if 'valueFrom' in e])==4
    # 정본 변조는 Helm과 클러스터 접근 전에 고정 오류로 거부해야 합니다.
    source=work/'ops/postgres/acceptance-pg_hba.conf'; original=source.read_bytes()
    variants=[original.rstrip(b'\n'),original.replace(b'local all',b'local postgres'),
              original.replace(b'aigw_acceptance_ro',b'+aigw_acceptance_ro'),
              original.replace(b'scram-sha-256',b'trust'),b'\n'.join(original.splitlines()[:4])+b'\n',
              original.replace(b'/18/docker/',b'/17/docker/'),
              b'\n'.join([original.splitlines()[1],original.splitlines()[0],*original.splitlines()[2:]])+b'\n']
    for content in variants:
        source.write_bytes(content)
        run(local,2,'HBA_POLICY_INVALID')
    source.write_bytes(original)
    # 배포 변경 명령만 stub으로 막고 실제 lint/template 후 호출자의 승인 파일 결속을 검사합니다.
    stub('helm',r'''
import json,os,subprocess,sys
args=sys.argv[1:]
if args[0]=='upgrade': sys.exit(0)
if args[0]=='status': print(json.dumps(dict(info=dict(status='deployed'),version=1))); sys.exit(0)
sys.exit(subprocess.call([os.environ['REAL_HELM'],*args]))
''')
    deploy_data=json.loads((work/'ops/runconfig/fixtures/lint-verify.json').read_text())
    deploy_data['purpose']='deploy'; deploy_data['execChartSha']['value']=requested_sha
    deploy_data['backup']['type']['value']='sftp'
    deploy_data['backup']['destination']['value']='sftp://lint-only.invalid/backup'
    def approve(node):
        for value in node.values():
            if isinstance(value,dict):
                if 'value' in value: value['approvalRef']='docs/gates/f1/synthetic.md'
                else: approve(value)
    approve(deploy_data)
    (work/'deploy-input.json').write_text(json.dumps(deploy_data))
    for name in ['exec-chart-ref','postgres-image','corpus-inventory','pvc-capacity','pvc-protection','backup-destination']:
        (gate/(name+'.md')).write_text('Status: APPROVED\n')
    backup_config('s3')
    deploy=helm.copy(); deploy[4]='deploy'; deploy[-1]='deploy-input.json'
    run(deploy,0,extra=auth|{'GIT_TEST':'chart','BACKUP_FIELDS':'sftp','REAL_HELM':real_helm})
    run(deploy,13,'RUNTIME_CREDENTIALS_MISSING',auth|{'GIT_TEST':'chart','BACKUP_FIELDS':'sftp','REAL_HELM':real_helm,'ACCEPTANCE_TEST':'absent'})
    disabled_start=len(records())
    run(deploy+['--acceptance-role','disabled'],0,extra=auth|{'GIT_TEST':'chart','BACKUP_FIELDS':'sftp','REAL_HELM':real_helm,'ACCEPTANCE_TEST':'absent'})
    assert not any(r['tool']=='kubectl' and 'get' in r['args'] and 'ai-gateway-db-acceptance-ro' in r['args'] for r in records()[disabled_start:])
    assert any(r['tool']=='kubectl' and r['args'][2:4]==['rollout','status'] for r in records())
    assert any(r['tool']=='kubectl' and 'exec' in r['args'] for r in records())
    for failure in ['sql-error','invalid-rule','missing-include']:
        run(deploy,1,'HBA_DEPLOY_CHECK_FAILED',auth|{'GIT_TEST':'chart','BACKUP_FIELDS':'sftp','REAL_HELM':real_helm,'HBA_TEST':failure})
        evidence=max((work/'evidence/F1/gates').glob('deploy-*.json'),key=lambda p:p.stat().st_mtime_ns)
        assert json.loads(evidence.read_text())['status']=='FAIL'
    run(deploy,0,extra=auth|{'GIT_TEST':'chart','BACKUP_FIELDS':'sftp','REAL_HELM':real_helm,'HBA_TEST':'original-nested-include'})
    run(deploy,1,'HBA_ROLLOUT_FAILED',auth|{'GIT_TEST':'chart','BACKUP_FIELDS':'sftp','REAL_HELM':real_helm,'ROLLOUT_TEST':'fail'})
    calls=(work/'calls.jsonl').read_text()
    assert sentinel not in calls
    for record in records():
        if 'helper' in record: assert not pathlib.Path(record['helper']).exists()
        if record['tool']=='git': assert all('extraHeader' not in arg and sentinel not in arg for arg in record['args'])
    for file in work.rglob('*'):
        if file.is_file(): assert sentinel.encode() not in file.read_bytes()
    print(json.dumps(dict(status='PASS',boundary='stub kubectl/git/Helm upgrade+status; actual Helm lint/template; 실제 Secret/Source 접근 없음',checks=checks),ensure_ascii=False))
PY
