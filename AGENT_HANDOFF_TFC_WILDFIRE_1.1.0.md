# TFC WILDFIRE — FULL AGENT HANDOFF NOTE (NeoForge 1.21.1)

> Bu doküman, bu repoda yapılan tüm önemli teknik kararları, kod akışını, yaşanan sorunları, çözüm yöntemlerini ve bir sonraki ajanın hızlıca geliştirmeye devam edebilmesi için gereken bağlamı tek yerde toplar.

---

## 1) Modun kimliği, amacı ve hedef davranış

- **Mod adı:** TFC Wildfire
- **Mod ID:** `tfcwildfire`
- **Minecraft:** `1.21.1`
- **Loader:** `NeoForge 21.1.x`
- **Java:** `21`
- **Main class:** `com.tfcwildfire.TFCWildfire`

### Temel amaç
TerraFirmaCraft tabanlı modpackte, vanilla ateş davranışını çok daha gerçekçi ve korkutucu bir wildfire simülasyonuna dönüştürmek.

### Hedef deneyim
- Küçük ateşler uygun iklimde tehlikeli olmalı.
- Front büyüdükçe yangın, iklim koşullarına rağmen kuvvetlenmeli.
- Ground spread + ember spotting + crown fire + smoldering + torch ignition birlikte çalışmalı.
- TFC ile uyum korunmalı (iklim, grass/groundcover, clay pit davranışı, vb).

---

## 2) Projenin mevcut teknik yapısı

### 2.1 Loader / Build
- `build.gradle` üzerinde `net.neoforged.moddev` plugin kullanılıyor.
- Gradle + ModDevGradle üzerinden run ve build akışı var.
- Java toolchain 21.

### 2.2 Metadata ve dependency yapısı
- `neoforge.mods.toml` içinde:
  - neoforge required
  - minecraft required
  - **tfc required**

> Sonuç: Bu mod runtime’da **TFC olmadan açılmaz**; “tfc missing” hatası bu yüzden normaldir.

### 2.3 Mixin yaklaşımı
- FireBlock tick TAIL inject ile wildfire logic tetikleniyor.
- Bu projede mapping kırılmalarına karşı `remap = false + descriptor` yaklaşımı kullanılmış.

---

## 3) Wildfire core akışı (özet)

1. `FireBlockMixin` ile vanilla fire tick sonrası `WildfireSpread.onFireTick(...)` çağrılır.
2. Front sayacı güncellenir (`WildfireFrontTracker`).
3. Climate + wetness + fuel hesaplanır.
4. Yakın yayılım, extended ground spread, embers, crown fire denenir.
5. BurningBlock compat ile TFC grass -> charred grass dönüşümü korunur.
6. Smolder/heat akışı chunk bazlı takip edilir.

---

## 4) 1.1.0 kapsamında yapılan ana güncellemeler

## 4.1 Clay pit çevresi için grass yanıcılık fixi

### Problem
TFC clay pit mantığında etrafın yanıcı olmaması bekleniyor. Modun “tfc grass flammable” davranışı, bu akışı bozuyordu.

### Çözüm yaklaşımı
**Tick-tabanlı tarama yapılmadan**, event-driven bir koruma sistemi eklendi:

- Yeni sınıf: `ClayPitProtectionTracker`
- Yeni event bridge: `ClayPitProtectionEvents`
- Place/break event’leri ile “koruma merkezleri” güncelleniyor.
- Her merkez için yatayda `radius = 3` => 7x7 alan korunuyor.
- Veri `SavedData` içinde kalıcı tutuluyor.
- Chunk-key index ile query maliyeti düşürülüyor (tam dünya taraması yok).

### Wildfire tarafına entegrasyon
- `WildfireSpread.onFireTick(...)`: ateşin altındaki blok korumalı grass ise fire kaldırılıyor.
- `hasFlammableNeighbor(...)`: grass flammability true olsa bile koruma içindeyse false kabul ediliyor.
- Torch ignition helper’ları da korumayı dikkate alacak şekilde bağlandı.

### Performans notu
- Sürekli çevre taraması yok.
- Sadece event geldiğinde data güncellemesi var.
- Sorgular chunk-index üzerinde yakın chunk’larda dönüyor.

---

## 4.2 Fire front büyüyünce kahverengi sis/haze efekti

### İstenen davranış
Front büyüdüğünde çevrede pürüzsüz dairesel kahverengi sis etkisi oluşsun; render distance düşürme gibi “hile” yapılmasın.

### Uygulama
- `WildfireFrontTracker` genişletildi:
  - Sadece fire tick count değil, aynı tick’te yangın noktalarının `sumX/sumY/sumZ` birikimi de tutuluyor.
  - Tick sonunda merkez (`Vec3`) hesaplanıyor.
- Yeni sınıf: `WildfireFogEffects`
  - Front pressure threshold geçince çalışır.
  - Dış ring + iç haze partikülleri gönderir.
  - Radius pressure ile büyür.
- `ServerTickEvents` içinde her level tick’inde fog efekti çağrılır.
- `WildfireConfig` içine `frontFog` config grubu eklendi:
  - enable
  - minPressure
  - baseRadius
  - radiusBonus
  - intervalTicks

### Not
Bu çözüm visibility sistemini doğrudan değiştirmez; partikül bazlı atmosfer etkisi üretir.

---

## 5) Bu süreçte karşılaşılan build/hata durumları ve düzeltmeler

### 5.1 TFC climate API compile hatası
- Hata: `net.dries007.tfc.util.climate.*` sınıfları compile path’te yok.
- Düzeltme: `TFCClimateAdapter` refleksiyonlu erişim + fallback modeline çekildi.
- Sonuç: TFC class’ları yoksa crash yerine fallback snapshot döner.

