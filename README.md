# Sarjana Drawer

Aplikasi Android untuk mencoret layar di atas aplikasi apa pun, mirip fitur kuas di XRecorder / AZ Screen Recorder, tanpa perlu merekam layar.

## Fitur

- Bubble melayang memakai logo sendiri (`mujib_logo.png`), bisa digeser dan menempel ke tepi layar
- Pena bebas, panah (ada kepala panah di ujung garis), dan 6 hero yang bisa ditaruh di layar
- Slider ukuran: perbesar atau kecilkan coretan, panah, dan hero
- Alat pilih: sentuh objek lalu geser untuk memindahkannya
- Pilihan warna, undo, redo, hapus semua
- Screenshot hasil coretan, tersimpan di Galeri (`Pictures/SarjanaDrawer`)
- Toolbar 2 baris yang bisa digeser ke mana saja lewat garis kecil di bagian atasnya
- Tampilan bergaya iOS: monokrom, transparan seperti kaca, kartu modular

## Letak file logo dan hero (taruh di GitHub)

Taruh file PNG di folder `assets`:

```
app/src/main/assets/
├── mujib_logo.png
└── heroes/
    ├── cosmiccurse_panicportal_bk_pose01_shadow.png       (Barbarian King)
    ├── cosmiccurse_panicportal_aq_pose02_shadow.png       (Archer Queen)
    ├── cosmiccurse_panicportal_mp_pose02_no_shadows.png   (Minion Prince)
    ├── cosmiccurse_panicportal_gw_pose01_shadows.png      (Grand Warden)
    ├── cosmiccurse_panicportal_rc_pose03_shadows.png      (Royal Champion)
    └── cosmiccurse_panicportal_dd_pose01_no_shadows.png   (Dragon Duke)
```

Cara upload lewat HP: buka repo di GitHub, masuk ke folder tujuan, tekan **Add file > Upload files**, pilih PNG-nya, lalu **Commit changes**.

Kalau nama file hero sedikit berbeda, aplikasi tetap mencari file di `heroes/` yang memuat kode hero (`_bk_`, `_aq_`, `_mp_`, `_gw_`, `_rc_`, `_dd_`). Kalau file belum ada, hero tampil sebagai kotak berinisial dan aplikasi tidak error.

Halaman utama aplikasi menunjukkan berapa gambar hero dan logo yang sudah terbaca.

## Membuat APK tanpa PC

Setiap kali ada perubahan di branch `main`, GitHub otomatis membangun APK:

1. Buka tab **Actions** di repo ini.
2. Pilih run **Build APK** yang terbaru (centang hijau).
3. Di bagian bawah, unduh artifact **SarjanaDrawer-apk**, ekstrak ZIP-nya, lalu pasang file `.apk`.

Setelah upload logo dan hero, build otomatis jalan lagi sehingga APK baru sudah memuat gambarnya. Untuk memicu manual: Actions > Build APK > **Run workflow**.

Kalau pakai Android Studio: buka folder proyek ini, lalu **Build > Build APK(s)**.

## Cara pakai

1. Buka aplikasi, tekan **Aktifkan bubble**.
2. Beri izin "Tampilkan di atas aplikasi lain", izin notifikasi, dan izin rekam layar (pilih **Seluruh layar** jika ditanya).
3. Sentuh bubble untuk masuk mode coret. Bubble disembunyikan selama mode coret.
4. Toolbar baris 1: pena, panah, pilih/geser, hero, warna, ukuran. Baris 2: undo, redo, screenshot, hapus semua, tutup.
5. Tombol tutup keluar dari mode coret (coretan dihapus) dan bubble muncul lagi. Matikan bubble dari aplikasi atau dari tombol **Matikan** di notifikasi.

## Catatan

- Efek blur kaca sungguhan tidak tersedia untuk jendela overlay di Android, jadi efek kaca dibuat dengan warna transparan dan garis tepi tipis.
- Screenshot ikut menangkap coretan dan hero. Toolbar disembunyikan sebentar saat mengambil gambar.
- Minimal Android 10 (API 29).
