# SparseSim HTTP API: `QUERY /reactions/similarity/by-reaction`

This document describes the REST endpoint implemented by `org.retrosynthesis.restapi.SearchController` when the Spring profile **`web`** is active. The service must be started with a valid Lucene index directory (see the main `README.md`).

- **Method and path:** `QUERY /reactions/similarity/by-reaction` - the HTTP QUERY method ([RFC 10008](https://www.rfc-editor.org/rfc/rfc10008)): like POST it carries a request body, but it is safe and idempotent, so it can be retried. Other methods get **`405 Method Not Allowed`** with `Allow: QUERY`.
- **Content-Type:** `application/json`
- **Base URL:** wherever the app is bound (default Spring Boot: `http://localhost:8080`)

---

## Request body

JSON object with the following properties:

| Field | Type | Required | Default | Description |
|--------|------|----------|---------|-------------|
| `smiles` | `string[]` | yes | — | One or more query **reaction SMILES** (reactants `>>` products). Each string is searched independently. |
| `knn` | `integer` | no | `10` | Maximum number of **Lucene hits** to retrieve per query string (see note below on post-filters). |
| `productsim` | `boolean` | no | `false` | When `true`, (1) broadens Lucene retrieval with a **count-aware** product ECFP4 `SHOULD` clause using the main product of each query reaction, (2) computes product ECFP4 Tanimoto for every surviving candidate, (3) surfaces that score as `ecfp_prod` in the `similarities` map, and (4) blends it into the final `sim` score via `productBoost`. When `false`, the response shape and scoring are unchanged from pure reaction similarity. |
| `subgraph` | `string` \| omitted / `null` | no | `null` | If set, non-blank **product SMILES** used as a **substructure filter**: only indexed reactions whose product matches this subgraph (path fingerprint, required match) are considered. |
| `rxnSmarts` | `string` \| omitted / `null` | no | `null` | If set, non-blank **SMARTS** pattern used to **filter** candidate reactions **after** Lucene retrieval. Reaction SMARTS (pattern contains `>`) is matched against the full reaction; otherwise it is treated as a molecule pattern. Blank or omitted means no SMARTS filter. |
| `productBoost` | `number` | no | `0.0` | Weight in `[0, 1]` of the product ECFP4 Tanimoto in the final `sim` score when `productsim=true`: `sim = (1 - productBoost) * reaction_score + productBoost * ecfp_prod`, where `reaction_score = (ecfp4 + path_6) / 2`. At `0.0` the product component is computed and returned but does not change ranking; at `1.0` ranking is driven purely by product similarity. Ignored when `productsim=false`. Values outside `[0, 1]` are clamped. |
| `rerank` | `boolean` | no | `true` | When `true`, results are **sorted by** the computed overall **`sim`** score (descending). When `false`, order follows Lucene’s hit order for the surviving candidates. |

Omitted optional fields use the defaults above (via `SearchRequest` deserialization).

### Errors

Invalid requests get **`400 Bad Request`** with an [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) problem details body (`application/problem+json`) whose `detail` says what is wrong:

- `smiles` missing, `null` or empty
- an entry of `smiles`, or `subgraph`, that is not valid (reaction) SMILES
- `rxnSmarts` that is not valid SMARTS
- `knn` below 1
- `productsim=true` for a reaction without products

One invalid entry in `smiles` fails the whole request.

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Invalid reaction SMILES: Not a reaction SMILES: CCO",
  "instance": "/reactions/similarity/by-reaction"
}
```

---

## Behaviour (pipeline)

For **each** entry in `smiles`, the server:

1. **Parses** the string as a reaction and builds **reaction** fingerprints (ECFP-style and path, depth 6 for path).
2. **Builds a Lucene boolean query:**
   - **Reaction similarity (always on for this endpoint):** `SHOULD` clauses on indexed reaction ECFP and path count fingerprints.
   - **`subgraph`:** If provided and non-blank, adds a **`FILTER`** clause: the indexed reaction’s **product** must contain the given subgraph (binary path fingerprint terms, all required).
   - **`productsim`:** If `true`, adds a **count-aware `SHOULD`** clause derived from the query reaction’s **main product** ECFP4 counts against the product ECFP field (`prod_ecfp`, fixed Lucene term boost of `1.0` — the `productBoost` parameter is applied later at the scoring stage, not here).
3. **Runs** Lucene with `knn` as the **top‑K** cap on scored hits.
4. **For each hit**, loads the stored reaction and computes **Tanimoto** similarity between query and candidate for both reaction ECFP and path fingerprints:
   - `reaction_score = (ecfp4 + path_6) / 2` (with `path_6` tied to path depth 6).
   - If `productsim=false`: **`sim`** = `reaction_score`, **`similarities`** = `{ecfp4, path_6}`.
   - If `productsim=true`: the candidate product ECFP4 is recomputed and its Tanimoto to the query product is taken as `ecfp_prod`; **`sim`** = `(1 - productBoost) * reaction_score + productBoost * ecfp_prod`, **`similarities`** = `{ecfp4, path_6, ecfp_prod}`.
5. **`rxnSmarts` filter:** if a pattern was supplied, candidates that **do not match** are **dropped** (they do not appear in the response).
6. **`rerank`:** if `true`, the remaining list is sorted by **`sim`** descending.

**Important:** `knn` limits Lucene hits **before** SMARTS filtering. If many hits are removed by `rxnSmarts`, the number of returned results can be **less than** `knn`, or empty.

---

## Response body

**Type:** JSON array of arrays: `List<List<Result>>`.

- The **outer** array has **one element per** input string in `smiles`, in order.
- Each **inner** array is the list of `Result` objects for that query.

Each **result object** has:

| Field | Type | Description |
|--------|------|-------------|
| `smiles` | `string` | Matched reaction SMILES from the index. |
| `sim` | `number` | When `productsim=false`: \((\text{ecfp4} + \text{path}_6) / 2\). When `productsim=true`: \((1 - \text{productBoost}) \cdot \text{reaction\_score} + \text{productBoost} \cdot \text{ecfp\_prod}\), where `reaction_score` is the same mean of `ecfp4` and `path_6`. |
| `xid` | `integer` | Document / reaction id stored in the index. |
| `similarities` | `object` | Always includes `ecfp4` and `path_6` (path depth in the key, e.g. `path_6`). When `productsim=true`, also includes `ecfp_prod` (product ECFP4 Tanimoto vs. the query reaction’s main product). |

Example:

```json
[
  [
    {
      "smiles": "CCO.O=O>>CC(=O)O",
      "sim": 0.87,
      "xid": 42,
      "similarities": {
        "ecfp4": 0.91,
        "path_6": 0.83
      }
    }
  ]
]
```

With `productsim=true`, each result additionally carries `ecfp_prod` and `sim` reflects the weighted blend:

```json
[
  [
    {
      "smiles": "CCO.O=O>>CC(=O)O",
      "sim": 0.88,
      "xid": 42,
      "similarities": {
        "ecfp4": 0.91,
        "path_6": 0.83,
        "ecfp_prod": 0.94
      }
    }
  ]
]
```

---

## Example `curl`

Pure reaction similarity (backward-compatible):

```bash
curl -sS -X QUERY "http://localhost:8080/reactions/similarity/by-reaction" \
  -H "Content-Type: application/json" \
  -d '{
    "smiles": ["CCO.O=O>>CC(=O)O"],
    "knn": 10,
    "productsim": false,
    "subgraph": null,
    "rxnSmarts": null,
    "productBoost": 0.0,
    "rerank": true
  }'
