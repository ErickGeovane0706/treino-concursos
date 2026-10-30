# Guia de Deploy, Backup e Monitoramento em Produção

Este guia explica, passo a passo, como colocar o **Treino de Concursos** no ar em um servidor **VPS Linux** começando do zero — pensado para quem nunca configurou um servidor antes.

Ele cobre os requisitos **RNF01** (HTTPS e cabeçalhos de segurança), **RNF04** (disponibilidade de 99%, monitoramento com alerta por e-mail, backup diário com retenção de 30 dias, cópia externa e teste de restauração) e **RNF09** (imagem Docker única em VPS de baixo custo).

---

## Visão geral da arquitetura de produção

```
Visitante / Googlebot
        │
        ▼  HTTPS (TLS 1.2+ / HTTP redireciona para HTTPS)
┌──────────────────────────────────────────────────────────┐
│ Cloudflare (DNS + CDN + Cache Rules + Proteção DDoS)     │
└──────────────────────────────────────────────────────────┘
        │
        ▼  HTTPS Full (strict) — Certificado de Origem (porta 443)
┌──────────────────────────────────────────────────────────┐
│ VPS Linux (2 vCPU, 4 GB RAM — Ubuntu 24.04 LTS)          │
│ Firewall UFW: apenas portas 22 (SSH), 80 e 443 abertas   │
│                                                          │
│  ┌────────────────────────────────────────────────────┐  │
│  │ Proxy Reverso (Nginx / Caddy) em :80 e :443        │  │
│  └─────────────────────────┬──────────────────────────┘  │
│                            │ http://127.0.0.1:8080       │
│  ┌─────────────────────────▼──────────────────────────┐  │
│  │ Docker Compose (docker-compose.prod.yml)           │  │
│  │                                                    │  │
│  │  • app     → Imagem única (Vite + Spring Boot)     │  │
│  │              JRE 21 com -Xmx512m (loopback :8080)  │  │
│  │  • db      → PostgreSQL 17 (SEM porta exposta)     │  │
│  │  • backup  → pg_dump diário (03h00) + retenção     │  │
│  │              de 30 dias + rclone para bucket S3/R2 │  │
│  └────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────┘
```

---

## 1. Criar o servidor VPS e fazer o primeiro acesso

### 1.1 Escolher e contratar o VPS (RNF09)

Qualquer provedor de nuvem que ofereça Linux serve (ex.: **Hetzner Cloud**, **DigitalOcean**, **Vultr**, **Linode/Akamai** ou **Hostinger VPS**). Escolha a seguinte configuração mínima:

- **Sistema operacional:** Ubuntu 24.04 LTS (64 bits)
- **Processador:** 2 vCPU
- **Memória:** 4 GB de RAM
- **Disco:** 40 GB SSD NVMe

### 1.2 Gerar sua chave SSH (no seu computador)

Antes de criar o servidor (ou logo após), abra o terminal do seu computador e gere um par de chaves SSH caso ainda não tenha:

```bash
ssh-keygen -t ed25519 -C "deploy-treino-concursos"
```

Pressione `Enter` para aceitar o caminho padrão (`~/.ssh/id_ed25519`) e defina uma senha para a chave. Em seguida, exiba a chave pública para colar no painel do provedor do VPS:

```bash
cat ~/.ssh/id_ed25519.pub
```

### 1.3 Conectar ao VPS e criar um usuário administrativo

Copie o endereço IP público do VPS (ex.: `203.0.113.10`) e conecte-se como `root`:

```bash
ssh root@203.0.113.10
```

Atualize o sistema operacional, configure o fuso horário de Brasília e crie um usuário chamado `deploy` com permissão de `sudo`:

```bash
apt update && apt upgrade -y
timedatectl set-timezone America/Sao_Paulo

adduser deploy
usermod -aG sudo deploy

# Copia sua chave SSH autorizada do root para o usuário deploy
rsync --archive --chown=deploy:deploy ~/.ssh /home/deploy
```

### 1.4 Criar área de Swap (2 GB de margem de segurança)

