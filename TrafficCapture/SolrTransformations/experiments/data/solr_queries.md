# Solr Query Showcase — IMDB Movies Schema

## Schema Summary

| Field              | Type          | Indexed | Stored | MultiValued |
|--------------------|---------------|---------|--------|-------------|
| `id`               | string        | ✓       | ✓      |             |
| `title`            | text_general  | ✓       | ✓      |             |
| `plot`             | text_general  | ✓       | ✓      |             |
| `rating`           | pfloat        | ✓       | ✓      |             |
| `rank`             | pint          | ✓       | ✓      |             |
| `year`             | pint          | ✓       | ✓      |             |
| `running_time_secs`| pint          | ✓       | ✓      |             |
| `release_date`     | pdate         | ✓       | ✓      |             |
| `image_url`        | string        |         | ✓      |             |
| `directors`        | string        | ✓       | ✓      | ✓           |
| `actors`           | string        | ✓       | ✓      | ✓           |
| `genres`           | string        | ✓       | ✓      | ✓           |

---

## 1. Faceting Queries

### 1a. Simple Field Faceting — Genre Distribution

Count how many movies exist per genre.

```
q=*:*&facet=true&facet.field=genres&facet.mincount=1&rows=0
```

---

### 1b. Multi-Field Faceting — Genre + Director Distribution

Facet on two fields simultaneously to see top genres and top directors.

```
q=*:*&facet=true&facet.field=genres&facet.field=directors&facet.limit=10&facet.mincount=1&rows=0
```

---

### 1c. Facet with a Query Filter — Genre Breakdown for High-Rated Movies

Show genre distribution only for movies with rating >= 8.0.

```
q=*:*&fq=rating:[8.0 TO *]&facet=true&facet.field=genres&facet.mincount=1&rows=0
```

---

### 1d. Facet Query — Custom Rating Buckets

Create custom facet buckets to categorize movies by rating tiers (Exceptional 9+, Great 7–9, Average 5–7, Below Average <5).

```
q=*:*&facet=true&facet.query=rating:[9.0 TO *]&facet.query=rating:[7.0 TO 9.0}&facet.query=rating:[5.0 TO 7.0}&facet.query=rating:[0 TO 5.0}&rows=0
```

---

### 1e. Range Faceting — Movies by Decade

Facet the `year` field into decade-wide buckets.

```
q=*:*&facet=true&facet.range=year&facet.range.start=1920&facet.range.end=2030&facet.range.gap=10&rows=0
```

---

### 1f. Range Faceting — Movies by Running Time (30-min buckets)

Bucket movies by their running time in 1800-second (30-min) intervals.

```
q=*:*&facet=true&facet.range=running_time_secs&facet.range.start=0&facet.range.end=14400&facet.range.gap=1800&rows=0
```

---

### 1g. Facet Pivot (Cross-Tabulation) — Genre × Year

For each genre, shows the top years by count. Hierarchical facet.

```
q=*:*&facet=true&facet.pivot=genres,year&facet.limit=5&rows=0
```

---

### 1h. Facet Pivot — Director × Genre

See which genres each top director works in (minimum 2 movies).

```
q=*:*&facet=true&facet.pivot=directors,genres&facet.pivot.mincount=2&facet.limit=10&rows=0
```

---

### 1i. Date Range Faceting — Movies by Release Year

Facet on `release_date` by 1-year intervals from 2000–2025.

```
q=*:*&facet=true&facet.range=release_date&facet.range.start=2000-01-01T00:00:00Z&facet.range.end=2025-01-01T00:00:00Z&facet.range.gap=+1YEAR&rows=0
```

---

## 2. Function Queries

### 2a. Function-Based Sort

Sort movies by rating using the explicit `field()` function.

```
q=*:*&sort=field(rating) desc&fl=title,rating,year&rows=10
```

---

### 2b. Computed Field — Running Time in Minutes

Uses `div()` to compute `running_time_secs / 60` and alias it as `running_time_minutes`.