```

Combined reaction + product similarity, weighted 60/40 toward reaction similarity:

```bash
curl -sS -X QUERY "http://localhost:8080/reactions/similarity/by-reaction" \
  -H "Content-Type: application/json" \
  -d '{
    "smiles": ["CCO.O=O>>CC(=O)O"],
    "knn": 10,
    "productsim": true,
    "productBoost": 0.4,
    "rerank": true
  }'
```

---

## MCP parity

The MCP tool **`search_similar_reactions`** (`org.retrosynthesis.mcp.SimilarityTools`) exposes the same parameters for a **single** reaction string and returns JSON encoding of one result list. Semantics match `QUERY /reactions/similarity/by-reaction` with a one-element `smiles` array.

---

# SparseSim HTTP API: `QUERY /reactions/similarity/by-product`

Molecule-based similarity search over the **product side** of the indexed reactions. Given one or more molecule SMILES, returns reactions whose product is most similar to the query molecule.

- **Method and path:** `QUERY /reactions/similarity/by-product`
- **Content-Type:** `application/json`
- **Base URL:** same as `/reactions/similarity/by-reaction`

## Request body

| Field | Type | Required | Default | Description |
|--------|------|----------|---------|-------------|
| `smiles` | `string[]` | yes | — | One or more query **molecule SMILES** (not reaction SMILES). Each is searched independently. |
| `knn` | `integer` | no | `10` | Maximum number of Lucene hits to retrieve per query string (before SMARTS post-filter). |
| `subgraph` | `string` \| omitted / `null` | no | `null` | If set, non-blank molecule SMILES used as a **substructure filter** on the indexed **product path fingerprint** (required match). |
| `rxnSmarts` | `string` \| omitted / `null` | no | `null` | Optional SMARTS applied against the full stored reaction after Lucene retrieval. Behaves identically to `rxnSmarts` on `QUERY /reactions/similarity/by-reaction`. |
| `rerank` | `boolean` | no | `true` | When `true`, results are sorted by the recomputed **ECFP4 Tanimoto** (`sim`) descending. |

### Errors

Same as `/reactions/similarity/by-reaction`: invalid requests get a `400` problem details body.

## Behaviour (pipeline)

For **each** entry in `smiles`:

1. **Parses** the string as a **molecule** and computes its **ECFP4 count fingerprint**.
2. **Builds a Lucene boolean query:**
   - **SHOULD**: ECFP4 count terms against the indexed **product ECFP4** field (`prod_ecfp`).
   - **FILTER** (if `subgraph` is set): binary product path fingerprint terms against `prod_pathfp`, all required.
3. **Runs** Lucene with `knn` as the top‑K cap.
4. **For each hit**, loads the stored reaction, reconstructs the combined **product** atom container, recomputes its ECFP4 count fingerprint, and computes **Tanimoto** similarity against the query molecule:
   - **`sim`** = product ECFP4 Tanimoto.
   - **`similarities`** = `{"ecfp_prod": ...}` (same key as the product score of `/reactions/similarity/by-reaction` with `productsim=true`).
5. **`rxnSmarts` filter**: if supplied, candidates whose full reaction does not match are dropped.
6. **`rerank`**: if `true`, sorted by `sim` descending.

Unlike `/reactions/similarity/by-reaction`, path fingerprints are used **only for substructure filtering** and do not contribute to the `sim` score.

## Response body

Same shape as `/reactions/similarity/by-reaction`: `List<List<Result>>` (outer list aligned with the input `smiles` array). Each `Result` has:

| Field | Type | Description |
|--------|------|-------------|
| `smiles` | `string` | Matched reaction SMILES from the index. |
| `sim` | `number` | ECFP4 Tanimoto between query molecule and the matched reaction's product. |
| `xid` | `integer` | Document / reaction id stored in the index. |
| `similarities` | `object` | `{"ecfp_prod": <number>}`. |

## Example `curl`

```bash
curl -sS -X QUERY "http://localhost:8080/reactions/similarity/by-product" \
  -H "Content-Type: application/json" \
  -d '{
    "smiles": ["CC(=O)O"],
    "knn": 10,
    "subgraph": null,
    "rxnSmarts": null,
    "rerank": true
  }'
```

## MCP parity

The MCP tool **`search_reactions_by_molecule`** exposes the same parameters for a **single** molecule SMILES and returns JSON encoding of one result list. Semantics match `QUERY /reactions/similarity/by-product` with a one-element `smiles` array.

---

# Health and info

Provided by Spring Boot Actuator:

- `GET /actuator/health` - `{"status":"UP"}` once the index is loaded (the app does not start without one). Use it as the liveness/readiness probe.
- `GET /actuator/info` - details of the served index, e.g. `{"index":{"reactions":40008}}`.
