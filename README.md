# FievPager

Pager tanpa internet untuk Android. HP saling terhubung lewat **Bluetooth dan WiFi Direct**, lalu pesan diteruskan dari HP ke HP sampai ke tujuan (mesh). Tidak butuh internet, pulsa, router, atau WiFi yang sama.

## Fitur
- Pesan siaran ke semua HP, atau pesan langsung ke satu HP dengan tanda "Diterima ✓"
- Tombol **SOS** yang berbunyi sebagai alarm di semua HP
- Pesan cepat: "Saya aman", "Butuh bantuan", dan lainnya
- Setiap HP otomatis jadi penerus pesan (maksimal 6 lompatan)
- Simpan-teruskan: pesan 30 menit terakhir dibagikan ke HP yang baru tersambung
- Tetap aktif di latar seperti pager, dengan notifikasi bunyi dan getar

## Memasang
1. Buka halaman **Releases** repo ini dari HP, lalu unduh `FievPager.apk` terbaru.
2. Buka file APK. Jika diminta, izinkan "Instal dari sumber tidak dikenal".
3. Buka FievPager, isi nama, lalu izinkan akses Bluetooth, perangkat sekitar, lokasi, dan notifikasi.
4. Lakukan hal yang sama di HP lain. Dalam jarak sekitar 50 meter, HP akan saling menemukan dalam beberapa detik.

Syarat: Android 8.0 ke atas dengan Google Play Services (hampir semua HP Android di Indonesia, kecuali Huawei keluaran baru).

## Jangkauan
| Kondisi | Perkiraan per sambungan |
|---|---|
| Dalam ruangan, banyak tembok | 10–30 m |
| Area terbuka | 50–100 m |

Jangkauan total bertambah dengan setiap HP yang aktif di antara pengirim dan penerima.

## Regulasi
Bluetooth dan WiFi bekerja di pita 2,4 GHz/5 GHz yang bebas izin untuk perangkat bersertifikat. Aplikasi ini hanya memakai radio bawaan HP, jadi tidak memancar di frekuensi lain.

## Privasi
Sambungan antar-HP dienkripsi oleh Nearby Connections, tetapi isi pesan bisa dibaca HP yang meneruskannya. Jangan kirim data sensitif.

## Struktur kode
- `MeshEngine.kt`: penemuan HP, sambungan, penerusan pesan, penghapus duplikat
- `MeshService.kt`: layanan latar dan notifikasi
- `ui/App.kt`: tampilan (Jetpack Compose)

Build otomatis berjalan di GitHub Actions setiap ada perubahan di branch `main`.

## Rencana berikutnya
- Mode modul LoRa 920–923 MHz untuk jarak berkilo-kilometer
- Enkripsi ujung-ke-ujung untuk pesan langsung