Mesmo com 4 GB de RAM e a JVM limitada a `-Xmx512m`, ter 2 GB de swap evita quedas por falta de memória durante o build da imagem Docker:

```bash
fallocate -l 2G /swapfile
chmod 600 /swapfile
mkswap /swapfile
swapon /swapfile
echo '/swapfile none swap sw 0 0' | tee -a /etc/fstab
```

### 1.5 Desativar login por senha no SSH

Abra uma **nova janela de terminal** no seu computador e teste se você consegue entrar com `ssh deploy@203.0.113.10` e rodar `sudo whoami`. Só depois de confirmar que funcionou, desative o login por senha no SSH:

```bash
sudo sed -i 's/^#\?PasswordAuthentication.*/PasswordAuthentication no/' /etc/ssh/sshd_config
sudo sed -i 's/^#\?PermitRootLogin.*/PermitRootLogin no/' /etc/ssh/sshd_config
sudo systemctl restart ssh
```

---

## 2. Configurar o Firewall (apenas portas 22, 80 e 443)

O VPS só deve aceitar conexões externas nas portas **22** (SSH), **80** (HTTP) e **443** (HTTPS). Nem o PostgreSQL (`5432`) nem a porta direta do Spring Boot (`8080`) ficam abertos para a internet:

```bash
sudo apt install -y ufw

# Bloqueia qualquer entrada por padrão e libera saídas
sudo ufw default deny incoming
sudo ufw default allow outgoing

# Libera exclusivamente SSH (22), HTTP (80) e HTTPS (443)
sudo ufw allow 22/tcp comment 'SSH'
sudo ufw allow 80/tcp comment 'HTTP'
sudo ufw allow 443/tcp comment 'HTTPS'

# Ativa o firewall
sudo ufw enable
sudo ufw status verbose
```

A saída de `sudo ufw status verbose` deve listar apenas `22/tcp`, `80/tcp` e `443/tcp` como `ALLOW IN`.

---

## 3. Instalar o Docker e o Docker Compose

Execute os comandos oficiais do repositório da Docker para instalar o Docker Engine e o plugin `docker compose` v2 no Ubuntu:

```bash
sudo apt install -y ca-certificates curl gnupg
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg

echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
  $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | \
  sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

# Permite que o usuário deploy rode comandos docker sem sudo
sudo usermod -aG docker deploy
sudo systemctl enable --now docker
```

Saia da sessão SSH (`exit`) e conecte-se novamente (`ssh deploy@203.0.113.10`) para que o grupo `docker` passe a valer. Confirme com:

```bash
docker --version
docker compose version
```

---

## 4. Configurar a Cloudflare (DNS, SSL "Full (strict)" e Cache)

### 4.1 Apontar o domínio para a Cloudflare

