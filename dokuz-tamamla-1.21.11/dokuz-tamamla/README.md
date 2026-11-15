# Dokuz Tamamla (Fabric 1.21.11, sadece istemci)

## Kurulum
1. Fabric Loader (1.21.11) + Fabric API kur.
2. `dokuz-tamamla-1.0.0.jar` dosyasını `.minecraft/mods` klasörüne at.

## Jar nasıl alınır (bilgisayara bir şey kurmadan)
1. GitHub'da yeni bir depo aç, bu klasördeki her şeyi yükle (`.github` klasörü dahil).
2. Depoda **Actions** sekmesi → "Modu derle" otomatik çalışır.
3. Bitince çalıştırmanın altındaki **dokuz-tamamla-jar** dosyasını indir; içindeki
   `-sources` olmayan jar senin modun.

Kendi bilgisayarında derlemek istersen: Java 21 + Gradle kur, bu klasörde `gradle build`.

## Kullanım
1. 1'den 9'a kadar map art'ları hotbar'da sırayla slot 1-9'a koy.
2. **P** ile menüyü aç (sunucuda da; Social Interactions yerine mod menüsü açılır) →
   "Hotbar'ı kaydet".
3. Dropper'ı kendin doldur (her rakamdan 8 tane).
4. Dropper'dan gelen itemler otomatik algılanır (menüde "Gelenler" listesi).
5. 9'a tamamlama tuşuna bas (varsayılan **K**, mod menüsünden değiştirilir): bir sonraki gelen item **sadece senin ekranında**
   `9 - (2. gelen rakam)` olarak görünür. Örnek: 5, 6, 2 geldi → K → sıradaki 3 görünür.
6. O item hotbar, envanter, elde ve item frame'de aynı sahte görünümle kalır. Frame'den geri
   alınınca (yere düşüp tekrar alınınca) yine sahte görünür.
7. Dropper ekranında sayılar, eksik olan item seçtiğin rakammış gibi görünür. Sahte itemi
   dropper'a geri koyarsan sayılar normale döner.
8. Sahte item aynı türden gerçek bir itemle aynı slotta birleşirse mod onu otomatik olarak boş bir
   slota ayırır (menüde "Aynıları ayır"). Envanterde en az bir boş slot olmalı.
9. Sıfırlama tuşu (menüden atanır) her şeyi gerçek haline döndürür.
   Dropper içindeki ve sunucudaki gerçek itemler hiçbir zaman değişmez.

Mod menüsü sadece P ile açılır ve Kontroller listesinde yer almaz. P artık Social
Interactions yerine her yerde (tek oyunculu ve sunucu) mod menüsünü açar. 9'a tamamlama tuşu da sadece
mod menüsünden ayarlanır. "Turu sıfırla" tuşu da öyle (varsayılan: yok). Menüde
"Tamamla tuşu" veya "Sıfırla tuşu" butonuna bas, sonra istediğin tuşa bas
(ESC iptal, DELETE tuşu kaldırır). Modun Kontroller listesinde hiçbir tuşu yok.

## 21 Modu (menüde "21 Modu" sekmesi)
1. Kart map art'larını hotbar'a sırayla koy: slot 1 = A (1/11), slot 2-9 = 3, 4, 5, 6, 7, 8, 9, 10.
2. P → "21 Modu" sekmesi → "Hotbar'ı kaydet".
3. 2. kart geldikten sonra 21 tuşuna bas (varsayılan **J**, menüden değiştirilir).
4. Tuşa bastıktan sonra gelen kartlar, **sadece senin ekranında**, sadece 2. kartın üzerine eklenerek
   toplamı tam 21 yapacak şekilde görünür. A her zaman en yüksek değeriyle sayılır. Ara toplam,
   A 1 sayılırken asla 17-20, A 11 sayılırken asla 18-20 olmaz.
   21 olunca mod kendini kapatır; sonraki turda tekrar bas. Kombinasyon her turda rastgele seçilir (A, 1 ya da 11 sayılır).
   Menüde seçilen plan ve kaç farklı yol olduğu görünür.
5. 21 sıfırlama tuşu (menüden atanır) 21 modunda değişen her şeyi gerçek haline döndürür ve turu sıfırlar.
   Hotbar, envanter, el, item frame ve dropper ekranı 9 modundaki gibi çalışır.

## Düello Modu (menüde "Düello" sekmesi)
1. 1. dropper: 1, 2 … 9 adlı itemler (noktasız); 2. dropper: 1.2, 2.2 … 9.2 adlı itemler
   (isimdeki ilk rakam değeridir). Map art veya örs ile isimlendirilmiş herhangi bir item olabilir.
2. 18 itemin hepsi envanterdeyken P → "Düello" → "Envanteri kaydet".
3. Dropper'ları doldururken bir kez açman yeterli; mod slot düzenini hatırlar.
4. ".2 lehine %" ayarı: %0 adil (.2 kazanır %44, berabere %11, kaybeder %44),
   %100'de .2 hiç kaybetmez (kazanır %80, berabere %20). Beraberlik her zaman kazanmadan yavaş artar.
5. Düelloyu aç (varsayılan **L**). İki dropper aynı anda çalışınca her tur için sonuç bu olasılıklarla
   seçilir, değerler rastgele belirlenir ve gelen itemler **sadece senin ekranında** öyle görünür.
6. Hotbar, envanter, el, item frame ve yere atılan item sahte görünür; dropper ekranlarında sahte görünen
   itemin slotu boş/eksik, gerçekte çıkan item yerinde görünür.
7. Düello sıfırlama tuşu (menüden atanır) düellodaki her şeyi gerçek haline döndürür.

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
