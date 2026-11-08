# SSH Client Gizlilik Politikasi

Son guncelleme: 8 Kasim 2026

Bu politika `com.dincer552.hattrickssh` Android uygulamasinin mevcut davranisini aciklar. Uygulama reklam veya analiz SDK'si kullanmaz; gelistiriciye analitik/telemetri gondermez ve uygulama hesabi olusturmaz.

## Cihazda saklanan bilgiler

Sunucu profilleri sunucu adi, host/IP, port, kullanici adi, kimlik dogrulama tercihi, aciklama, Cloudflare Access tercihi ve parola ile kimlik dogrulama secilmisse SSH parolasini icerebilir. Profil verileri uygulamanin ozel dosya alaninda AES-GCM ile sifrelenir. Sifreleme anahtari Android Keystore icinde uretilir ve uygulama tarafindan disari aktarilamaz.

Kullanicinin Android dosya secicisinden sectigi SSH private key uygulamaya aktarilir ve Android Keystore tabanli AES-GCM sifrelemesiyle ilgili profil kimligiyle iliskilendirilerek saklanir. Private key dosyasinin kendisi sunucuya gonderilmez; anahtar SSH kimlik dogrulamasinda cihazda kullanilir. Parola ile kimlik dogrulamasi secilirse parola, SSH baglantisi sirasinda SSH protokolu uzerinden kullanicinin sectigi sunucuya iletilir.

SSH sunucusu host key parmak izleri de uygulamanin ozel alaninda sifrelenmis olarak saklanir. Cloudflare Access kullanilirsa `cloudflared` erisim durumu ve yapilandirmasi uygulamanin ozel alaninda tutulur; bu Cloudflare verileri uygulama tarafindan ayrica AES-GCM ile sifrelenmez.

## Ag baglantilari ve ucuncu taraflar

- SSH baglantisi yalnizca kullanicinin profilde belirttigi sunucuya ve porta kurulur. Secilen sunucu baglanti verilerini ve gonderilen komut/terminal icerigini alabilir. SSH trafigi SSH protokoluyle korunur.
- Cloudflare Access istege baglidir. Etkinlestirildiginde tarayiciyla Cloudflare kimlik dogrulamasi yapilabilir ve SSH trafigi kullanicinin yapilandirdigi Cloudflare Tunnel uzerinden tasinir.
- Uygulama acilista ve kullanici guncelleme kontrolunu sectiginde herkese acik GitHub Releases API'sine baglanir. Kullanici yeni surumu indirmeyi secerse APK GitHub'dan indirilir. Bu ag istekleri GitHub'a olagan baglanti bilgilerini (ornegin IP adresi) aciklar.
- Android APK kurulum ekrani gosterir. Kullanici bilinmeyen kaynaklardan yukleme iznini Android ayarlarindan vermek ve guncellemeyi onaylamak zorunda kalabilir. Android, kurulu uygulamanin imza sertifikasi eslesmeyen APK'nin uzerine kurulum yapilmasina izin vermez.

Gelistiricinin sunuculari profilleri, private key'leri, parolalari veya terminal icerigini toplamaz. Uygulamada reklam gosterilmez ve reklam/analiz amaciyla veri paylasimi yapilmaz.

## Saklama ve yedekleme

Profiller ve kimlik bilgileri kullanici profili silene, uygulama verisini temizleyene veya uygulamayi kaldirana kadar cihazda kalir. Android uygulama yedeklemesi kapalidir; bulut yedekleme ve cihaz aktarimi kurallari uygulama verilerini haric tutar. Uygulama kaldirilirsa veya cihaz degistirilirse bilgiler otomatik olarak geri yuklenmez.

## Iletisim ve politika yayini

Uygulama cocuklara yonelik degildir ve cocuklardan bilerek kisisel bilgi toplamaz. Dagitimdan once gelistirici destek/gizlilik iletisim adresini eklemeli ve bu politikayi herkesin erisebildigi HTTPS kullanan bir web sayfasinda yayinlamalidir.