1. Crie uma conta gratuita em [cloudflare.com](https://www.cloudflare.com/) e clique em **Add a domain** informando seu domínio (ex.: `seudominio.com.br`).
2. Escolha o plano **Free** e anote os dois *nameservers* fornecidos pela Cloudflare (ex.: `ada.ns.cloudflare.com` e `bob.ns.cloudflare.com`).
3. No painel do [Registro.br](https://registro.br/), abra o seu domínio, clique em **Alterar servidores DNS** e informe os dois *nameservers* da Cloudflare.
4. No painel da Cloudflare, em **DNS → Records**, crie os dois registros apontando para o IP público do seu VPS, ambos com **Proxy status: Proxied (nuvem laranja ativada)**:

| Tipo | Nome | Conteúdo | Proxy |
|---|---|---|---|
| `A` | `@` | `203.0.113.10` (IP do seu VPS) | Proxied (nuvem laranja) |
| `A` | `www` | `203.0.113.10` (IP do seu VPS) | Proxied (nuvem laranja) |

> Se o seu `SITE_URL` usar `https://www.seudominio.com.br`, crie em **Rules → Redirect Rules** uma regra simples redirecionando `seudominio.com.br/*` (sem `www`) com `301` para `https://www.seudominio.com.br/${1}` (ou vice-versa, mantendo sempre um único endereço canônico).

### 4.2 Configurar SSL/TLS como "Full (strict)" (RNF01)

1. Na Cloudflare, acesse **SSL/TLS → Overview** e selecione o modo de criptografia **Full (strict)**.
2. Em **SSL/TLS → Edge Certificates**:
   - Ative **Always Use HTTPS** (redireciona todo acesso HTTP para HTTPS).
   - Defina **Minimum TLS Version** como **TLS 1.2**.
   - Ative **Automatic HTTPS Rewrites**.
3. Em **SSL/TLS → Origin Server**, clique em **Create Certificate**:
   - Mantenha a opção padrão (*Generate private key and CSR with Cloudflare*, validade de **15 anos**, cobrindo `seudominio.com.br` e `*.seudominio.com.br`).
   - Clique em **Create**.
   - Você verá dois blocos de texto: **Origin Certificate** (`.pem`) e **Private Key** (`.key`). Guarde essa tela aberta para o próximo passo.

### 4.3 Configurar o Proxy Reverso (Nginx) no VPS com o Certificado de Origem

No VPS, instale o Nginx e salve o Certificado de Origem da Cloudflare:

```bash
sudo apt install -y nginx
sudo mkdir -p /etc/ssl/cloudflare
sudo chmod 700 /etc/ssl/cloudflare
```

Cole o conteúdo do **Origin Certificate** em `/etc/ssl/cloudflare/origem.pem`:

```bash
sudo nano /etc/ssl/cloudflare/origem.pem
```

Cole o conteúdo da **Private Key** em `/etc/ssl/cloudflare/origem.key` e restrinja as permissões:

```bash
sudo nano /etc/ssl/cloudflare/origem.key
sudo chmod 600 /etc/ssl/cloudflare/origem.pem /etc/ssl/cloudflare/origem.key
```

Agora crie o arquivo `/etc/nginx/sites-available/treino-concursos`:

```bash
sudo nano /etc/nginx/sites-available/treino-concursos
```

Com o seguinte conteúdo (substitua `seudominio.com.br` pelo seu domínio real):

```nginx
server {
    listen 80;
    listen [::]:80;
    server_name seudominio.com.br www.seudominio.com.br;
    return 301 https://www.seudominio.com.br$request_uri;
}

server {
    listen 443 ssl http2;
    listen [::]:443 ssl http2;
    server_name seudominio.com.br;

    ssl_certificate     /etc/ssl/cloudflare/origem.pem;
    ssl_certificate_key /etc/ssl/cloudflare/origem.key;
    ssl_protocols       TLSv1.2 TLSv1.3;

    return 301 https://www.seudominio.com.br$request_uri;
}

server {
    listen 443 ssl http2;
    listen [::]:443 ssl http2;
    server_name www.seudominio.com.br;

    ssl_certificate     /etc/ssl/cloudflare/origem.pem;
    ssl_certificate_key /etc/ssl/cloudflare/origem.key;
    ssl_protocols       TLSv1.2 TLSv1.3;

    # Restringe logs de acesso a 14 dias no logrotate do Ubuntu (RNF08)
    access_log /var/log/nginx/treino_access.log;
    error_log  /var/log/nginx/treino_error.log;

    location / {
        proxy_pass         http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header   Host              $host;
        proxy_set_header   X-Real-IP         $remote_addr;
        proxy_set_header   X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header   X-Forwarded-Proto https;
        proxy_set_header   X-Forwarded-Port  443;
    }
}
```

Ative o site, ajuste a retenção de logs do Nginx para 14 dias (conforme **RNF08**) e recarregue o serviço:

```bash
sudo ln -sf /etc/nginx/sites-available/treino-concursos /etc/nginx/sites-enabled/treino-concursos
sudo rm -f /etc/nginx/sites-enabled/default
sudo sed -i 's/rotate [0-9]\+/rotate 14/' /etc/logrotate.d/nginx
sudo nginx -t && sudo systemctl reload nginx
```

### 4.4 Configurar as Regras de Cache (Cache Rules) na Cloudflare (RNF03)

No painel da Cloudflare, abra **Caching → Cache Rules** e crie as três regras abaixo, nesta ordem:

1. **Regra 1 — Painel e API sem cache (`Bypass Cache`):**
   - **When incoming requests match:** `(starts_with(http.request.uri.path, "/admin")) or (starts_with(http.request.uri.path, "/api"))`
   - **Cache eligibility:** *Bypass cache*
2. **Regra 2 — Ativos estáticos com hash (`/assets/*`, `/fontes/*`, `/css/*`):**
   - **When incoming requests match:** `(starts_with(http.request.uri.path, "/assets/")) or (starts_with(http.request.uri.path, "/fontes/")) or (starts_with(http.request.uri.path, "/css/"))`
   - **Cache eligibility:** *Eligible for cache*
   - **Edge TTL:** *Respect origin* (ou 1 ano para `/assets/*`, que já traz hash no nome do arquivo).
3. **Regra 3 — Páginas públicas HTML (`/`, `/questao/*`, `/questoes*`, páginas institucionais):**
   - **When incoming requests match:** `(http.request.method eq "GET") and not (starts_with(http.request.uri.path, "/admin")) and not (starts_with(http.request.uri.path, "/api"))`
   - **Cache eligibility:** *Eligible for cache*
   - **Edge TTL:** *Use cache-control header if present, bypass cache if not* (respeita o `Cache-Control` enviado pelo Spring Boot).

---

## 5. Implantar a Aplicação no VPS

### 5.1 Clonar o repositório

Conectado ao VPS com o usuário `deploy`:

```bash
sudo mkdir -p /opt/treino-concursos
sudo chown deploy:deploy /opt/treino-concursos
git clone https://github.com/ErickGeovane0706/treino-concursos.git /opt/treino-concursos
cd /opt/treino-concursos
```

### 5.2 Preencher as variáveis de ambiente de produção (`.env`)

Gere duas senhas fortes (uma para o banco de dados e outra para o primeiro Administrador do painel):

```bash
openssl rand -base64 32
openssl rand -base64 24
```

Copie o arquivo `.env.example` para `.env` e proteja-o para que apenas o usuário `deploy` possa lê-lo:

```bash
cp .env.example .env
chmod 600 .env
nano .env
```

Preencha todas as variáveis no `/opt/treino-concursos/.env`:

| Variável | Descrição | Exemplo |
|---|---|---|
| `DB_NAME` | Nome do banco PostgreSQL | `treino_concursos` |
| `DB_USERNAME` | Usuário do PostgreSQL | `treino` |
| `DB_PASSWORD` | Senha forte do PostgreSQL (gerada com `openssl`) | *(colar senha gerada)* |
| `SITE_URL` | URL canônica pública (sem barra no final) | `https://www.seudominio.com.br` |
| `CONTATO_EMAIL` | E-mail público exibido em Contato e Privacidade (RF18) | `contato@seudominio.com.br` |
| `CONTATO_RESPONSAVEL` | Nome do responsável legal exibido nas páginas legais | `Seu Nome Completo` |
| `ADMIN_INICIAL_EMAIL` | E-mail do primeiro Administrador criado na subida (RF01 RN5) | `admin@seudominio.com.br` |
| `ADMIN_INICIAL_SENHA` | Senha inicial do Administrador (8 a 72 caracteres) | *(colar senha gerada)* |
| `ADS_ENABLED` | Ativa o script do Google AdSense (`false` até a aprovação na Fase 2) | `false` |
| `ADSENSE_PUBLISHER_ID` | ID de publisher do AdSense (`pub-...`, preencher na Fase 2) | *(vazio por enquanto)* |
| `ADSENSE_SLOT_QUESTAO` | ID do bloco de anúncio da questão (Fase 2) | *(vazio por enquanto)* |
| `ADSENSE_SLOT_LATERAL` | ID do bloco de anúncio lateral (Fase 2) | *(vazio por enquanto)* |
| `BACKUP_RETENCAO_DIAS` | Dias de retenção dos arquivos de backup locais e remotos | `30` |
| `BACKUP_CRON` | Horário diário do `pg_dump` (fuso de Brasília) | `0 3 * * *` |
| `BACKUP_S3_PROVIDER` | Provedor compatível com S3 (`Cloudflare`, `Backblaze`, `AWS`) | `Cloudflare` |
| `BACKUP_S3_BUCKET` | Nome do bucket externo onde os backups são guardados | `treino-concursos-backups` |
| `BACKUP_S3_PREFIXO` | Pasta dentro do bucket | `treino-concursos` |
| `BACKUP_S3_ENDPOINT` | Endpoint S3 do provedor (ex.: Cloudflare R2) | `https://<ACCOUNT_ID>.r2.cloudflarestorage.com` |
| `BACKUP_S3_ACCESS_KEY_ID` | Access Key ID do bucket de backup | *(colar chave)* |
| `BACKUP_S3_SECRET_ACCESS_KEY` | Secret Access Key do bucket de backup | *(colar chave secreta)* |

### 5.3 Construir a imagem única e subir os serviços

Execute o Docker Compose de produção:

```bash
docker compose -f docker-compose.prod.yml up -d --build
```

O que acontece nesse comando:
1. O `Dockerfile` multi-stage compila o frontend com Node 22 (`npm ci && npm run build`), empacota o `.jar` no Maven copiando `frontend/dist` para `static/`, e gera a imagem final com JRE 21 configurada com `-Xmx512m`.
2. O container `treino-concursos-db` (`postgres:17-alpine`) sobe **apenas na rede interna** (sem porta exposta no host).
3. O container `treino-concursos-app` aguarda o banco ficar *healthy*, aplica as migrations do Flyway (`V1`, `V2`, `V3`...), cria o primeiro usuário `ADMIN` e escuta em `127.0.0.1:8080`.
4. O container `treino-concursos-backup` agenda o backup diário para as 03h00 da manhã.

### 5.4 Verificar se tudo subiu corretamente

```bash
# 1. Todos os 3 containers devem aparecer como Up (e db/app como healthy)
docker compose -f docker-compose.prod.yml ps

# 2. Confirme nos logs que o Flyway rodou e o Spring iniciou na porta 8080
docker compose -f docker-compose.prod.yml logs --tail=50 app

# 3. Confirme que a porta 5432 do PostgreSQL NÃO aparece aberta no host
sudo ss -tulpn | grep 5432 || echo "OK: PostgreSQL isolado na rede interna"

# 4. Teste os cabeçalhos HTTPS e de segurança (RNF01) pelo domínio público
curl -I https://www.seudominio.com.br/sobre
```

A resposta do `curl -I` deve retornar `HTTP/2 200` e conter os cabeçalhos `strict-transport-security`, `content-security-policy`, `x-content-type-options: nosniff` e `referrer-policy: strict-origin-when-cross-origin`.

### 5.5 Como atualizar o site em novas versões

Sempre que fizer merge de uma nova funcionalidade na branch `main`:

```bash
cd /opt/treino-concursos
git pull origin main
docker compose -f docker-compose.prod.yml up -d --build app
```

---

## 6. Backup Diário com Cópia Externa (RNF04)

### 6.1 Criar o bucket gratuito no Cloudflare R2 (ou Backblaze B2)

Para que o backup sobreviva mesmo em caso de perda total do VPS, uma cópia é enviada diariamente para fora do servidor:

1. No painel da Cloudflare, abra **R2 Object Storage → Create bucket**.
2. Dê o nome `treino-concursos-backups` e clique em **Create bucket**.
3. Volte para a página inicial do R2, clique em **Manage R2 API Tokens → Create API token**:
   - **Permissions:** *Object Read & Write*
   - **Specify bucket(s):** selecione apenas `treino-concursos-backups`
   - Clique em **Create API Token** e copie os valores de **Access Key ID**, **Secret Access Key** e **Jurisdiction-specific endpoint for S3 clients** (`https://<ACCOUNT_ID>.r2.cloudflarestorage.com`) para o seu `/opt/treino-concursos/.env`.
4. Recrie o container de backup para carregar as variáveis:
   ```bash
   docker compose -f docker-compose.prod.yml up -d backup
   ```

### 6.2 Disparar e validar um backup agora mesmo

Você não precisa esperar até as 03h00 da manhã para testar o backup. Execute manualmente:

```bash
docker compose -f docker-compose.prod.yml exec backup /usr/local/bin/backup.sh
```

Saída esperada:
```text
[...] Iniciando pg_dump do banco 'treino_concursos' em 'db'...
[...] Backup local salvo em /backups/treino_concursos_2026-10-23_123000.sql.gz (... bytes).
[...] Enviando copia externa para externo:treino-concursos-backups/treino-concursos...
[...] Aplicando retencao de 30 dias no armazenamento externo...
[...] Copia externa concluida com sucesso.
[...] Rotina de backup finalizada.
```

Para listar os arquivos salvos localmente e no bucket externo:

```bash
# Backups no volume local do VPS
docker compose -f docker-compose.prod.yml exec backup ls -lh /backups

# Backups enviados para o armazenamento externo (Cloudflare R2 / S3)
docker compose -f docker-compose.prod.yml exec backup rclone ls externo:treino-concursos-backups/treino-concursos
```

---

## 7. Como Restaurar o Backup (e Teste Mensal Obrigatório — RNF04)

### 7.1 Teste mensal de restauração (sem derrubar o site em produção)

O requisito **RNF04** exige testar a restauração pelo menos uma vez por mês. Você pode testar o arquivo `.sql.gz` mais recente subindo um container PostgreSQL 17 descartável:

```bash
cd /opt/treino-concursos

# 1. Descobre o arquivo de backup mais recente no volume
ARQUIVO_RECENTE=$(docker compose -f docker-compose.prod.yml exec -T backup sh -c 'ls -1t /backups/*.sql.gz | head -n 1')
echo "Testando restauracao de: $ARQUIVO_RECENTE"

# 2. Sobe um PostgreSQL temporário de teste
docker run -d --name pg-teste-restore -e POSTGRES_HOST_AUTH_METHOD=trust postgres:17-alpine
sleep 5

# 3. Restaura o dump dentro do container temporário
docker compose -f docker-compose.prod.yml exec -T backup zcat "$ARQUIVO_RECENTE" \
  | docker exec -i pg-teste-restore psql -v ON_ERROR_STOP=1 -U postgres -d postgres

# 4. Confere a contagem de questões e usuários restaurados
docker exec pg-teste-restore psql -U postgres -d postgres -c "SELECT COUNT(*) AS total_questoes FROM questoes; SELECT COUNT(*) AS total_usuarios FROM usuarios;"

# 5. Remove o container temporário após validar
docker rm -f pg-teste-restore
```

Se o passo 3 terminar sem erro e o passo 4 exibir as contagens esperadas, o backup está íntegro e pronto para uso em emergências.

### 7.2 Procedimento de restauração em produção (em caso de desastre)

1. **Se precisar baixar o backup do armazenamento externo (ex.: em um VPS novo):**
   ```bash
   # Lista os arquivos disponíveis no bucket remoto
   docker compose -f docker-compose.prod.yml exec backup rclone ls externo:treino-concursos-backups/treino-concursos

   # Baixa o arquivo desejado para /backups no VPS
   docker compose -f docker-compose.prod.yml exec backup \
     rclone copy externo:treino-concursos-backups/treino-concursos/treino_concursos_AAAA-MM-DD_HHMMSS.sql.gz /backups/
   ```

2. **Pare temporariamente a aplicação para ninguém gravar dados durante a restauração:**
   ```bash
   docker compose -f docker-compose.prod.yml stop app
   ```

3. **Restaure o arquivo `.sql.gz` no banco `db` (o dump já inclui `--clean --if-exists`):**
   ```bash
   ARQUIVO="/backups/treino_concursos_AAAA-MM-DD_HHMMSS.sql.gz"

   docker compose -f docker-compose.prod.yml exec -T backup zcat "$ARQUIVO" \
     | docker compose -f docker-compose.prod.yml exec -T db \
       psql -v ON_ERROR_STOP=1 -U "${DB_USERNAME:-treino}" -d "${DB_NAME:-treino_concursos}"
   ```

4. **Suba novamente a aplicação e confira os logs:**
   ```bash
   docker compose -f docker-compose.prod.yml start app
   docker compose -f docker-compose.prod.yml logs --tail=30 app
   ```

---

## 8. Monitoramento com Alerta por E-mail (UptimeRobot — RNF04)

Para medir a disponibilidade mínima de 99% ao mês e receber aviso imediato caso o site saia do ar:

1. Crie uma conta gratuita em [uptimerobot.com](https://uptimerobot.com/).
2. Clique em **+ Add New Monitor** e preencha:
   - **Monitor Type:** `HTTP(s)`
   - **Friendly Name:** `Treino de Concursos - Produção`
   - **URL (or IP):** `https://www.seudominio.com.br/sobre`
   - **Monitoring Interval:** `5 minutes`
   - **Alert Contacts To Notify:** marque o seu endereço de e-mail.
3. Clique em **Create Monitor**.
4. **Como testar o alerta por e-mail:**
   - No VPS, pare o container da aplicação por alguns minutos:
     ```bash
     docker compose -f docker-compose.prod.yml stop app
     ```
   - Aguarde a checagem do UptimeRobot detectar a falha (`502 Bad Gateway`) e confirme o recebimento do e-mail **"Monitor is DOWN"**.
   - Em seguida, religue imediatamente a aplicação:
     ```bash
     docker compose -f docker-compose.prod.yml start app
     ```
   - Confirme o recebimento do e-mail **"Monitor is UP"**.

---

## 9. Checklist do Google Search Console (RF09, US14)

Assim que o site estiver no ar com HTTPS:

- [ ] **1. Adicionar propriedade de domínio:**
  1. Acesse o [Google Search Console](https://search.google.com/search-console) e clique em **Adicionar propriedade**.
  2. Escolha a opção **Domínio** e informe `seudominio.com.br`.
  3. Copie o registro `TXT` fornecido pelo Google (`google-site-verification=...`).
  4. No painel da Cloudflare (**DNS → Records**), adicione um registro do tipo `TXT` com nome `@` e o valor copiado.
  5. Volte ao Search Console e clique em **Verificar**.
- [ ] **2. Enviar o Sitemap:**
  1. No menu lateral do Search Console, abra **Sitemaps**.
  2. Em **Adicionar um novo sitemap**, digite `https://www.seudominio.com.br/sitemap.xml` e clique em **Enviar**.
  3. Confirme que o status aparece como **Processado com êxito**.
- [ ] **3. Testar a leitura do HTML pelo Googlebot:**
  1. Cadastre e publique pelo menos uma questão pelo painel (`https://www.seudominio.com.br/admin`).
  2. Copie a URL pública da questão (`https://www.seudominio.com.br/questao/{id}/{slug}`).
  3. Cole a URL na barra superior **Inspeção de URL** do Search Console e clique em **Testar URL publicada**.
  4. Clique em **Ver página testada → HTML** e confirme que o enunciado, as alternativas e o comentário dentro de `<details>` estão presentes no HTML lido pelo Google.

---

## 10. Checklist Final — Marco da Fase 1

Antes de avançar para a Fase 2 (solicitação e ativação do Google AdSense):

- [ ] Site abrindo com `https://` válido e cadeado seguro no celular e no computador.
- [ ] Qualquer pessoa abre uma questão no celular, responde com um toque, vê a correção e o comentário na hora e avança pelo botão **Próxima questão**.
- [ ] O botão **Continuar de onde parei** e a chave **Ocultar resolvidas** funcionam salvando o progresso no navegador.
- [ ] O backup diário rodou, enviou cópia para o bucket externo e o teste de restauração funcionou sem erros.
- [ ] O monitor do UptimeRobot está ativo e enviou o e-mail de teste.
- [ ] **Meta de conteúdo antes de pedir o AdSense:** acesse `/admin` e **cadastre de 200 a 300 questões públicas com comentários próprios e detalhados** explicando por que a alternativa certa está certa e por que as demais estão erradas. Nunca solicite a aprovação do AdSense com o site vazio ou com poucas questões.