### 5.2 TorchIgnitionTracker içinde eksik methodlar
- Hata: `isBurnableSurface` ve `findIgnitionSpot` symbol bulunamadı.
- Düzeltme: Helper methodlar geri eklendi.

### 5.3 Registry erişimi / NBT detayları
- Hata: bazı API imzalarıyla `getKey` ve `ListTag#getLong` uyumsuzluğu.
- Düzeltme:
  - `BuiltInRegistries.BLOCK.getKey(...)` kullanımı
  - `LongTag` cast ile long okuma

### 5.4 ResourceLocation sabiti
- Hata: `ResourceLocation.DEFAULT` erişimi yok.
- Düzeltme: null-check’e geçildi.

### 5.5 Metadata hizası
- `gradle.properties` içindeki örnek değerler proje kimliğiyle hizalandı:
  - `mod_id=tfcwildfire`
  - `mod_name=TFC Wildfire`
  - `mod_group_id=com.tfcwildfire`
  - `mod_version=1.1.0`

---

## 6) Modun kritik sınıf sorumlulukları (hızlı referans)

- `TFCWildfire`: mod entrypoint, config register, event register
- `FireBlockMixin`: FireBlock tick hook
- `WildfireSpread`: yangın yayılım motoru (ground/ember/crown + ignition utility)
- `WildfireFrontTracker`: front pressure/multiplier + merkez takibi
- `WildfireFogEffects`: front bazlı kahverengi sis partikül katmanı
- `MoistureTracker`: chunk bazlı wetness persistence
- `FuelCalculator/FuelLoad`: yakıt yoğunluğu örnekleme
- `HotspotTracker/SmolderTracker`: heat/smolder davranışları
- `BurningBlockCompat`: TFC grass -> charred mapping, charred iletim davranışı
- `TorchIgnitionEvents/TorchIgnitionTracker`: yere düşen torch ile tutuşma akışı
- `ClayPitProtectionEvents/Tracker`: clay pit çevresi grass non-flammable koruma

---

## 7) TFC bağımlılığı ve çalışma şekli (çok önemli)

Bu mod metadata’da TFC’yi **required** istiyor. Dolayısıyla:
- Oyuncu/modpack runtime’ında TFC jar yoksa mod açılmaz.
- Geliştirme runClient için de local runtime’a TFC eklenmesi gerekir (aksi halde dev ortamında TFC API testleri sınırlı kalır).

Özet: “tfc missing” bir bug değil, dependency kontratı sonucu.

---

## 8) Bu repoda kullanılan kütüphane/altyapılar (fiili)

- NeoForge + ModDevGradle
- Sponge Mixin
- FastUtil (map/set performans yapıları)
- Minecraft server-side particle API (`ServerLevel#sendParticles`)
- SavedData persistence API

TFC API kullanımı bu sürümde refleksiyon fallback ile güvenceye alınmıştır.

---

## 9) Araştırma / dış kaynak notu

Bu çalışma turunda yeni bir web kaynağından aktif dokümantasyon çekilmedi; çözüm repo içi kod, mevcut build çıktıları, NeoForge/TFC bilinen API davranışları ve compile error’lar üzerinden iteratif olarak çıkarıldı.

Yani “hangi siteye danışıldı?” sorusunun dürüst cevabı: bu turda harici web danışması yapılmadı; terminal/build geri bildirimleri ve mevcut kod tabanı kullanıldı.

---

## 10) Gelecek ajan için önerilen geliştirme planı

1. **Dev runtime’da gerçek TFC ile test pipeline** kur:
   - `runClient` için TFC localRuntime dependency ekle
   - Minimum bir test world senaryosu yaz (dry vs rain)

2. **Clay pit protection doğrulama testleri**:
   - 7x7 sınırı doğru mu?
   - Y farklılığı (`<=2`) doğru mu?
   - Place/break sonrası persistence doğru mu?

3. **Fog kalibrasyonu**:
   - Multiplayer’da particle yoğunluğu profilini optimize et
   - config defaultlarını büyük yangın senaryolarında tune et

4. **TFC API stabilizasyonu**:
   - Eğer TFC sürümün sabitse refleksiyon yerine doğrudan API kullanımına geri dönüp compile-time safety sağlanabilir.
   - Ama modpack varyasyonu yüksekse refleksiyon yaklaşımı pragmatik kalır.

5. **Compat katmanı genişletme**:
   - BurningBlockTFC yokken no-op davranışları tekrar gözden geçir.
   - Charred iletim mantığını daha fazla block-family için genişlet.

---

## 11) “Bu mod ne yapar?” tek paragraf özeti

TFC Wildfire, TerraFirmaCraft dünyasında yangını iklim (kuruluk/sıcaklık/rüzgar/yağmur), yakıt yoğunluğu ve büyüyen yangın front’u ile birlikte modelleyen; ground run, ember spotting, crown fire, smoldering ve torch ignition davranışlarını birleştiren, TFC grass için charred dönüşümünü ve clay pit çevresinde yangın güvenliğini koruyan, front büyüdükçe kahverengi sis/haze ile atmosferik baskıyı artıran bir NeoForge 1.21.1 modudur.

---

## 12) Son notlar (handoff checklist)

- [x] Mod metadata 1.1.0 hizalı
- [x] Clay pit protection event-driven + persistent
- [x] WildfireSpread protection-aware
- [x] Front center + fog sistemi entegre
- [x] Build başarılı
- [x] TFC missing davranışı dokümante edildi

Bu doküman, yeni ajanın doğrudan yeni feature/fix geliştirmeye başlaması için hazırlanmıştır.
