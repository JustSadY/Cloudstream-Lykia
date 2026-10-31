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

Durum sütunu, [test aracıyla](#-test-aracı) 26-31 Ekim 2026'da yapılan uçtan uca denemeyi gösterir: ana sayfa → arama → detay → kaynak bağlantıları → bağlantıya gerçek HTTP isteği.

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
| **HDFilmDelisi** | hdfilmdelisi.one | Film | Kendi oynatıcısı (MP4) | ✅ |
| **WebteIzle** | webteizle.info | Film | VidMoly | ✅ (CAPTCHA'lı kaynaklar atlanıyor) |
| **KultFilmler** | kultfilmler.net | Film, Dizi | VidMoly, VidPapi (HLS) | ✅ |
| **DiziYou** | diziyou.one | Dizi | Kendi oynatıcısı (dublaj + orijinal) | ✅ |
| **YabanciDizi** | yabancidizi.news | Dizi, Film | VidMoly, ok.ru, MolyStream | ⚠️ Liste/detay ✅, oynatma telefonda WebView ile |
| **AsyaWatch** | asyawatch.com | Dizi, Film | FourPichive (HLS) | ✅ |
| **DramaDizilerim** | dramadizilerim.com | Asya Dizisi | Embed HLS/MP4 | ✅ |
| **StarTV** | startv.com.tr | Dizi | Kendi oynatıcısı (HLS) | ✅ |
| **ShowTV** | showtv.com.tr | Dizi | Kendi oynatıcısı (HLS) | ✅ |
| **KanalD** | kanald.com.tr | Dizi | Kendi oynatıcısı (HLS) | ✅ |
| **TV8** | tv8.com.tr | Dizi, Program | Kendi oynatıcısı (HLS, MP4) | ✅ |
| **NowTV** | nowtv.com.tr | Dizi, Program | Kendi oynatıcısı (HLS) | ✅ |
| **Izle7** | izle7.com | Dizi, Film | Dailymotion (HLS) | ✅ |
| **TV2** | tv2.com.tr | Dizi, Film, Program | Kendi oynatıcısı (HLS) | ✅ |
| **TV100** | tv100.com | Haber, Video | JSON-LD `VideoObject` (MP4) | ✅ |
| **Beyaz TV** | beyaztv.com.tr | Program | Dailymotion (HLS) | ✅ |
| **HaberTürk** | haberturk.com | Haber, Spor, Video | JSON-LD `VideoObject` (MP4/HLS) | ✅ |
| **CNN Türk** | cnnturk.com | Haber, Spor, Video | `cnnvod.duhnet.tv` (HLS) | ✅ |
| **Haber Global** | haberglobal.com | Haber, Video | Dailymotion (HLS) | ✅ |
| **Bloomberg HT** | bloomberght.com | Haber, Ekonomi, Program | `hope-video` (MP4) | ✅ |
| **Tele1** | tele1.com.tr | Haber, Video | Dailymotion (HLS) | ✅ |
| **TRT Canlı** | trt.com.tr | Canlı TV | Resmî TRT HLS | ✅ |
| **KRT TV** | krttv.com.tr | Haber, Video | `bilgin.io` (MP4) | ✅ |
| **Ülke TV** | ulketv.com | Haber, Video | `tevideo.org` (MP4) | ✅ |
| **TGRT Haber** | tgrthaber.com | Haber, Video | `v.tgrthaber.com` (MP4) | ✅ |
| **TVNET** | tvnet.com.tr | Canlı TV | Resmî TVNET HLS | ✅ |
| **Kanal 7 Canlı** | kanal7.com | Canlı TV | Resmî Kanal 7 HLS | ✅ |
| **Yirmidört TV** | yirmidort.tv | Haber, Video | `yirmidorthaber.com` (MP4) | ✅ |
| **İnternet Haber** | internethaber.com | Haber, Video | `v.internethaber.com` (MP4) | ✅ |
| **Milliyet TV** | milliyet.com.tr | Haber, Video | Dailymotion (HLS) | ✅ |
| **Cumhuriyet TV** | cumhuriyet.com.tr | Haber, Video | Dailymotion (HLS) | ✅ |
| **Hürriyet TV** | hurriyet.com.tr | Haber, Spor, Video | Dailymotion (HLS) | ✅ |
| **DiziMom** | dizidad.com | Dizi | HDPlayerSystem (HLS) | ✅ |
| **SezonlukDizi** | sezonlukdizi.cc | Dizi | Byse/Filemoon (HLS) | ✅ |
| **SezonlukFilm** | sezonlukdizi.one | Film | VideoSeyred (HLS) | ✅ |
| **TvDiziler** | tvdiziler.tv | Dizi | Kendi oynatıcısı (MP4) | ✅ |
| **DDizi** | ddizi.tel | Dizi | Kendi oynatıcısı (HLS) | ✅ |
| **AtvDizi** | atv.com.tr | Dizi | Kendi oynatıcısı (MP4) | ✅ |
| **DiziBal** | dizibal.org | Dizi, Film, Anime | Pilavyer (HLS) | ✅ |
| **DiziBol** | dizibol.org | Dizi, Film, Anime | Pilavyer (HLS) | ✅ |
| **DiziRella** | dizirella.one | Dizi | Kendi oynatıcısı (HLS) | ✅ |
| **DiziRoll** | diziroll.club | Dizi | FourPichive, Pichive (HLS) | ✅ |
| **Dizigecesi** | dizigecesi.com | Dizi, Film | VidMoly, P2P (HLS) | ✅ |
| **SelcukFlix** | selcukflix.app | Dizi, Film | Pichive (HLS) | ✅ |
| **KoreanTurk** | koreanturk.tv | Asya Dizisi, Film | Kendi oynatıcısı (HLS) | ✅ |
| **SetFilmIzle** | setfilmizle.ltd | Film, Dizi | SetPlay (HLS) | ✅ |
| **WebDramaTurkey** | webdramaturkey2.com | Asya Dizisi, Film | dtpasn.asia (HLS) | ✅ |
| **Tabii** | tabii.com | Dizi, Film, Belgesel | Resmî DASH + HLS | ✅ |
| **TRT Belgesel** | trtbelgesel.com.tr | Belgesel, Dizi, Canlı | Resmî YouTube + HLS | ✅ |
| **AnimeciX** | animecix.tv | Anime | TauVideo | ✅ |
| **Animeler** | animeler.live | Anime | Kendi oynatıcısı (HLS) | ✅ |
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
> **TvDiziler:** Oynatıcı yapılandırması geçersiz JavaScript içerebildiği için (`safarihlsjs: isTVDevice ? true : undefined`) JSON yerine alanlar regex ile okunuyor. `video.twimg.com` adresleri `Referer` başlığıyla 403 verdiğinden bu kaynaklarda referer boş bırakılıyor. Site `tvdiziler.tv` alan adına taşındı; kaçışlı (`\/`) video adresleri çözülüyor.
>
> **DDizi:** `ddizi.im` adresi `ddizi.tel`e yönlendiği ve POST aramasında form verisi kaybolduğu için `mainUrl` doğrudan `ddizi.tel`. Twitter CDN kaynaklarında referer gönderilmiyor.
>
> **AtvDizi:** ATV'nin resmî sitesindeki ücretsiz dizi bölümleri. Bölüm sayfalarındaki JSON-LD `VideoObject` kaydından doğrudan MP4 adresi alınıyor; fragmanlar atlanıyor.
>
> **DiziBal / DiziBol:** Aynı Tailwind tabanlı platformun iki ayrı alan adı. Katalog `/series`, `/movie` ve `/episode` yollarından, oynatma ise Pilavyer HLS akışından okunuyor.
>
> **DiziRella:** Bölüm sayfasındaki `ksdpictures.site/dzembed.php` oynatıcısından `SOURCE` m3u8 adresi ve Türkçe altyazı izleri alınıyor.
>
> **SetFilmIzle:** Site `setfilmizle.ltd` adresine taşındı; film ve diziler SetPlay HLS kaynağından oynatılıyor.
>
> **KoreanTurk / WebDramaTurkey:** Kore ve Asya dizileri; bölüm sayfalarındaki gömülü oynatıcıdan HLS adresi çözülüyor. WebDramaTurkey'de `dtpasn.asia` kaynakları kullanılıyor.
>
> **Tabii:** TRT'nin resmî dijital platformu. Katalog ve bölüm listeleri `__NEXT_DATA__` üzerinden okunuyor; akışlar DASH/HLS olarak veriliyor. Bazı yapımlar Widevine DRM korumalıdır ve oynatma desteği uygulamanın Widevine desteğine bağlıdır.
>
> **TRT Belgesel:** Resmî sitedeki programlar ve bölümler; bölüm videoları TRT Belgesel'in resmî YouTube kanalından, canlı yayın ise TRT'nin resmî HLS akışından alınıyor. Video indirilmiyor veya yeniden yayınlanmıyor.
>
> **HDFilmDelisi:** Next.js/RSC tabanlı film sitesi; detay ve embed adresleri RSC çıktısından, oynatma ise doğrudan MP4 kaynağından alınıyor.
>
> **DiziRoll:** Yabancı dizi arşivi; site içi arama uç noktası yetkisiz yanıt verdiği için arama katalog süzgeciyle yapılıyor.
>
> **SezonlukFilm:** Film arşivi; `videoseyred.in` oynatıcısının JSON listesinden HLS adresi çözülüyor, medya isteğine `Accept: */*` ekleniyor.
>
> **Dizigecesi:** Bölüm sayfasındaki `data-embed` kimlikleri `/ajax/embed` üzerinden çözülüyor; VidMoly ve P2P kaynakları destekleniyor.
>
> **Animeler:** Next.js tabanlı anime sitesi; katalog `article.mac`, arama `/api/search`, oynatma `window.__PLAYER_BOOT__` master adresinden okunuyor.
>
> **ShowTV:** Bölüm listesi `/sitemap_<dizi>.xml` dosyasından; oynatma bölüm sayfasındaki oynatıcı JSON'undan (m3u8/mp4) alınıyor.
>
> **KanalD:** Arama `/actions/search` JSON uç noktasından; bölümler `/<dizi>/bolumler` sayfalarından, oynatma JSON-LD `contentUrl` alanından okunuyor.
>
> **TV8:** Katalog `/programlar` sayfasından; bölümler program sayfasındaki `Tüm Bölümler` sekmesinden, oynatma bölüm sayfasındaki imzalı `videocdn.tv8.com.tr` HLS akışından (yoksa JSON-LD `contentURL` MP4'ünden) alınıyor. Site araması Google CSE kullandığı için arama katalog üzerinden süzülüyor.
>
> **NowTV:** NOW'un resmî sitesi. Dizi ve program bölümleri `/ajax/videos`, oynatma ise `/ajax/stream` uç noktasından alınıyor; arama `POST /arama` ile yapılıyor. Film sayfaları yalnızca fragman (Dailymotion) sunduğu için kapsam dışı bırakıldı.
>
> **Izle7:** Kanal 7'nin VOD portalı. Diziler `/kanal7/diziler` kategorilerinden, filmler `/kanal7/filmler` sayfasından okunuyor; kategori bölümleri `?page=N` ile sayfalanıyor. Oynatma, bölüm sayfasındaki `embed-<id>` adresinden alınan Dailymotion HLS akışıyla yapılıyor; akış isteğine `Origin`, `Referer`, `Accept` ve eşleşen `dmTs`/`dmV1st` çerezleri ekleniyor.
>
> **TV2:** Eski teve2 sitesi. Dizi, film ve program kategorileri `/diziler`, `/filmler` ve `/programlar` altından; bölümler `.../bolumler?page=N` sayfalarından okunuyor. Oynatma, bölüm sayfasındaki JSON-LD `contentUrl` alanından veya Kanal D embed sayfasından alınan HLS akışıyla yapılıyor; sitedeki `tvS1` yazım hatası `/S1/` yoluna düzeltiliyor.
>
> **TV100:** Resmî kanalın video galerisi. Ana sayfa `/video-galeri?sayfa=N`, arama `/ara?key=...` üzerinden okunuyor; foto galerileri eleniyor. Oynatma, sayfadaki JSON-LD `VideoObject.contentUrl` MP4 adresinden alınıyor.
>
> **Beyaz TV:** Program arşivi `/program` kartlarından, bölümler `/program/<program>/<bölüm>` sayfalarından okunuyor; arama program adları üzerinden yerel olarak yapılıyor. Oynatma, sayfadaki `video: "<id>"` Dailymotion kimliğinin metadata API'sinden çözülen HLS akışıyla yapılıyor; akış isteğine `Origin`, `Referer`, `Accept` ve `dmTs`/`dmV1st` çerezleri ekleniyor.
>
> **HaberTürk:** Video kategorileri `/video`, `/video/haber`, `/video/spor`, `/video/ekonomi` ve `/video/magazin` sayfalarından okunuyor; arama bu listeler yerel olarak süzülerek yapılıyor. Oynatma, sayfadaki JSON-LD `VideoObject.contentUrl` adresinden (MP4 veya HLS) alınıyor.
>
> **CNN Türk:** Video galerisi `/videolar/` sayfasından okunuyor; arama video listesi yerel olarak süzülerek yapılıyor. Oynatma, sayfadaki `cnnvod.duhnet.tv` m3u8 adresinden alınıyor; HTTP verilen adresler HTTPS'e çevriliyor.
>
> **Haber Global:** Video galerisi `/video-galeri` ve `/video-galeri/<sayfa>` sayfalarından okunuyor; arama `/arama?key=...` sonuçlarından yalnızca `VideoObject` içeren sayfalar süzülerek alınıyor. Oynatma, sayfadaki Dailymotion kimliğinin metadata API'sinden çözülen HLS akışıyla yapılıyor; akış isteğine `Origin`, `Referer`, `Accept` ve `dmTs`/`dmV1st` çerezleri ekleniyor.
>
> **Bloomberg HT:** `/video` sayfasındaki son videolar ve `/arama?q=...` sonuçları okunuyor. Oynatma, video sayfasındaki JSON-LD `VideoObject.embedUrl` üzerinden açılan `/video/embed/<id>` sayfasındaki `data-hope-video` JSON'undan en yüksek çözünürlüklü MP4 olarak alınıyor.
>
> **Tele1:** `/video` sayfasındaki haber videoları okunuyor; arama bu liste üzerinden yerel olarak yapılıyor. Oynatma, sayfadaki `data-video` Dailymotion kimliğinin metadata API'sinden çözülen HLS akışıyla yapılıyor; YouTube'a gömülü videolar kapsam dışı bırakılıyor.
>
> **TRT Canlı:** TRT 1, TRT 2, TRT Haber, TRT Spor, TRT Spor Yıldız, TRT Belgesel, TRT Çocuk, TRT Müzik, TRT Avaz, TRT Kurdî, TRT Arabi ve TRT World'ün resmî `medya.trt.com.tr` HLS akışları tek listede sunuluyor. Kanal listesi statiktir; arama kanal adına göre yapılır.
>
> **KRT TV:** `/video` ve `/video?sayfa=N` galerisi okunuyor; arama `/arama?key=...` sonuçlarındaki adayların detay sayfaları taranarak yalnızca video içerenler alınıyor. Site araması boş dönerse video galerisinde yerel arama yapılıyor. Oynatma, haber sayfasındaki `krttv-v.bilgin.io` MP4 adresinden.
>
> **Ülke TV:** `/video` galerisindeki haber videoları okunuyor; site araması bulunmadığı için arama video galerisi üzerinden yerel olarak yapılıyor. Oynatma, video sayfasındaki JSON-LD `VideoObject` kaydından veya `vidyome` embed sayfasından alınan `tevideo.org` MP4 adresiyle yapılıyor.
>
> **TGRT Haber:** Gündem, dünya, ekonomi, spor ve magazin video kategorileri okunuyor; arama bu kategorilerde yerel olarak yapılıyor. Oynatma, haber sayfasındaki `v.tgrthaber.com` MP4 adresinden.
>
> **TVNET:** TVNET'in resmî `mncdn.com` HLS canlı yayın akışı tek kanal olarak sunuluyor. Kanal listesi statiktir.
>
> **Kanal 7 Canlı:** Kanal 7 ve Kanal 7 Avrupa'nın resmî HLS canlı yayınları tek listede sunuluyor. Kanal listesi statiktir; arama kanal adına göre yapılır.
>
> **Yirmidört TV:** `/video` galerisi ve `/arama-sonuclari?q=...` araması okunuyor; oynatma, video sayfasındaki `beethoven-cdn.yirmidorthaber.com` MP4 adresinden.
>
> **İnternet Haber:** `/video-galeri?page=N` galerisi ve `/arama?key=...` araması okunuyor; oynatma, video sayfasındaki JSON-LD `VideoObject` kaydından alınan `v.internethaber.com` MP4 adresinden.
>
> **Milliyet TV:** `/video` galerisindeki haber videoları okunuyor; site araması statik sonuç vermediği için arama galeri üzerinden yerel olarak yapılıyor. Oynatma, video sayfasındaki `dailymotion_player_<id>` kimliğinin metadata API'sinden çözülen HLS akışıyla yapılıyor; akış isteğine `Origin`, `Referer`, `Accept` ve `dmTs`/`dmV1st` çerezleri ekleniyor.
>
> **Cumhuriyet TV:** `/video` galerisindeki haber videoları okunuyor; site araması statik sonuç vermediği için arama galeri üzerinden yerel olarak yapılıyor. Oynatma, video sayfasındaki JSON-LD `VideoObject.contentUrl` Dailymotion kimliğinin metadata API'sinden çözülen HLS akışıyla yapılıyor; akış isteğine `Origin`, `Referer`, `Accept` ve `dmTs`/`dmV1st` çerezleri ekleniyor.
>
> **Hürriyet TV:** `/video` ve `/sporarena/video-galeri/` kategorilerindeki videolar okunuyor; arama bu iki liste üzerinden yerel olarak yapılıyor. Oynatma, video sayfasındaki `data-videoid` Dailymotion kimliğinin metadata API'sinden çözülen HLS akışıyla yapılıyor; akış isteğine `Origin`, `Referer`, `Accept` ve `dmTs`/`dmV1st` çerezleri ekleniyor.
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
- Yeni eklentiler: AtvDizi, DiziBal, DiziBol, DiziRella, SelcukFlix, KoreanTurk, SetFilmIzle, WebDramaTurkey, Tabii ve TRT Belgesel. KoreanTurk ve SetFilmIzle güncel alan adlarıyla geri eklendi. CizgiMax, DiziBox, DiziKorea, HDFilmIzle ve Anizm tarama veya oynatma adımlarını geçemediği için eklenmedi.
- Yeni eklentiler: HDFilmDelisi, DiziRoll, SezonlukFilm, Dizigecesi, Animeler, ShowTV, KanalD ve TV8. ShowTV bölüm listesini sitemap üzerinden, KanalD aramayı `/actions/search` uç noktası üzerinden okuyor; DiziRoll ve TV8 araması katalog süzgeciyle çalışıyor. DiziIzle yalnızca YouTube kaynağı sunduğu için eklenmedi.
- Yeni eklentiler: NowTV ve Izle7. NowTV dizi/program bölümlerini `/ajax/videos` ve `/ajax/stream` üzerinden, Izle7 ise Kanal 7 kategorilerini ve Dailymotion HLS akışlarını okuyor. NowTV filmleri yalnızca fragman olarak yayınlandığı için kapsam dışı bırakıldı.
- Yeni eklenti: TV2. Dizi, film ve program arşivi; bölümler `/bolumler?page=N`, oynatma ise JSON-LD `contentUrl` veya Kanal D embed sayfasındaki `duhnet.tv` HLS akışı üzerinden okunuyor.
- Yeni eklentiler: TV100 ve Beyaz TV. TV100 video galerisini JSON-LD `VideoObject` MP4 adresleriyle, Beyaz TV ise program arşivini Dailymotion HLS akışlarıyla okuyor. Her iki eklenti de test aracında ana sayfa, arama, detay, kaynak ve oynatma adımlarını geçti.
- Yeni eklenti: HaberTürk. Video kategorileri ve JSON-LD `VideoObject` akışları okunuyor; arama video listeleri üzerinden yerel olarak yapılıyor. Test aracında tüm adımlar geçti.
- Yeni eklenti: CNN Türk. `/videolar/` galerisi ve `cnnvod.duhnet.tv` HLS akışları okunuyor; arama video listesi üzerinden yerel. Test aracında tüm adımlar geçti.
- Yeni eklenti: Haber Global. `/video-galeri` sayfaları ve JSON-LD `VideoObject` içindeki Dailymotion kimlikleri okunuyor; arama sonuçları video içeren sayfalara süzülüyor. Test aracında tüm adımlar geçti.
- Yeni eklentiler: Bloomberg HT ve Tele1. Bloomberg HT videoları `data-hope-video` JSON'undaki MP4 adreslerinden, Tele1 ise `data-video` Dailymotion kimliklerinden çözülüyor. İki eklenti de test aracında tüm adımları geçti.
- Yeni eklenti: TRT Canlı. TRT'nin 12 resmî kanalı `medya.trt.com.tr` HLS akışlarıyla tek listede sunuluyor. Test aracında tüm adımlar geçti.
- Yeni eklenti: KRT TV. `/video` galerisi ve `/arama?key=...` sonuçları okunuyor; video içeren sayfalar JSON-LD `VideoObject` ile süzülüyor, oynatma `bilgin.io` MP4 adresinden yapılıyor. Test aracında tüm adımlar geçti.
- Yeni eklenti: Ülke TV. `/video` galerisi okunuyor; arama galeri üzerinden yerel. Oynatma, video sayfasındaki JSON-LD `VideoObject` kaydından alınan `tevideo.org` MP4 adresinden yapılıyor. Test aracında tüm adımlar geçti.
- Yeni eklenti: TGRT Haber. Gündem, dünya, ekonomi, spor ve magazin video kategorileri okunuyor; arama kategorilerde yerel. Oynatma, `v.tgrthaber.com` MP4 adresinden. Test aracında tüm adımlar geçti.
- Yeni eklenti: TVNET. Resmî `mncdn.com` HLS canlı yayın akışı tek kanal olarak sunuluyor. Test aracında tüm adımlar geçti.
- Yeni eklenti: Kanal 7 Canlı. Kanal 7 ve Kanal 7 Avrupa'nın resmî HLS canlı yayınları tek listede sunuluyor. Test aracında tüm adımlar geçti.
- Yeni eklenti: Yirmidört TV. `/video` galerisi ve `/arama-sonuclari?q=...` araması okunuyor; oynatma `yirmidorthaber.com` MP4 adresinden. Test aracında tüm adımlar geçti.
- Yeni eklenti: İnternet Haber. `/video-galeri?page=N` galerisi ve `/arama?key=...` araması okunuyor; oynatma `v.internethaber.com` MP4 adresinden. Test aracında tüm adımlar geçti.
- Yeni eklenti: Milliyet TV. `/video` galerisi okunuyor, arama galeri üzerinden yerel; oynatma `dailymotion_player_<id>` kimliğinden Dailymotion HLS ile. Test aracında tüm adımlar geçti.
- Yeni eklenti: Cumhuriyet TV. `/video` galerisi okunuyor, arama galeri üzerinden yerel; oynatma JSON-LD `VideoObject.contentUrl` Dailymotion kimliğinden HLS ile. Test aracında tüm adımlar geçti.
- Yeni eklenti: Hürriyet TV. `/video` ve `/sporarena/video-galeri/` kategorileri okunuyor, arama yerel; oynatma `data-videoid` Dailymotion kimliğinden HLS ile. Test aracında tüm adımlar geçti.
- NTV denendi; sayfadaki m3u8 adresinin jetonu süresi geçmiş olarak (`410 Gone`) döndüğü için eklenmedi.
- DDizi, DiziGom, JetFilmizle ve TvDiziler oynatma hataları giderildi. DDizi kaçışlı video adreslerini çözüyor; DiziGom güncel `pilavyerplay` alan adını kullanıyor; JetFilmizle ölü kaynakları atlayıp tek tırnaklı VidMoly akışını doğrudan okuyor; TvDiziler `tvdiziler.tv` alan adına taşındı.
- FilmKovasi, FullHDFilmIzlede ve WFilmIzle denendi; detay sayfaları/oynatıcıları güvenilir çalışmadığı için eklenmedi.
- Dizipod, Dizilla, DiziPal, FilmModu ve JetFilmizle; Dizilla Next.js/JSON-LD ve Pichive oynatıcısına, JetFilmizle yeni videopark HLS akışına, DiziPal/Dizipod güncel oynatıcı adreslerine göre yenilendi.
- FilmMakinesi büyük ölçüde yeniden yazıldı; FullHDFilmizlesene, HDFilmCehennemi, DiziYou, WebteIzle, AnimeciX, YouTube ve CanliTV güncel sitelere uyarlandı.
- ok.ru çözücüsü yeni sayfa kodlamasına uyarlandı, tek tırnaklı yeni sayfayı okuyan VidMoly çözücüsü eklendi, karıştırılmış oynatıcılar için `OynaticiCozucu` (Rhino) eklendi.
- Kapanan, park edilen, bot/CAPTCHA korumasına geçen ya da baştan yazılması gereken siteler çıkarıldı (CizgiMax, DiziBox, DiziKorea, FullHDFilm, GolgeTV, InatBox, IzleAI, NetflixMirror, RecTV, SinemaCX, SineWix, SuperFilmGeldi, TurkAnime, UgurFilm, Watch2Movies, HDFilmIzle, Anizm) ve yetişkin içerik eklentileri hiç alınmadı.
- CI: derleme sonuçlarını `builds` dalına yükleme açıldı; alan adı kontrolü GitHub sunucusunda ve ek paket gerektirmeden çalışıyor.

Tam liste için `git log`.

## 🎁 Teşekkürler

- [recloudstream/cloudstream](https://github.com/recloudstream/cloudstream) ve [recloudstream/TestPlugins](https://github.com/recloudstream/TestPlugins)

## 🌐 Telif Hakkı ve Lisans

- Bu depodaki değişiklikler ve yeni eklentiler: _Copyright (C) 2026 by_ [JustSadY](https://github.com/JustSadY)
- [GNU GENERAL PUBLIC LICENSE Version 3, 29 June 2007](LICENSE) koşullarına göre lisanslanmıştır.
