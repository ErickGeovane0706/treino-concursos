#!/bin/sh
# ==============================================================================
# Backup diario do PostgreSQL com retencao de 30 dias e copia externa (RNF04)
# Executado automaticamente pelo cron do servico "backup" ou manualmente com:
#   docker compose -f docker-compose.prod.yml exec backup /usr/local/bin/backup.sh
# ==============================================================================
set -eu

DIRETORIO_BACKUP="${BACKUP_DIR:-/backups}"
RETENCAO_DIAS="${BACKUP_RETENCAO_DIAS:-30}"
DATA_HORA="$(date +%Y-%m-%d_%H%M%S)"
ARQUIVO="${DIRETORIO_BACKUP}/${PGDATABASE}_${DATA_HORA}.sql.gz"
ARQUIVO_TMP="${ARQUIVO}.tmp"

mkdir -p "$DIRETORIO_BACKUP"

echo "[$(date -Iseconds)] Iniciando pg_dump do banco '${PGDATABASE}' em '${PGHOST}'..."
pg_dump --no-owner --no-privileges --clean --if-exists | gzip -9 > "$ARQUIVO_TMP"

# Garante que o arquivo gerado existe e nao e um gzip vazio (> 50 bytes)
TAMANHO="$(wc -c < "$ARQUIVO_TMP" | tr -d ' ')"
if [ "$TAMANHO" -le 50 ]; then
    rm -f "$ARQUIVO_TMP"
    echo "[$(date -Iseconds)] ERRO: arquivo de backup vazio ou invalido (${TAMANHO} bytes)." >&2
    exit 1
fi

mv "$ARQUIVO_TMP" "$ARQUIVO"
chmod 600 "$ARQUIVO"
echo "[$(date -Iseconds)] Backup local salvo em ${ARQUIVO} (${TAMANHO} bytes)."

# Retencao local: remove arquivos com mais de N dias (padrao: 30 dias)
find "$DIRETORIO_BACKUP" -name "${PGDATABASE}_*.sql.gz" -type f -mtime +"$RETENCAO_DIAS" -print -delete

# Copia fora do VPS (RNF04): envia para bucket S3 / Cloudflare R2 / Backblaze B2 via rclone
if [ -n "${BACKUP_S3_BUCKET:-}" ] && [ -n "${RCLONE_CONFIG_EXTERNO_ENDPOINT:-}" ]; then
    DESTINO_REMOTO="externo:${BACKUP_S3_BUCKET}/${BACKUP_S3_PREFIXO:-treino-concursos}"
    echo "[$(date -Iseconds)] Enviando copia externa para ${DESTINO_REMOTO}..."
    rclone copy "$ARQUIVO" "$DESTINO_REMOTO" --s3-no-check-bucket
    echo "[$(date -Iseconds)] Aplicando retencao de ${RETENCAO_DIAS} dias no armazenamento externo..."
    rclone delete "$DESTINO_REMOTO" --min-age "${RETENCAO_DIAS}d"
    echo "[$(date -Iseconds)] Copia externa concluida com sucesso."
else
    echo "[$(date -Iseconds)] AVISO: BACKUP_S3_BUCKET / RCLONE_CONFIG_EXTERNO_ENDPOINT nao configurados; mantendo apenas backup local."
fi

echo "[$(date -Iseconds)] Rotina de backup finalizada."
