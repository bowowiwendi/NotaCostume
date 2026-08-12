# Panduan Pengembangan Nota Costume

Dokumen ini untuk membantu developer (termasuk AI agent) memahami struktur proyek dan berkontribusi.

## Ringkasan

Aplikasi Android native (Kotlin + ViewBinding, Material 3) untuk membuat **nota pembelian** dengan
banyak barang dalam satu nota. Data disimpan lokal di SQLite. Output nota bisa dicetak, di-PDF,
atau dibagikan sebagai gambar.

## Struktur Proyek

```
NotaCostume/
├── app/
│   ├── build.gradle          # konfigurasi build, versi, signing
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/notacostume/app/
│       │   ├── MainActivity.kt        # wadah utama: toolbar + FragmentContainer + BottomNavigation
│       │   ├── FormFragment.kt        # tab "Buat Nota" (form dinamis multi barang)
│       │   ├── RiwayatFragment.kt     # tab "Riwayat" (daftar + pencarian)
│       │   ├── NotaDetailActivity.kt  # preview nota + bagikan/cetak/PDF/hapus/edit
│       │   ├── EditNotaActivity.kt    # host form mode edit
│       │   ├── Nota.kt                # model: Nota + NotaItem
│       │   ├── NotaDbHelper.kt        # SQLiteOpenHelper (2 tabel: nota, nota_item)
│       │   ├── NotaAdapter.kt         # RecyclerView adapter daftar nota
│       │   ├── NotaPrinter.kt         # render nota ke Canvas → preview/PDF/print/share
│       │   └── Rupiah.kt              # format & parse Rp
│       └── res/
│           ├── layout/                # fragment_form, fragment_riwayat, item_barang, item_nota, dll
│           ├── menu/                  # menu_bottom (navigasi), menu_detail (edit/hapus)
│           ├── values/                # strings, colors, themes (Material 3 + dynamic color)
│           └── drawable/              # ikon vektor + background gradient
├── .github/workflows/build.yml        # CI: build debug tiap push, release saat tag v*
└── README.md
```

## Build

```bash
./gradlew assembleDebug    # APK debug
./gradlew assembleRelease  # APK rilis (signed jika keystore tersedia)
```

- `compileSdk`/`targetSdk` 34, `minSdk` 24, Java/Kotlin 17.
- **Signing rilis**: dibaca dari `keystore.properties` (lokal, di-.gitignore) atau env CI
  (`KEYSTORE_B64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`). Tanpa keystore, release
  build memakai debug signing.

## Alur Data

1. `FormFragment` → pengguna mengisi toko, tanggal, dan daftar barang (tiap baris = `NotaItem`).
2. Tombol Simpan → `NotaDbHelper.insert(Nota)` → tabel `nota` + `nota_item` dalam satu transaksi.
3. `RiwayatFragment` → `NotaDbHelper.getAll()` → `NotaAdapter` menampilkan kartu daftar.
4. Klik kartu → `NotaDetailActivity` → `NotaPrinter.preview()` membuat bitmap nota A5.
5. Aksi:
   - **Bagikan** → bitmap disimpan ke cache, dibagikan via `FileProvider` (ACTION_SEND).
   - **Cetak** → `NotaPrinter.print()` via Android `PrintManager`.
   - **PDF** → `NotaPrinter.savePdf()` via `PdfDocument` + MediaStore ke folder Downloads.
6. **Edit** → `EditNotaActivity` → `FormFragment.forEdit(id)` prefill → `NotaDbHelper.update()`.

## Skema Database

```sql
CREATE TABLE nota (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    nomor TEXT, toko TEXT, tanggal TEXT, catatan TEXT, dibuat_pada INTEGER
);
CREATE TABLE nota_item (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    nota_id INTEGER, nama TEXT, jumlah INTEGER, harga INTEGER
);
```

`Nota.total` dihitung dari item (`harga * jumlah`, disum). Tidak ada kolom total — selalu kalkulasi.

## Rilis

- Version ada di `app/build.gradle` (`versionCode`, `versionName`).
- Buat rilis: `git tag v1.0.0 && git push --tags` → CI build release + GitHub Release.
- Branch `release` berisi snapshot stabil; pengembangan harian di `main`.

## Konvensi

- Gunakan ViewBinding (`_b` nullable pattern di fragment).
- String UI selalu lewat `strings.xml` (jangan hardcode).
- Warna di `colors.xml` (referensi `md_*` untuk M3 palette).
- Ikon pakai vector drawable.
