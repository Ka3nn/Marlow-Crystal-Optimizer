# Dokuz Tamamla (Fabric 1.21.1, sadece istemci)

## Kurulum
1. Fabric Loader (1.21.1) + Fabric API kur.
2. `dokuz-tamamla-1.0.0.jar` dosyasını `.minecraft/mods` klasörüne at.

## Jar nasıl alınır (bilgisayara bir şey kurmadan)
1. GitHub'da yeni bir depo aç, bu klasördeki her şeyi yükle (`.github` klasörü dahil).
2. Depoda **Actions** sekmesi → "Modu derle" otomatik çalışır.
3. Bitince çalıştırmanın altındaki **dokuz-tamamla-jar** dosyasını indir; içindeki
   `-sources` olmayan jar senin modun.

Kendi bilgisayarında derlemek istersen: Java 21 + Gradle kur, bu klasörde `gradle build`.

## Kullanım
1. 1'den 9'a kadar map art'ları hotbar'da sırayla slot 1-9'a koy.
2. **P** ile menüyü aç (Social Interactions açılmazsa, örn. tek oyunculu) →
   "Hotbar'ı kaydet".
3. Dropper'ı kendin doldur (her rakamdan 8 tane).
4. Dropper'dan gelen itemler otomatik algılanır (menüde "Gelenler" listesi).
5. 9'a tamamlama tuşuna bas (varsayılan **K**, mod menüsünden değiştirilir): bir sonraki gelen item **sadece senin ekranında**
   `9 - (2. gelen rakam)` olarak görünür. Örnek: 5, 6, 2 geldi → K → sıradaki 3 görünür.
6. O item hotbar, envanter, elde ve item frame'de aynı sahte görünümle kalır.
   Dropper içindeki ve sunucudaki gerçek itemler değişmez.

Mod menüsü sadece P ile açılır ve Kontroller listesinde yer almaz (sunucuda P önce
Social Interactions'ı açar; açılmazsa mod menüsü açılır). 9'a tamamlama tuşu da sadece
mod menüsünden ayarlanır. "Turu sıfırla" tuşu da öyle (varsayılan: yok). Menüde
"Tamamla tuşu" veya "Sıfırla tuşu" butonuna bas, sonra istediğin tuşa bas
(ESC iptal, DELETE tuşu kaldırır). Modun Kontroller listesinde hiçbir tuşu yok.

## Menü
- Hedef: Otomatik (9'a tamamla) ya da elle 1-9 seçimi
- 9'a tamamlanacak item: kaçıncı gelen (varsayılan 2.)
- Bildirim: Açık/Kapalı (ekranda "Hazır → 3" yazısı; kayıtta görünmesin istersen kapat)
- Sonra sıfırla: hile tetiklenince gelenler listesi sıfırlansın
- Turu sıfırla / Görünümleri temizle

## Bilinen sınırlar
- Sahte item, envanterde aynı haritanın gerçek bir kopyasıyla üst üste (stack) binerse
  ikisi birden sahte görünür. Ayrı slotta tut.
- Item frame kırılıp item yerden alınırsa sahte görünüm kalkar.
