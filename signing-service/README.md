# Merkezi Android APK imzalayıcı

My App Store, JKS dosyasını ve parolasını bu servise bir kez gönderir. Servis bunları Fernet ile şifreleyerek diskte saklar. Projelerin GitHub Actions iş akışları, uzun ömürlü GitHub secret kullanmadan GitHub OIDC kimliğiyle imza isteği yapar.

## Güvenlik modeli

- JKS dosyası veya parolası hiçbir uygulama deposuna ya da APK'ya eklenmez.
- Anahtar dosyası ve parola, `SIGNING_ENCRYPTION_KEY` ile şifrelenerek tek bir `.enc` dosyasında saklanır.
- Yalnızca izin verilen GitHub depoları, `main` dalındaki `push` ve `workflow_dispatch` çalıştırmaları imza isteyebilir.
- GitHub OIDC token'ı imza servisi tarafından GitHub'ın JWKS anahtarlarıyla ve özel audience ile doğrulanır.
- APK paket adı, seçilen anahtarın kayıtlı paket adıyla eşleşmelidir.
- `apksigner verify` başarılı olmadan imzalı APK döndürülmez.
- Yönetici token'ı yalnızca anahtar kaydı içindir. Bunu My App Store'un şifreli yerel ayarlarında saklayın.
- Servisi internete doğrudan açmayın; yalnızca HTTPS reverse proxy / Cloudflare Tunnel arkasında yayınlayın. Uygulama tarafında HTTPS zorunludur.

## VM kurulumu

Ubuntu 24.04 üzerinde Python 3.11+, Java 17 ve Android SDK Build Tools 36.0.0 kurulu olmalıdır. `apksigner` ve `aapt` konumlarını `.env` dosyasında belirtin.

```bash
sudo mkdir -p /opt/apk-signing /var/lib/apk-signing/keys
sudo chown -R "$USER":"$USER" /opt/apk-signing
cd /opt/apk-signing
# signing-service klasörünü buraya kopyalayın
python3 -m venv .venv
. .venv/bin/activate
pip install -r signing-service/requirements.txt
cp signing-service/.env.example .env
python -c "from cryptography.fernet import Fernet; print(Fernet.generate_key().decode())"
openssl rand -hex 32
```

Üretilen iki değeri `.env` içindeki `SIGNING_ENCRYPTION_KEY` ve `SIGNING_ADMIN_TOKEN` alanlarına girin. `.env` dosyasını yalnızca servis kullanıcısının okuyabildiğinden emin olun:

```bash
chmod 600 .env
sudo useradd --system --home /opt/apk-signing --shell /usr/sbin/nologin apk-signing || true
sudo chown root:apk-signing .env
sudo chmod 640 .env
sudo chown -R root:root /opt/apk-signing
sudo chmod -R a+rX /opt/apk-signing
sudo chown -R apk-signing:apk-signing /var/lib/apk-signing
```

Servisi test amaçlı başlatın:

```bash
set -a; . ./.env; set +a
cd /opt/apk-signing/signing-service
../.venv/bin/uvicorn main:APP --host 127.0.0.1 --port 8787
```

Başka terminalden `curl http://127.0.0.1:8787/health` çağrısı `{"ok":true,...}` döndürmelidir. Doğrulandıktan sonra systemd servisini etkinleştirin:

```bash
sudo cp /opt/apk-signing/signing-service/apk-signing.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now apk-signing
sudo systemctl status apk-signing
```

Cloudflare Tunnel ingress'ini `http://127.0.0.1:8787` adresine yönlendirin ve yalnızca HTTPS hostname yayınlayın. Public firewall/NSG üzerinde 8787 portunu açmayın.

## My App Store'dan anahtar ekleme

1. My App Store > **İMZALAMA** bölümünü açın.
2. Servisin HTTPS adresini ve `.env` içindeki yönetici token'ını girin.
3. Key ID olarak `my-app-store`, paket adı olarak `com.dincer552.hattrickssh`, alias olarak mevcut JKS alias'ını girin.
4. Mevcut kurulumla aynı JKS dosyasını seçin ve anahtar parolasını girerek kaydedin.

Diğer uygulamalar için her uygulama/Android package için ayrı key ID ve JKS tanımlayın. Aynı paketin tüm sürümleri daima aynı imza anahtarını kullanmalıdır. Mevcut sertifika kaybolursa Android mevcut uygulamanın üstüne güncelleme kurmaz.

## GitHub Actions

Her imza isteyen repoda Actions variable `APK_SIGNING_SERVICE_URL` değerini servisin HTTPS adresi olarak tanımlayın. Secret veya JKS eklemeyin. Workflow için `id-token: write` izni verin. `main` üzerindeki build job, GitHub'ın kısa ömürlü OIDC token'ını alıp imzasız APK'yı `POST /v1/sign` ile gönderir. Bu depodaki `.github/workflows/build-apk.yml` örnek uygulamadır.

Servisteki `ALLOWED_GITHUB_REPOSITORIES` listesinde ilgili `owner/repo` bulunmalıdır. Workflow yalnızca `refs/heads/main`, `push` veya `workflow_dispatch` koşullarında imzalanır.

## İstek sözleşmesi

- `PUT /v1/admin/keys/{key_id}`: Bearer admin token + multipart `keystore`, `package_name`, `repository`, `key_alias`, `key_password`.
- `POST /v1/sign`: Bearer GitHub OIDC token + multipart `key_id`, `apk`.
- `GET /health`: servis sağlık kontrolü.
