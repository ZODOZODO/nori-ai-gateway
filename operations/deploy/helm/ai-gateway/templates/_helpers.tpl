{{- define "aigw.labels" -}}
app: {{ .Release.Name }}
app.kubernetes.io/name: ai-gateway
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end -}}

{{- define "aigw.image" -}}
{{ .Values.image.repository }}@sha256:{{ .Values.image.digest }}
{{- end -}}

{{- define "aigw.guard" -}}
{{- $v := .Values -}}
{{- if ne .Release.Name "ai-gateway" }}{{ fail "RENDER_INVALID_RELEASE" }}{{ end -}}
{{- if ne .Release.Namespace "tc" }}{{ fail "RENDER_INVALID_NAMESPACE" }}{{ end -}}
{{- if or (ne (int $v.replicaCount) 1) (ne (int $v.capacity.maxRunningJobs) 1) (ne (int $v.capacity.generationConcurrency) 1) }}{{ fail "용량 확장 게이트 전 초기 프로필만 허용" }}{{ end -}}
{{- if not (regexMatch "^[a-f0-9]{64}$" $v.image.digest) }}{{ fail "APP_IMAGE_DIGEST_REQUIRED" }}{{ end -}}
{{- if or (ne (int $v.capacity.queuedDepth) 4) (ne $v.capacity.queueWaitLimit "60s") (ne $v.capacity.jobLifetimeLimit "180s") }}{{ fail "CAPACITY_PROFILE_CONFLICT" }}{{ end -}}
{{- if and $v.ingest.enabled (not $v.knowledgeSource.read.enabled) }}{{ fail "INGEST_REQUIRES_SOURCE_READ" }}{{ end -}}
{{- if and (eq $v.sanitize.verifyRuleSet "mask-v1r") (or (ne $v.profile "verify") (ne $v.acceptance.suite "F3-RULEVER")) }}{{ fail "VERIFY_RULE_SET_SUITE_ONLY" }}{{ end -}}
{{- if eq $v.profile "prod" -}}
  {{- fail "PRODUCTION_RENDER_BLOCKED: tcAdapter http 구현은 F5a" -}}
{{- else if not $v.testHooks.enabled }}{{ fail "VERIFY_HOOKS_REQUIRED" }}{{ end -}}
{{- if and (not $v.inference.enabled) (not (and (eq $v.profile "verify") $v.testHooks.enabled)) }}{{ fail "DISABLED_INFERENCE_VERIFY_HOOKS_REQUIRED" }}{{ end -}}
{{- /* 앱 자격·DB 설정은 운영자가 만드는 파일 Secret 하나(ai-gateway-config)에서만 받는다. 차트는 만들지 않는다(Plan U6 ①). */ -}}
{{- if or $v.configSecret.create (ne $v.configSecret.name "ai-gateway-config") }}{{ fail "CONFIG_SECRET_REFERENCE_ONLY" }}{{ end -}}
{{- if eq $v.profile "verify" -}}
  {{- $allowed := 0 -}}{{- $blocked := 0 -}}
  {{- range $v.egressProbe.targets -}}
    {{- if eq .expect "ALLOWED" }}{{- $allowed = add1 $allowed -}}{{- else if eq .expect "BLOCKED" }}{{- $blocked = add1 $blocked -}}{{ end -}}
  {{- end -}}
  {{- if or (lt (int $allowed) 2) (lt (int $blocked) 2) }}{{ fail "EGRESS_PROBE_TARGETS_REQUIRED" }}{{ end -}}
{{- end -}}
{{- if $v.inference.enabled -}}
  {{- /* 추론 서버 기존 endpoint 직접 호출(SPEC §26.1 임차 원칙): scheme://host:port 만 받고 경로·끝 "/"·"/v1" 은 거부한다. */ -}}
  {{- $_ := required "INFERENCE_BASE_URL_REQUIRED" $v.inference.baseUrl -}}
  {{- if not (regexMatch "^https?://[^/@?#:[:space:]]+:[0-9]{1,5}$" $v.inference.baseUrl) }}{{ fail "INFERENCE_BASE_URL_INVALID" }}{{ end -}}
  {{- if le (int $v.inference.port) 0 }}{{ fail "INFERENCE_PORT_REQUIRED" }}{{ end -}}
  {{- if ne (int (regexReplaceAll "^.*:([0-9]+)$" $v.inference.baseUrl "${1}")) (int $v.inference.port) }}{{ fail "INFERENCE_PORT_CONFLICT" }}{{ end -}}
{{- end -}}
{{- if $v.knowledgeSource.read.enabled -}}
  {{- $_ := required "SEAWEED_ENDPOINT_REQUIRED" $v.seaweed.s3Endpoint -}}
{{- end -}}
{{- if $v.ingest.enabled -}}
  {{- range $key := list "bootstrapServers" "topic" "groupId" }}{{- $_ := required (printf "KAFKA_REQUIRED:%s" $key) (index $v.kafka $key) -}}{{ end -}}
{{- end -}}
{{- if $v.ingest.fixtureCatalog.enabled -}}
  {{- if ne $v.profile "verify" }}{{ fail "FIXTURE_CATALOG_VERIFY_ONLY" }}{{ end -}}
  {{- $_ := required "FIXTURE_CATALOG_REQUIRED" $v.fixtureCatalog.content -}}
  {{- $_ := required "FIXTURE_PREFIX_REQUIRED" $v.fixtureCatalog.prefix -}}
{{- end -}}
{{- $targets := list "dns" "loki" "tempo" "alloy" "gitlab" -}}
{{- if $v.inference.enabled }}{{- $targets = append $targets "inference" -}}{{ end -}}
{{- if $v.knowledgeSource.read.enabled }}{{- $targets = append $targets "seaweed" -}}{{ end -}}
{{- if $v.ingest.enabled }}{{- $targets = append $targets "kafka" -}}{{ end -}}
{{- range $key := $targets -}}
  {{- $target := required (printf "NETPOL_TARGET_REQUIRED:%s" $key) (index $v.netpol.targets $key) -}}
  {{- $_ := required (printf "NETPOL_APPROVAL_REQUIRED:%s" $key) $target.approvalRef -}}
  {{- if eq $key "inference" }}{{- if ne (int $target.port) (int $v.inference.port) }}{{ fail "INFERENCE_PORT_CONFLICT" }}{{ end -}}{{ end -}}
{{- end -}}
{{- if $v.acceptance.enabled -}}
  {{- if or (ne $v.profile "verify") (eq $v.acceptance.suite "none") }}{{ fail "ACCEPTANCE_VERIFY_SUITE_REQUIRED" }}{{ end -}}
  {{- if not (regexMatch "^[a-f0-9]{64}$" $v.acceptance.image.digest) }}{{ fail "ACCEPTANCE_IMAGE_DIGEST_REQUIRED" }}{{ end -}}
  {{- $_ := required "ACCEPTANCE_PARENT_REQUIRED" $v.acceptance.parentRunId -}}
  {{- range $key := list "acceptanceSigner" "acceptanceClient" }}{{- $_ := required (printf "ACCEPTANCE_REF_REQUIRED:%s" $key) (index $v.credentialsRef $key) -}}{{ end -}}
  {{- range $key := list "loki" "tempo" "metrics" "approvalRef" }}{{- $_ := required (printf "READBACK_REQUIRED:%s" $key) (index $v.acceptance.readback $key) -}}{{ end -}}
  {{- $metrics := required "NETPOL_TARGET_REQUIRED:metrics" (index $v.netpol.targets "metrics") -}}
  {{- $_ := required "NETPOL_APPROVAL_REQUIRED:metrics" $metrics.approvalRef -}}
  {{- if or (not (regexMatch "^[a-f0-9]{64}$" $v.specVerify.sha256)) (not (regexMatch "^[a-f0-9]{64}$" $v.specVerify.expectedSha256)) }}{{ fail "SPEC_VERIFY_SHA_REQUIRED" }}{{ end -}}
  {{- if ne $v.specVerify.sha256 $v.specVerify.expectedSha256 }}{{ fail "SPEC_VERIFY_SHA_MISMATCH" }}{{ end -}}
  {{- $_ := required "SPEC_VERIFY_GZIP_REQUIRED" $v.specVerify.gzipBase64 -}}
  {{- $s := $v.acceptance.suite -}}
  {{- $f1 := has $s (list "F1" "F1-RUBRIC") -}}
  {{- $f2 := has $s (list "F2" "F2-OUTAGE" "F2-RUBRIC" "F3-PROBE") -}}
  {{- $f3 := has $s (list "F3" "F3-ACL" "F3-RULEVER" "F4" "F4-RUBRIC" "F4-OUTAGE" "F4-CLOCK" "F4-PERF") -}}
  {{- if or (and $f1 (or $v.inference.enabled $v.knowledgeSource.read.enabled $v.ingest.enabled $v.ingest.fixtureCatalog.enabled)) (and $f2 (or (not $v.inference.enabled) $v.knowledgeSource.read.enabled $v.ingest.enabled $v.ingest.fixtureCatalog.enabled)) (and $f3 (or (not $v.inference.enabled) (not $v.knowledgeSource.read.enabled) (not $v.ingest.enabled) (not $v.ingest.fixtureCatalog.enabled))) }}{{ fail "SUITE_FLAGS_CONFLICT" }}{{ end -}}
  {{- if and (not $f1) (not $f2) (not $f3) }}{{ fail "SUITE_UNSUPPORTED" }}{{ end -}}
  {{- if hasSuffix "-OUTAGE" $s -}}
    {{- if not (has $v.acceptance.outagePhase (list "STOPPED" "LOADING")) }}{{ fail "OUTAGE_PHASE_REQUIRED" }}{{ end -}}
  {{- else if $v.acceptance.outagePhase }}{{ fail "OUTAGE_PHASE_FORBIDDEN" }}{{ end -}}
  {{- if hasSuffix "-RUBRIC" $s }}{{- $_ := required "RUBRIC_TARGETS_REQUIRED" $v.acceptance.rubricTargets -}}{{ end -}}
  {{- if eq (include "aigw.fixtureWriter" .) "true" }}{{- $_ := required "FIXTURE_WRITER_REF_REQUIRED" $v.credentialsRef.acceptanceFixtureWriter -}}{{ end -}}
{{- end -}}
{{- end -}}

{{- define "aigw.fixtureWriter" -}}
{{- has .Values.acceptance.suite (list "F3" "F3-ACL" "F3-RULEVER" "F4" "F4-PERF") -}}
{{- end -}}

{{- define "aigw.peer" -}}
{{- if .cidr }}
- ipBlock:
    cidr: {{ .cidr | quote }}
{{- else }}
- namespaceSelector:
    matchLabels:
      kubernetes.io/metadata.name: {{ .namespace | quote }}
  podSelector:
    matchLabels:
      {{- toYaml .podSelector | nindent 6 }}
{{- end }}
{{- end -}}

{{- /* DB 대상(Plan §11.3 2026-11-10): 공유 서버 192.168.0.150:15432(ipBlock). */ -}}
{{- define "aigw.dbHost" -}}
192.168.0.150
{{- end -}}

{{- define "aigw.dbEgress" -}}
- to:
    - ipBlock:
        cidr: 192.168.0.150/32
  ports:
    - {protocol: TCP, port: 15432}
{{- end -}}
