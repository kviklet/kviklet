{{- define "fullname" -}}
{{- if .Values.fullnameOverride -}}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- $name := default .Chart.Name .Values.nameOverride -}}
{{- if contains $name .Release.Name -}}
{{- .Release.Name | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" -}}
{{- end -}}
{{- end -}}
{{- end -}}


{{/*
Return the Postgresql Secret Name
*/}}
{{- define "config.datasource.secretName" -}}
{{- if .Values.postgresql.enabled }}
    {{- if .Values.postgresql.auth.existingSecret -}}
        {{- printf "%s" .Values.postgresql.auth.existingSecret -}}
    {{- else -}}
        {{- $releaseName := .Release.Name -}}
        {{- printf "%s" .Release.Name -}}-postgresql
    {{- end -}}
{{- end -}}
{{- end -}}

{{- define "config.datasourceUrl" -}}
{{- $releaseName := .Release.Name -}}
{{- $databaseName := default "kviklet" .Values.postgresql.auth.database}}
{{- printf "%s" .Values.postgresql.auth.existingSecret -}}
jdbc:postgresql://{{ $releaseName }}-postgresql.kviklet.svc:5432/{{ $databaseName }}
{{- end -}}