# Sesli Planlayıcı

Gününü sesli anlat, gerisini planlayıcı halletsin.

Sesli Planlayıcı, günlük planı konuşarak oluşturan bir Android uygulaması. Kullanıcı gününü
anlatır, konuşma metne çevrilir, yapay zeka metinden görevleri çıkarır. Kullanıcı görevleri onaylar
ya da düzeltir. Uygulama gün boyunca hatırlatır, gün sonunda da günü birlikte değerlendirir.

> **Durum:** Erken geliştirme (Faz 0, prototip). Henüz kurulabilir bir sürüm yok.

## Nasıl çalışır

```
Konuş → metne çevir → AI görevleri çıkarır → onayla / düzelt → hatırlatmalar → gün sonu
```

1. **Bugün:** günün görevleri (sabah / öğleden sonra / akşam / saatsiz) ve "Gününü anlat" butonu
2. **Ses kaydı:** cihazdaki Türkçe konuşma tanıma, canlı metin
3. **Onay kartları:** AI'ın bulduğu görevler. Gün söylenmediyse takvimden seçilir, saat isteğe bağlıdır.
4. **Gün sonu:** önce tamamlananlar, sonra kalanlar için "Yarına taşı / Bırak"
5. **Bildirimler:** az ve anlamlı. Planlama hatırlatması, sabah özeti, görev hatırlatması, gün sonu.

Ekran tasarımları: [`docs/design/`](docs/design/) (tarayıcıda açılabilir).

## Gizlilik ilkeleri

- Ses kaydı saklanmaz; yalnızca metin işlenir, o da görevler oluşturulduktan sonra silinir.
- Görevler yalnızca cihazda tutulur; hesap ya da bulut senkronizasyonu yok.
- AI API anahtarı uygulamada bulunmaz; istekler küçük bir proxy üzerinden gider.
- Bildirimler cihazda zamanlanır; sunucu push'u yok.

## Teknoloji

| Alan | Seçim |
|---|---|
| Platform | Android (Kotlin, Jetpack Compose, Material 3) |
| Mimari | Özelliğe göre paketler, `ui / domain / data` katmanları, ViewModel + StateFlow |
| Veri | Room, DataStore |
| DI / JSON | Hilt, kotlinx.serialization |
| Ses → metin | Android `SpeechRecognizer` (tr-TR) |
| Bildirim | AlarmManager + NotificationCompat |
| AI | Proxy üzerinden LLM (sağlayıcı henüz seçilmedi) |

## Geliştirme

Gerekenler: Android Studio (güncel sürüm, JDK 21 ile gelir) ve tercihen gerçek bir Android cihaz.
Türkçe ses tanıma emülatörde güvenilir çalışmayabilir.

```bash
./gradlew assembleDebug        # derleme
./gradlew testDebugUnitTest    # birim testler
./gradlew lint                 # lint
```

`local.properties` ve `.env` dosyaları repoya girmez; proxy adresi ve benzeri ayarlar bu dosyalarda tutulur.

## Yol haritası

- [ ] **Faz 0:** Prototip (ses → metin → AI → ham JSON)
- [ ] **Faz 1:** Onay kartları, yerel veritabanı, Bugün ekranı
- [ ] **Faz 2:** Bildirimler
- [ ] **Faz 3:** Gün sonu ve ayarlar
- [ ] **Faz 4:** Alpha (Play kapalı test)
