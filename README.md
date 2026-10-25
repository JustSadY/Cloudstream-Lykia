# ☁️ Cloudstream-Lykia: CloudStream için Türkçe Eklentiler

[![CloudStream Derleyici](https://img.shields.io/github/actions/workflow/status/JustSadY/Cloudstream-Lykia/Derleyici.yml?label=CloudStream%20Derleyici&logo=github)](https://github.com/JustSadY/Cloudstream-Lykia/actions/workflows/Derleyici.yml)

_[CloudStream](https://github.com/recloudstream/cloudstream) (Android) için Türkçe film, dizi, anime ve canlı TV eklentileri._

[Döküman](https://recloudstream.github.io/csdocs/) **━** [CloudStream](https://github.com/recloudstream/cloudstream)

## 💾 Kurulum

1. **[cloudstream/pre-release](https://github.com/recloudstream/cloudstream/releases/tag/pre-release)** adresinden güncel APK'yı indirip kurun.
2. CloudStream'de **Ayarlar → Eklentiler → Depo ekle**'ye girin.
3. `Depo URL'si` alanına şunu yazın ve **Depo ekle**'ye basın:

   ```
   https://raw.githubusercontent.com/JustSadY/Cloudstream-Lykia/master/repo.json
   ```

   _Uygulamanın yüklü olduğu cihazda şu bağlantı da depoyu doğrudan ekler:_ `cloudstreamrepo://raw.githubusercontent.com/JustSadY/Cloudstream-Lykia/master/repo.json`
4. Eklenen **Cloudstream-Lykia** deposuna dokunun, istediğiniz eklentileri indirin.
5. Ana sayfanın üstünden kaynağı (ör. `FilmDozu`) seçin.

> Eklentiler GitHub Actions tarafından derlenip `builds` dalına konur. `repo.json` o daldaki `plugins.json` dosyasını gösterir; yeni sürümler CloudStream'de **Eklentiler → Güncelle** ile gelir.

## 📱 Eklentiler

Durum sütunu, [test aracıyla](#-test-aracı) 26 Ekim 2026'da yapılan uçtan uca denemeyi gösterir: ana sayfa → arama → detay → kaynak bağlantıları → bağlantıya gerçek HTTP isteği.

| Eklenti | Site | Tür | Kaynaklar | Durum |
|---|---|---|---|---|
| **FilmDozu** | filmdozu.com | Film | ok.ru, VidMoly | ✅ |
| **Dizipod** | dizipod.com | Dizi, Film | Kendi oynatıcısı (HLS) | ✅ |
| **Dizilla** | dizilla.now | Dizi | Pichive, FourPichive (dublaj + altyazı) | ✅ |
| **DiziPal** | dizipal3008.com | Dizi, Film | Kendi oynatıcısı (HLS) | ⚠️ Bağlantı ✅, oynatma CDN'i bu ağdan erişilemiyor |
| **DiziGom** | dizigom.news | Dizi, Film | Pilavyer (HLS) | ⚠️ Çalışan başlıklarda ✅, yeni eklenenlerin bir kısmında kaynak yok |
| **FilmModu** | www.filmmodu.one | Film | Kendi oynatıcısı (dublaj + altyazı) | ✅ |
| **JetFilmizle** | jetfilmizle.app | Film | Videopark (HLS), VidMoly, ok.ru | ✅ |
| **FilmMakinesi** | filmmakinesi.to | Film | CloseLoad, Rapid | ✅ |
| **FullHDFilmizlesene** | fullhdfilmizlesene.now | Film | RapidVid | ✅ |
| **HDFilmCehennemi** | hdfilmcehennemi.nl | Film, Dizi | Rapidrame (dublaj + altyazı) | ✅ |
| **WebteIzle** | webteizle.info | Film | VidMoly | ✅ (CAPTCHA'lı kaynaklar atlanıyor) |
| **KultFilmler** | kultfilmler.net | Film, Dizi | VidMoly, VidPapi (HLS) | ✅ |
| **DiziYou** | diziyou.one | Dizi | Kendi oynatıcısı (dublaj + orijinal) | ✅ |
| **YabanciDizi** | yabancidizi.news | Dizi, Film | VidMoly, ok.ru, MolyStream | ⚠️ Liste/detay ✅, oynatma telefonda WebView ile |
| **AsyaWatch** | asyawatch.com | Dizi, Film | FourPichive (HLS) | ✅ |
| **DramaDizilerim** | dramadizilerim.com | Asya Dizisi | Embed HLS/MP4 | ✅ |
| **StarTV** | startv.com.tr | Dizi | Kendi oynatıcısı (HLS) | ✅ |
| **DiziMom** | dizidad.com | Dizi | HDPlayerSystem (HLS) | ✅ |
| **SezonlukDizi** | sezonlukdizi.cc | Dizi | Byse/Filemoon (HLS) | ✅ |
| **TvDiziler** | tvdiziler.cc | Dizi | Kendi oynatıcısı (HLS) | ✅ |
| **DDizi** | ddizi.tel | Dizi | Kendi oynatıcısı (HLS) | ✅ |
| **AnimeciX** | animecix.tv | Anime | TauVideo | ✅ |
| **RareFilmm** | rarefilmm.com | Film (eski/nadir) | ok.ru | ✅ |
| **BelgeselX** | belgeselx.com | Belgesel | Odnoklassniki (ok.ru) | ✅ |
| **CanliTV** | Türkiye IPTV listesi | Canlı TV | m3u8 | ✅ |
| **YouTube** | Invidious (yt.chocolatemoo53.com) | Video | DASH + yerleşik YouTube çözücüsü | ✅ |

> **DiziPal:** Arama, detay ve kaynak çözümlemesi çalışıyor. Video CDN'i (`*.uk-traffic-076.com`) test makinesinin ağından TCP olarak erişilemediği için son oynatma adımı burada doğrulanamadı; farklı bir ağda/telefonda denenmelidir.
>
> **Dizilla:** Site Next.js'e geçti; ana sayfa, arama ve bölüm listesi JSON-LD üzerinden okunuyor. Cloudflare koruması uygulamadaki `CloudflareKiller` ile aşılır; JVM test aracında bu adım atlanır.
>
> **YabanciDizi:** Site, oynatıcı adresini veren `/api/...` sayfalarını Cloudflare üzerinden tarayıcı olmayan istemcilere boş döndürüyor. Eklenti bu durumda sayfayı CloudStream'in WebView'ında açıp oynatıcıyı yakalıyor; bu adım bilgisayarda denenemediği için telefonda doğrulanmalı.
>
> **DiziGom:** Site Laravel/Tailwind tabanlı yeni bir yapıya geçti; detaylar JSON-LD, arama `/ara?q=`, oynatıcı ise `data-pv` anahtarıyla Pilavyer üzerinden çözülüyor. Yeni eklenen başlıkların bir kısmında oynatıcı henüz yok (`data-player-type="none"`) veya Pilavyer 404 döndürüyor; çalışan başlıklarda (ör. `Reacher`, `Ayakçı`) oynatma doğrulandı.
>
> **KultFilmler:** VidPapi'nin `securedLink` adresi JVM HTTP istemcisine 403 dönüyor; eklenti aynı akışın `videoSource` (`master.txt`) adresini kullanıyor. VidMoly kaynakları da destekleniyor.
>
> **BelgeselX:** Bölüm kaynaklarındaki bozuk `video/belgeselx.php` adresi atlanıyor; çalışan ok.ru (Odnoklassniki) kaynakları kullanılıyor.
>
> **AsyaWatch:** Cloudflare denetimi yanıt gövdesini tüketmeden `peekBody` ile yapılıyor; dizi/film detayları JSON-LD ve site API'sinden okunuyor.
>
> **DramaDizilerim:** Bölüm sayfalarındaki embed adresleri sırayla çözülüyor; `let source = "..."` olarak verilen doğrudan HLS/MP4 adresleri ve altyazı izleri alınıyor.
>
> **StarTV:** Resmî site. Bölüm listesi `/api/tv-series/.../episodes`, oynatma ise `player-assets.dygdigital.com` paketinden alınan çalışma zamanı anahtarıyla `dygvideo.dygdigital.com` üzerinden çözülüyor.
>
> **DiziMom:** Site `dizidad.com` adresine taşındı; "Son Bölümler" kartları bölüm sayfasına gittiği için `load` otomatik olarak dizi detayına yönleniyor. Oynatıcı `hdplayersystem.com` (FirePlayer); `master.txt` adresi `Accept: */*` başlığı olmadan `security error` döndürdüğü için başlık ekleniyor.
>
> **SezonlukDizi:** Alternatiflerin çoğu reCAPTCHA kapısı döndürüyor; çalışan Filemoon/Byse kaynağı kullanılıyor. Byse'nin `/api/videos/<kod>` yanıtındaki AES-256-GCM şifreli `playback` paketi, `key_parts` içinden sürüm numarasına göre seçilen parçalarla çözülüp HLS adresi alınıyor.
>
> **TvDiziler:** Oynatıcı yapılandırması geçersiz JavaScript içerebildiği için (`safarihlsjs: isTVDevice ? true : undefined`) JSON yerine alanlar regex ile okunuyor. `video.twimg.com` adresleri `Referer` başlığıyla 403 verdiğinden bu kaynaklarda referer boş bırakılıyor.
>
> **DDizi:** `ddizi.im` adresi `ddizi.tel`e yönlendiği ve POST aramasında form verisi kaybolduğu için `mainUrl` doğrudan `ddizi.tel`. Twitter CDN kaynaklarında referer gönderilmiyor.
>
> FilmMakinesi ve HDFilmCehennemi, oynatıcıların karıştırılmış (obfuscate) video adresini çözmek için CloudStream uygulamasıyla gelen Rhino JavaScript motorunu kullanır (`OynaticiCozucu.kt`).
>
> Yetişkin içerik eklentisi yoktur.

## 🧪 Test Aracı

`test-araci/`, eklentileri telefona gerek kalmadan, CloudStream'in resmi JVM kütüphanesiyle (`library-jvm`) gerçekten çalıştırır. Sitelerin alan adı veya yapısı değiştiğinde neyin bozulduğunu hızlıca gösterir.

```bash
# Tüm eklentiler (paralel)
JAVA_HOME=/usr/lib/jvm/java-17-openjdk ./gradlew -p test-araci run --continue

# Tek eklenti, ayrıntılı günlükle
VERBOSE=1 ./gradlew -p test-araci :FilmDozu:run

# Belirli bir sayfayı dene (ör. ana sayfadaki ilk içeriğin videosu silinmişse)
./gradlew -p test-araci :YabanciDizi:run -Ppage=https://yabancidizi.news/dizi/lupin-izle-1

# Yalnızca bir oynatıcı adresini çözücüyle dene
./gradlew -p test-araci :FilmDozu:run -Purl='https://vidmoly.net/embed-xxxx.html' -Pref='https://filmdozu.com/'
```

Her eklenti için bir `SONUC|...` satırı yazar: `main` (ana sayfa öğesi), `search`, `load`, `links` (bağlantı sayısı), `play` (ilk bağlantıların HTTP durumu ve türü: `m3u8`, `video`, `dash`) ve varsa hatalar. WebView ve Cloudflare çözümü JVM'de yoktur; bunlara dayanan adımlar telefonda denenmelidir.

## 🛠️ Yerelde Derleme

Gerekenler: **JDK 17** ve **Android SDK** (`platforms;android-35`, `build-tools;34.0.0`).

```bash
# SDK yolu ANDROID_HOME ile ya da local.properties içinde sdk.dir= ile verilir
echo "sdk.dir=$HOME/Android/Sdk" > local.properties

# Tüm eklentileri derle ve plugins.json üret
JAVA_HOME=/usr/lib/jvm/java-17-openjdk ./gradlew make makePluginsJson

# Tek eklenti
./gradlew FilmDozu:make

# USB hata ayıklaması açık telefona doğrudan yükle
./gradlew FilmDozu:deployWithAdb
```

Çıktılar `<Eklenti>/build/<Eklenti>.cs3` ve `build/plugins.json` olarak oluşur.

## ➕ Yeni Eklenti Eklemek

1. `__Temel` klasörünü kopyalayıp yeniden adlandırın (ör. `YeniSite`). `__Temel` ve `test-araci`, `settings.gradle.kts` içinde derleme dışı bırakılmıştır.
2. `build.gradle.kts` içindeki `description`, `tvTypes`, `iconUrl` alanlarını, Kotlin dosyalarında `mainUrl`, `name` ve seçicileri düzenleyin.
3. `./gradlew YeniSite:make` ile derleyin, `./gradlew -p test-araci :YeniSite:run` ile deneyin, `master`'a push edin. CI eklentiyi otomatik yayınlar.

Kökteki her klasör `build.gradle.kts` içeriyorsa otomatik olarak derlemeye dahil edilir.

## ⚙️ Depo Yapısı ve CI

| Dosya | Görevi |
|---|---|
| `repo.json` | CloudStream'e eklenen depo tanımı; `builds` dalındaki `plugins.json`'u gösterir |
| `build.gradle.kts` | Tüm eklentilerin ortak Gradle ayarları (CloudStream gradle eklentisi, Kotlin 2.3, JVM 11) |
| `.github/workflows/Derleyici.yml` | `master`'a her push'ta derler, `.cs3` + `plugins.json` dosyalarını `builds` dalına yükler |
| `.github/workflows/Kontrol.yml` | 9 saatte bir eklenti adreslerini kontrol eder (`KONTROL.py`), yönlendirme varsa adresi güncelleyen bir PR açar |
| `test-araci/` | Eklentileri JVM'de uçtan uca deneyen araç |

GitHub'da bir kez yapılması gerekenler:

- **Settings → Actions → General → Workflow permissions**: _Read and write permissions_ seçili olmalı.
- Alan adı kontrolünün PR açabilmesi için aynı sayfada _Allow GitHub Actions to create and approve pull requests_ işaretlenmeli.
- Depo **herkese açık** olmalı; CloudStream `raw.githubusercontent.com` adreslerini oturum açmadan okur.

## ⚖️ Sorumluluk

Bu depo yalnızca sitelerin herkese açık sayfalarını okuyan eklentiler içerir; hiçbir video barındırmaz. Sitelerin içerik hakları ve kullanımın yasallığı kullanıcının sorumluluğundadır.

## 📝 Değişiklikler

26 Ekim 2026 itibarıyla yapılan değişiklikler:

- Paket adı `com.lykia`; başlıklar sadeleştirildi, hata ayıklama betikleri kaldırıldı.
- Kotlin 2.3.0 ve JVM 11'e geçildi; tüm eklentiler güncel CloudStream API'sine (`newExtractorLink`, `Score`) taşındı.
- Yeni eklentiler: FilmDozu, YabanciDizi (MolyStream çözücüsü dahil).
- Yeni eklentiler: BelgeselX, AsyaWatch, KultFilmler ve DiziGom. DiziGom yeni Laravel/Tailwind sitesine göre baştan yazıldı; KultFilmler'e VidPapi, BelgeselX'e ok.ru desteği eklendi.
- Yeni eklentiler: StarTV, DiziMom, SezonlukDizi, TvDiziler, DDizi ve DramaDizilerim. DiziMom ile SezonlukDizi yeniden yazılarak geri eklendi; SezonlukDizi için Byse (Filemoon) AES-GCM çözücüsü, DiziMom için HDPlayerSystem desteği yazıldı.
- FilmKovasi, FullHDFilmIzlede ve WFilmIzle denendi; detay sayfaları/oynatıcıları güvenilir çalışmadığı için eklenmedi.
- Dizipod, Dizilla, DiziPal, FilmModu ve JetFilmizle; Dizilla Next.js/JSON-LD ve Pichive oynatıcısına, JetFilmizle yeni videopark HLS akışına, DiziPal/Dizipod güncel oynatıcı adreslerine göre yenilendi.
- FilmMakinesi büyük ölçüde yeniden yazıldı; FullHDFilmizlesene, HDFilmCehennemi, DiziYou, WebteIzle, AnimeciX, YouTube ve CanliTV güncel sitelere uyarlandı.
- ok.ru çözücüsü yeni sayfa kodlamasına uyarlandı, tek tırnaklı yeni sayfayı okuyan VidMoly çözücüsü eklendi, karıştırılmış oynatıcılar için `OynaticiCozucu` (Rhino) eklendi.
- Kapanan, park edilen, bot/CAPTCHA korumasına geçen ya da baştan yazılması gereken siteler çıkarıldı (CizgiMax, DiziBox, DiziKorea, FullHDFilm, GolgeTV, InatBox, IzleAI, KoreanTurk, NetflixMirror, RecTV, SetFilmIzle, SinemaCX, SineWix, SuperFilmGeldi, TurkAnime, UgurFilm, Watch2Movies) ve yetişkin içerik eklentileri hiç alınmadı.
- CI: derleme sonuçlarını `builds` dalına yükleme açıldı; alan adı kontrolü GitHub sunucusunda ve ek paket gerektirmeden çalışıyor.

Tam liste için `git log`.

## 🎁 Teşekkürler

- [recloudstream/cloudstream](https://github.com/recloudstream/cloudstream) ve [recloudstream/TestPlugins](https://github.com/recloudstream/TestPlugins)

## 🌐 Telif Hakkı ve Lisans

- Bu depodaki değişiklikler ve yeni eklentiler: _Copyright (C) 2026 by_ [JustSadY](https://github.com/JustSadY)
- [GNU GENERAL PUBLIC LICENSE Version 3, 29 June 2007](LICENSE) koşullarına göre lisanslanmıştır.
