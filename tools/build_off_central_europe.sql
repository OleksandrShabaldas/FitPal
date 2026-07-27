-- Build a Central-European Open Food Facts slice for FitPal (fully offline import).
--
-- Requires DuckDB (https://duckdb.org — a single binary, no server).
-- Run:   duckdb < build_off_central_europe.sql
-- Output: off_central_europe.csv  (import it in the app: AI model setup → "European foods")
--
-- It streams the public Open Food Facts CSV export and filters it down to products sold in
-- Slovakia / Czechia / Poland that have a calorie value. The export is large (~9 GB), so this
-- downloads a lot but only KEEPS the small filtered slice (usually a few MB). If you'd rather
-- not stream it, download the file first and replace the URL below with the local path.
--
-- Want more/less coverage? Edit the country list in the WHERE clause (Open Food Facts country
-- tags look like 'en:germany', 'en:austria', 'en:hungary', …). Removing the country filter
-- entirely gives the whole world — huge, not recommended for a phone.

COPY (
  SELECT
    code,
    product_name                           AS name,
    TRY_CAST("energy-kcal_100g" AS DOUBLE) AS kcal_100g,
    TRY_CAST(proteins_100g      AS DOUBLE) AS protein_100g,
    TRY_CAST(fat_100g           AS DOUBLE) AS fat_100g,
    TRY_CAST(carbohydrates_100g AS DOUBLE) AS carbs_100g,
    TRY_CAST(serving_quantity   AS DOUBLE) AS serving_g
  FROM read_csv(
    'https://static.openfoodfacts.org/data/en.openfoodfacts.org.products.csv.gz',
    delim = '\t', header = true, all_varchar = true, ignore_errors = true, quote = ''
  )
  WHERE product_name IS NOT NULL
    AND product_name <> ''
    AND TRY_CAST("energy-kcal_100g" AS DOUBLE) > 0
    AND (
         countries_tags LIKE '%en:slovakia%'
      OR countries_tags LIKE '%en:czech-republic%'
      OR countries_tags LIKE '%en:poland%'
    )
) TO 'off_central_europe.csv' (HEADER, DELIMITER ',');
