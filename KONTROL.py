
from cloudscraper import CloudScraper
import os, re


class konsol:
    @staticmethod
    def log(mesaj):
        print(mesaj, flush=True)


class MainUrlUpdater:
    def __init__(self, base_dir="."):
        self.base_dir = base_dir
        self.oturum   = CloudScraper()

    @property
    def eklentiler(self):
        return sorted([
            dosya for dosya in os.listdir(self.base_dir)
                if os.path.isdir(os.path.join(self.base_dir, dosya))
                    and not dosya.startswith(".")
                        and dosya not in {"gradle", "build", "CanliTV", "__Temel", "YouTube"}
        ])

    def _kt_dosyasini_bul(self, dizin, dosya_adi):
        for kok, alt_dizinler, dosyalar in os.walk(dizin):
            if dosya_adi in dosyalar:
                return os.path.join(kok, dosya_adi)

        return None

    @property
    def kt_dosyalari(self):
        return [
            kt_dosya_yolu for eklenti in self.eklentiler
                if (kt_dosya_yolu := self._kt_dosyasini_bul(eklenti, f"{eklenti}.kt"))
        ]

    def _mainurl_bul(self, kt_dosya_yolu):
        with open(kt_dosya_yolu, "r", encoding="utf-8") as file:
            icerik = file.read()
            if mainurl := re.search(r'override\s+var\s+mainUrl\s*=\s*"([^"]+)"', icerik):
                return mainurl[1]

        return None

    def _mainurl_guncelle(self, kt_dosya_yolu, eski_url, yeni_url):
        with open(kt_dosya_yolu, "r+", encoding="utf-8") as file:
            icerik = file.read()
            yeni_icerik = icerik.replace(eski_url, yeni_url)
            file.seek(0)
            file.write(yeni_icerik)
            file.truncate()

    def _versiyonu_artir(self, build_gradle_yolu):
        with open(build_gradle_yolu, "r+", encoding="utf-8") as file:
            icerik = file.read()
            if version_match := re.search(r'version\s*=\s*(\d+)', icerik):
                eski_versiyon = int(version_match[1])
                yeni_versiyon = eski_versiyon + 1
                yeni_icerik = icerik.replace(f"version = {eski_versiyon}", f"version = {yeni_versiyon}")
                file.seek(0)
                file.write(yeni_icerik)
                file.truncate()
                return yeni_versiyon

        return None

    @property
    def mainurl_listesi(self):
        return {
            dosya: self._mainurl_bul(dosya) for dosya in self.kt_dosyalari
        }

    def guncelle(self):
        for dosya, mainurl in self.mainurl_listesi.items():
            eklenti_adi = dosya.split("/")[0]

            print("\n")
            konsol.log(f"[~] Kontrol Ediliyor : {eklenti_adi}")
            try:
                istek = self.oturum.get(mainurl, allow_redirects=True)
                konsol.log(f"[+] Kontrol Edildi   : {mainurl}")
            except Exception as hata:
                konsol.log(f"[!] Kontrol Edilemedi : {mainurl}")
                konsol.log(f"[!] {type(hata).__name__} : {hata}")
                continue

            final_url = istek.url[:-1] if istek.url.endswith("/") else istek.url

            if mainurl == final_url:
                continue

            self._mainurl_guncelle(dosya, mainurl, final_url)

            if self._versiyonu_artir(f"{eklenti_adi}/build.gradle.kts"):
                konsol.log(f"[»] {mainurl} -> {final_url}")


if __name__ == "__main__":
    updater = MainUrlUpdater()
    updater.guncelle()