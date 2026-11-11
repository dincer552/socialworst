# Cloud Agent (VM)

Ubuntu VM üzerinde çalışan, OpenAI Responses API kullanan ve her eylem için açık kullanıcı onayı isteyen ilk sürüm.

## Güvenlik
- Servis yalnızca 127.0.0.1:8765 üzerinde dinler; public interface'e bind etmez.
- API erişimi rastgele üretilen bearer token ile korunur.
- OpenAI API anahtarı yalnızca VM'de /etc/cloud-agent/agent.env içinde tutulur; GitHub'a veya APK'ya konmaz.
- Model yalnızca sabit eylem listesinden öneri oluşturabilir. Modelin ürettiği shell komutu çalıştırılmaz.
- Eylemler pending_approval durumundayken çalıştırılmaz. /approve çağrısı açık onaydır.
- İlk sürümde izin verilenler salt-okunur disk, RAM, başarısız servis ve son hata raporlarıdır.
- Komutlar sabit argv listeleriyle subprocess üzerinden çalışır; shell=True kullanılmaz.
- Servis root olmayan cloud-agent hesabıyla çalışır.

## Kurulum
Kaynakları VM'ye kopyaladıktan sonra:
```bash
cd cloud-agent
sudo bash install.sh
```
Kurulum sırasında OpenAI API key ve model adı sorulur. Varsayılan model gpt-6; hesabınızda erişimi olan bir model adını girebilirsiniz. Anahtar yalnızca VM'ye kaydedilir.

```bash
sudo systemctl status cloud-agent
sudo journalctl -u cloud-agent -f
```

## API
Servise yalnızca SSH local port forwarding üzerinden erişin. Portu güvenlik duvarında veya Azure NSG'de internete açmayın.
```bash
ssh -L 18765:127.0.0.1:8765 user@vm
```
Authorization: Bearer <CLOUD_AGENT_TOKEN> başlığı kullanılır. Token /etc/cloud-agent/agent.env dosyasındadır; Android entegrasyon aşamasında güvenli aktarım ve saklama eklenecektir.

- GET /health
- GET /tasks
- GET /tasks/<task-id>
- POST /tasks body: {"request":"Disk kullanımını incele"}
- POST /tasks/<task-id>/approve body: {"action_index":0}
- POST /tasks/<task-id>/reject body: {"action_index":0}

## Onay akışı
1. İstek OpenAI'ye gönderilir.
2. Öneriler pending_approval olarak SQLite'a yazılır.
3. Kullanıcı her öneriyi ayrı ayrı onaylar veya reddeder.
4. Yalnızca onaylanan sabit allowlist eylemi yürütülür.
5. Sonuç görev kaydına yazılır.

Bu sürüm keyfi terminal komutu, dosya düzenleme/silme, servis yeniden başlatma, paket kurma veya GitHub yazma yetkisi vermez. Bu yetenekler ayrı allowlist ve onay ekranı eklenmeden açılmayacaktır.
