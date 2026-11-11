{{- define "db.guard" -}}
{{- if ne .Release.Name "ai-gateway-db-restore" }}{{ fail "RENDER_INVALID_RELEASE" }}{{ end -}}
{{- if ne .Release.Namespace "tc" }}{{ fail "RENDER_INVALID_NAMESPACE" }}{{ end -}}
{{- if ne (toString .Values.replicas) "1" }}{{ fail "RENDER_INVALID_REPLICAS" }}{{ end -}}
{{- $tag := required "RENDER_REQUIRED_pgImage.tag" .Values.pgImage.tag -}}
{{- if not (regexMatch "^[A-Za-z0-9_][A-Za-z0-9_.-]*-pg18(-[A-Za-z0-9_.-]+)?$" $tag) }}{{ fail "RENDER_INVALID_PG18_TAG" }}{{ end -}}
{{- if not (regexMatch "^[a-f0-9]{64}$" .Values.pgImage.digest) }}{{ fail "RENDER_INVALID_IMAGE_DIGEST" }}{{ end -}}
{{- $size := required "RENDER_REQUIRED_persistence.size" .Values.persistence.size -}}
{{- if not (regexMatch "^[1-9][0-9]*Gi$" $size) }}{{ fail "RENDER_INVALID_PVC_SIZE" }}{{ end -}}
{{- end -}}
{{- define "db.labels" -}}
app: ai-gateway-db
aigw.nori/db-instance: {{ .Release.Name }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end -}}
{{- define "db.image" -}}harbor.nori.local/library/pgvector:{{ .Values.pgImage.tag }}@sha256:{{ .Values.pgImage.digest }}{{- end -}}
{{- /* 임시 인스턴스의 superuser·단일 계정 자격은 앱과 같은 Secret ai-gateway-config 의 두 키에서 받는다(Plan U6 ③ — DB 역할 Secret 4종 없음). */ -}}
{{- define "db.accountEnv" -}}
- name: AI_GATEWAY_DB_USER
  valueFrom:
    secretKeyRef:
      name: ai-gateway-config
      key: AI_GATEWAY_DB_USER
- name: AI_GATEWAY_DB_PASSWORD
  valueFrom:
    secretKeyRef:
      name: ai-gateway-config
      key: AI_GATEWAY_DB_PASSWORD
{{- end -}}
