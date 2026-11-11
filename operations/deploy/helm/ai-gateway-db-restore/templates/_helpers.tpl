{{- define "db.guard" -}}
{{- if not (has .Release.Name (list "ai-gateway-db" "ai-gateway-db-restore")) }}{{ fail "RENDER_INVALID_RELEASE" }}{{ end -}}
{{- if ne (toString .Values.replicas) "1" }}{{ fail "RENDER_INVALID_REPLICAS" }}{{ end -}}
{{- if and (eq .Release.Name "ai-gateway-db-restore") .Values.backup.enabled }}{{ fail "RENDER_RESTORE_BACKUP_FORBIDDEN" }}{{ end -}}
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
