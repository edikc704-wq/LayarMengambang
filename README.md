# FloatDock

App Flutter buat bikin garis putih kecil mengambang di tepi kanan layar.
Geser ke kiri (atau tap) buat munculin daftar semua aplikasi di HP, tap
layar kosong buat nutup panel, pilih salah satu aplikasi buat dibuka di
window mengambang (floating, bukan split-screen).

## Yang perlu kamu tau dulu (penting, biar gak salah ekspektasi)

1. **Window mengambangnya itu yang ngatur Android, bukan kode FloatDock.**
   Begitu app yang dipilih (misal WhatsApp) terbuka mengambang, kotak
   resize & tombol close (X) di pojoknya itu disediain otomatis sama
   sistem Android lewat mekanisme *freeform window* — FloatDock cuma
   "memanggil" app itu buat dibuka di ukuran & posisi tertentu.

2. **Fitur "auto-tertutup kalau ditipiskan banget" gak bisa 100% dibuat.**
   App biasa (tanpa root / tanpa jadi device-owner) gak dikasih izin sama
   Android buat memantau ukuran atau memaksa-tutup window milik app LAIN.
   Buat nutup app mengambang, pakai tombol close (X) bawaan Android di
   window-nya. Mau buka lagi? Tinggal pilih app yang sama dari panel
   FloatDock, otomatis kebuka/pindah ke depan lagi.

3. **Harus didukung HP-nya.**
   - Samsung, Xiaomi (MIUI), Oppo/realme (ColorOS), dll: biasanya freeform
     window udah jalan langsung.
   - **Khusus Realme/Oppo (ColorOS)**: lokasi toggle-nya beda-beda tergantung
     versi ColorOS, dan kadang gak ketebak otomatis sama app (lihat poin di
     bawah). Coba cek satu-satu, biasanya ada di salah satu dari ini:
     - Settings → Opsi Pengembang → **"Force activities to be resizable"**
     - Settings → Fitur Khusus / Aplikasi Pintar → **"Aplikasi Mengambang"**
       (kalau ada, ini fitur bawaan ColorOS sendiri, beda jalur dari FloatDock)
     - Setelah toggle, **restart HP** — banyak versi ColorOS butuh reboot
       biar settingnya kebaca.
   - Pixel / Android polos (AOSP): perlu aktifin manual lewat
     **Settings → System → Developer options → "Force activities to be
     resizable"**. Kalau Developer Options belum muncul: Settings → About
     phone → tap "Build number" sebanyak 7x.
   - Kalau device-nya sama sekali gak support, app yang dipilih bakal
     tetap kebuka tapi full-screen biasa (otomatis fallback, gak crash).
   - **Catatan teknis**: app ini cuma bisa *mendeteksi* status freeform lewat
     `Settings.Global` Android — dan banyak ROM custom (termasuk ColorOS)
     gak selalu nulis status itu ke key yang sama dengan AOSP. Kalau app
     nampilin status "gak diketahui", itu bukan berarti freeform-nya mati,
     cuma app gak bisa mastiin lewat cara otomatis — cek manual di Settings.

4. Sebagian kecil app sengaja mematikan dukungan multi-window di manifest
   mereka sendiri. Kalau itu yang dipilih, FloatDock gak bisa maksa.

5. Project ini ditulis manual tanpa proses compile & test di HP asli
   (dibuat lewat chat, bukan di mesin yang ada Flutter SDK + Android
   device). Jadi kemungkinan perlu sedikit penyesuaian pas pertama kali
   di-build/dicoba — semua logic intinya ada di `OverlayService.kt`.

## Struktur project

```
floatdock/
├── pubspec.yaml
├── lib/main.dart                  -> UI: minta izin, start/stop service
├── custom_android/                -> file Android native (akan "ditempel"
│   ├── AndroidManifest.xml           ke folder android/ oleh GitHub Actions,
│   ├── kotlin/.../MainActivity.kt    lihat penjelasan di bawah)
│   ├── kotlin/.../OverlayService.kt
│   └── res/layout, res/drawable
└── .github/workflows/build-apk.yml -> otomatis build APK
```

Kenapa nggak langsung taruh di folder `android/` biasa? Karena folder
`android/` Flutter itu isinya banyak file scaffold/binary (gradle
wrapper, icon default, dst) yang harus dibuat oleh command `flutter
create`. Workflow GitHub Actions di bawah ini yang otomatis bikin
scaffold itu, lalu menempelkan file-file custom kita di atasnya — jadi
kamu nggak perlu install Flutter SDK sendiri.

## Cara upload ke GitHub & jadi APK (tanpa install Flutter di laptop)

1. Buat repo baru di GitHub, contoh nama `floatdock` (public/private bebas).
2. Upload semua isi project ini ke repo (pertahankan struktur foldernya):
   - Lewat web: drag & drop semua file/folder ke halaman repo GitHub.
   - Atau lewat terminal:
     ```
     git init
     git add .
     git commit -m "init floatdock"
     git branch -M main
     git remote add origin https://github.com/USERNAME/floatdock.git
     git push -u origin main
     ```
3. Buka tab **Actions** di repo → pilih workflow **"Build APK"** →
   klik **"Run workflow"** (atau otomatis jalan tiap kali kamu push ke
   branch `main`).
4. Tunggu sampai tanda centang hijau, scroll ke bagian **Artifacts** di
   bawah halaman run-nya → download `floatdock-apk.zip` → di dalamnya
   ada `app-release.apk`.
5. Pindahin APK ke HP → install (aktifkan "Izinkan dari sumber tidak
   dikenal" kalau diminta) → buka app → kasih izin "Tampil di atas
   aplikasi lain" & "Izinkan berjalan di latar belakang" → tap
   **"Aktifkan Floating Switcher"**.

## Izin yang diminta app ini

- **Tampil di atas aplikasi lain** (`SYSTEM_ALERT_WINDOW`) — wajib.
- **Abaikan optimasi baterai** — disarankan, biar service-nya gak gampang
  dimatiin Android pas di background.

## Kalau mau diutak-atik

- Ukuran default window mengambang: 70% lebar x 60% tinggi layar,
  posisinya — ada di fungsi `launchFloating()` dalam `OverlayService.kt`.
- Posisi awal garis handle (tepi kanan, tengah layar secara vertikal) —
  ada di fungsi `showHandle()`.
- Jumlah kolom di panel app (`numColumns`) — ada di
  `custom_android/res/layout/overlay_app_grid.xml`.