```
q=*:*&fl=title,running_time_secs,running_time_minutes:div(running_time_secs,60)&rows=10
```

---

### 2c. Computed Field — Running Time in Hours

Nested division to convert seconds → hours.

```
q=*:*&fl=title,running_time_hours:div(running_time_secs,3600)&rows=10
```

---

### 2d. Additive Boost by Rating (eDisMax `bf`)

Search for "war" in the title; additively boost relevance score by `rating`.

```
q=title:war&defType=edismax&bf=rating&fl=title,rating,score&rows=10
```

---

### 2e. Multiplicative Boost (eDisMax `boost`)

Search for "love"; multiply the score by `rating` (stronger effect than additive).

```
q=title:love&defType=edismax&boost=rating&fl=title,rating,year,score&rows=10
```

---

### 2f. Recency Boost — Prefer Newer Movies

Search for "adventure"; boost newer movies using `recip()` on movie age.

```
q=title:adventure&defType=edismax&bf=recip(sub(2025,year),1,1000,1000)&fl=title,year,rating,score&rows=10
```

`sub(2025, year)` = age. `recip(age, 1, 1000, 1000)` decays as age increases.

---

### 2g. Sort by Custom Function — Weighted Score (Rating × 1000 / Rank)

Movies with high ratings and low rank numbers rise to the top.

```
q=*:*&sort=div(mul(rating,1000),rank) desc&fl=title,rating,rank,custom_score:div(mul(rating,1000),rank)&rows=10
```

---

### 2h. Function Range Filter — Only Long Movies (≥ 2 hours)

`{!frange l=7200}` keeps only documents where `running_time_secs >= 7200`.

```
q=*:*&fq={!frange l=7200}running_time_secs&fl=title,running_time_secs,running_time_minutes:div(running_time_secs,60)&rows=10
```

---

### 2i. Function Range Filter — Rating-to-Rank Ratio

Filter to movies where `(rating × 100) / rank >= 5`, sort by that ratio.

```
q=*:*&fq={!frange l=5}div(mul(rating,100),rank)&fl=title,rating,rank,ratio:div(mul(rating,100),rank)&sort=div(mul(rating,100),rank) desc&rows=10
```

---

### 2j. If/Else Function — Label Movies by Length

`if(sub(running_time_secs,7200), 1, 0)` — returns 1 when running time ≠ 7200 (approximates > 2h flag).

```
q=*:*&fl=title,running_time_secs,is_long:if(sub(running_time_secs,7200),1,0)&rows=10
```

---

### 2k. Combined: Faceting + Function Boost

Search plot for "drama", boost by rating, and facet results by decade.

```
q=plot:drama&defType=edismax&bf=rating&facet=true&facet.range=year&facet.range.start=1950&facet.range.end=2030&facet.range.gap=10&fl=title,year,rating,score&rows=10
```

---

## Quick Reference: Function Query Syntax

| Function | Syntax | Description |
|----------|--------|-------------|
| `field()` | `field(rating)` | Raw field value |
| `div()` | `div(a,b)` | Division: a / b |
| `mul()` | `mul(a,b)` | Multiplication: a × b |
| `sub()` | `sub(a,b)` | Subtraction: a − b |
| `sum()` | `sum(a,b)` | Addition: a + b |
| `sqrt()` | `sqrt(rating)` | Square root |
| `log()` | `log(rank)` | Natural logarithm |
| `pow()` | `pow(rating,2)` | Power: rating² |
| `recip()` | `recip(x,m,a,b)` | Reciprocal: a / (m*x + b) |
| `if()` | `if(x,a,b)` | Conditional: if x≠0 return a, else b |
| `min()` | `min(a,b)` | Minimum of two values |
| `max()` | `max(a,b)` | Maximum of two values |
| `abs()` | `abs(sub(a,b))` | Absolute value |
| `{!frange}` | `{!frange l=5 u=10}expr` | Function range filter |