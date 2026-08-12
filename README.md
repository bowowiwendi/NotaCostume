# Nota Costume — Aplikasi Nota Pembelian

Aplikasi Android sederhana untuk membuat **nota pembelian** dengan cepat, langsung di HP. Cocok untuk toko costume, toko kelontong, atau usaha kecil lainnya.

## Fitur

- 📝 **Buat nota** — isi nama toko, tanggal, dan beberapa barang sekaligus (nama + jumlah + harga)
- 🧮 **Total otomatis** — total harga terhitung langsung saat mengetik
- 📋 **Riwayat nota** — tersimpan di database lokal (SQLite)
- 🔍 **Pencarian** — cari nota berdasarkan nomor, toko, tanggal, atau nama barang
- ✏️ **Edit nota** — ubah nota yang sudah dibuat
- 📤 **Bagikan foto nota** — kirim gambar nota ke WhatsApp, IG, dan lainnya
- 🖨️ **Cetak / Print** — cetak lewat Android Print Manager
- 📄 **Simpan PDF** — nota disimpan sebagai PDF ke folder Downloads
- 🎨 **Modern UI** — Material 3 + dynamic color (Material You)

## Cara Build

Persyaratan:

- Android SDK (compileSdk 34, minSdk 24)
- JDK 17+
- Gradle 8.7 (atau pakai wrapper yang sudah disertakan)

```bash
# clone repo
git clone https://github.com/bowowiwendi/NotaCostume.git
cd NotaCostume

# build APK debug
./gradlew assembleDebug
```

Hasil build ada di `app/build/outputs/apk/debug/app-debug.apk`.

Jika Android SDK tidak ada di lokasi default, buat file `local.properties`:

```properties
sdk.dir=/path/ke/android-sdk
```

## CI / Rilis Otomatis (GitHub Actions)

Repositori ini punya workflow `.github/workflows/build.yml`:

- **Setiap push / PR** → build otomatis APK debug, hasilnya tersedia di tab **Actions** sebagai artifact.
- **Tag `v1.0.0` dst.** → build otomatis APK release + membuat **GitHub Release** dengan file `app-release.apk` terlampir.

Untuk menandatangani APK rilis, set **repository secrets** di GitHub:

| Secret            | Isi                                        |
| ----------------- | ------------------------------------------ |
| `KEYSTORE_B64`    | Isi file keystore dalam format base64      |
| `KEYSTORE_PASSWORD` | Password keystore                        |
| `KEY_ALIAS`       | Alias key                                  |
| `KEY_PASSWORD`    | Password key                               |

Tanpa secret tersebut, build rilis tetap berhasil tapi memakai debug signing (tidak untuk dipublikasikan ke Play Store).

Lokal, signing rilis juga bisa lewat file `keystore.properties` (tidak ikut di-commit):

```properties
keystoreFile=/path/ke/release.keystore
storePassword=...
keyAlias=...
keyPassword=...
```

## Teknologi

- **Kotlin** + ViewBinding
- **Material 3** (Material Components for Android)
- **SQLite** (SQLiteOpenHelper)
- **Android Print Framework** (PrintManager)
- **MediaStore** untuk simpan PDF
- **FileProvider** untuk berbagi gambar
