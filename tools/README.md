# FitPal tools — building an offline Open Food Facts slice

The app ships with USDA food data, which is US-centric. For Central-European products (Horalka,
Kofola, Sedita, Vinea, regional Polish/Czech brands…) **fully offline**:

> **Easiest path — no PC needed:** in the app, **Settings → AI model → European foods (Open Food
> Facts) → Download European foods**. That pulls the most popular Slovak/Czech/Polish products
> directly and stores them offline. Use the steps below only if you want a **bigger or custom**
> slice than the popular set.

To build a larger slice, generate it once with [Open Food Facts](https://world.openfoodfacts.org)
on your computer and import the file via "…or import a food file". After that, search and barcode
scans for those products work with no internet.

## 1. Generate the file (on a PC, once)

You need [DuckDB](https://duckdb.org) — it's a single download, no install/server.

```bash
duckdb < build_off_central_europe.sql
```

This streams the public Open Food Facts export, keeps only products sold in **Slovakia,
Czechia, and Poland** that have a calorie value, and writes **`off_central_europe.csv`**
(usually a few MB). The export it reads is large (~9 GB), so it uses bandwidth/time but only
saves the small filtered result.

- **Don't have the DuckDB CLI?** Use Python instead: `pip install duckdb`, then
  `python -c "import duckdb; duckdb.sql(open('build_off_central_europe.sql').read())"`.
- **Want more countries?** Edit the `WHERE` clause in the `.sql` (tags look like `en:germany`,
  `en:austria`, `en:hungary`). More countries = a bigger file.
- **Slow connection?** Download `en.openfoodfacts.org.products.csv.gz` first and change the URL
  in the `.sql` to the local path.

The output CSV has these columns (the app also accepts the raw Open Food Facts names):

```
code,name,kcal_100g,protein_100g,fat_100g,carbs_100g,serving_g
```

So any CSV with at least a `name` and a `kcal_100g` column works — you can also hand-make one.

## 2. Import it in the app

1. Copy `off_central_europe.csv` to your phone (cable, cloud, etc.). A `.csv.gz` works too.
2. In FitPal: **Settings → AI model → European foods (Open Food Facts) → Import food file**.
3. Pick the file. It imports into the same offline food database and builds the search index.

Done — those products are now searchable offline (Manual entry, "+ Add ingredient"), and
scanning their barcode resolves locally too.

## Notes

- Data is from Open Food Facts, licensed **ODbL** — keep a "Data: Open Food Facts" credit if you
  redistribute the file.
- Open Food Facts is crowd-sourced: popular products are well filled in, obscure ones may have
  gaps or the odd bad value. Re-running the script later picks up newer data.
- This complements the USDA databases; it doesn't replace them.
