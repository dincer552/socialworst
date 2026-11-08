# Gizlilik Politikası — SSH Client

Son güncelleme: 8 Kasım 2026

Bu politika, `com.dincer552.hattrickssh` Android uygulamasının mevcut kod davranışını açıklar. Uygulama reklam veya analiz SDK'sı kullanmaz; uygulama geliştiricisine analitik/telemetri göndermez ve uygulama hesabı oluşturmaz.

## Cihazda saklanan bilgiler

Kullanıcının oluşturduğu sunucu profilleri; sunucu adı, host/IP, port, kullanıcı adı, kimlik doğrulama tercihi, açıklama, Cloudflare Access tercihi ve parola kimlik doğrulaması seçildiyse SSH parolasını içerir. Profil verisi uygulamanın özel dosya alanında AES-GCM ile şifrelenir. Şifreleme anahtarı Android Keystore içinde üretilir ve uygulama tarafından dışarı aktarılamaz.

Kullanıcının Android dosya seçicisinden seçtiği SSH private key, uygulamaya aktarılır ve aynı Android Keystore tabanlı şifreli depolamada profil kimliğiyle ilişkilendirilir. Anahtar veya parola GitHub'a, uygulama geliştiricisine ya da bir analiz hizmetine gönderilmez. SSH kimlik doğrulaması sırasında seçilen sunucuya iletilir.

SSH sunucusu host key parmak izleri de uygulama özel alanında şifrelenmiş olarak saklanır. Cloudflare Access kullanılırsa `cloudflared` erişim durumu ve yapılandırması uygulamanın özel dosya alanında tutulur; bu Cloudflare verileri ayrıca AES-GCM ile şifrelenmiş olarak tanımlanmamıştır.

## Ağ bağlantıları ve üçüncü taraflar

- SSH bağlantısı, yalnızca kullanıcının profilde belirttiği sunucuya ve porta kurulur. Bağlantı içeriği SSH protokolü üzerinden gider.
- Cloudflare Access isteğe bağlıdır. Açıldığında kimlik doğrulaması Cloudflare hizmetleri ve kullanıcının tarayıcısı üzerinden tamamlanır; SSH trafiği yapılandırılmış Cloudflare tünelinden taşınır.
- GitHub APK dağıtımındaki sürüm kontrolü, herkese açık GitHub Releases API'sine istek gönderir ve kullanıcı güncellemeyi seçerse imzalı APK'yi GitHub'dan indirir. İstek GitHub'a standart ağ bağlantısı bilgilerini (ör. IP adresi) açıklar.
- Google Play dağıtımında sürüm kontrolü/güncelleme Google Play In-App Updates API'siyle yapılır. Bu sürüm uygulama içinden APK indirme veya kurma izni istemez.

Uygulama geliştiricisinin sunucusu üzerinden profil, özel anahtar, parola veya terminal içeriği toplanmaz. Uygulamada reklam gösterilmez ve reklam/analiz amacıyla veri paylaşımı yapılmaz.

## Saklama, silme ve yedekleme

Profiller ve kimlik bilgileri kullanıcı profili silene, uygulama verisini temizleyene veya uygulamayı kaldırana kadar cihazda kalır. Android uygulama yedeklemesi kapalıdır; bulut yedekleme ve cihaz aktarımı kuralları uygulama verilerinin tamamını hariç tutar. Bu nedenle uygulama kaldırılırsa veya cihaz değiştirilirse bu bilgiler otomatik olarak geri yüklenmez.

## Çocukların gizliliği

Uygulama çocuklara yönelik değildir ve çocuklardan bilerek kişisel bilgi toplamaz.

## Değişiklikler ve iletişim

Politika değişiklikleri bu dosyada yayımlanır. Google Play listelemesi tamamlanmadan önce uygulama geliştiricisinin destek/gizlilik iletişim adresi bu bölüme ve Play Console'a eklenmelidir.
