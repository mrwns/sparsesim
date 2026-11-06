# sparsesim

Sparse similarity search for reaction SMILES using a Lucene index.

## Requirements

- **Java 19** (project uses Gradle toolchains; see `build.gradle`)

## Build

Build a runnable Spring Boot JAR:

```bash
./gradlew clean bootJar
```

The JAR will be in `build/libs/`.

## Create an index (CLI)

Indexing runs in the Spring profile **`indexer`** via `org.retrosynthesis.cli.CliIndexerRunner`.

- **Input file**: one reaction SMILES per line, optionally followed by whitespace (space or tab) and an ID (blank lines and `#` comments are ignored)
- **Output**: a directory containing the Lucene index

Each reaction keeps the ID given on its line; a line without one gets `rxn_<i>`, `i` being the reaction's 0-based position in the file (blank and comment lines are not counted). IDs are returned as `xid` in search results and must be unique: the indexer checks all of them, supplied and generated, before indexing anything, and exits with code 2 listing the duplicates otherwise.

Example input file `reactions.smi`:

```text
# reaction SMILES, optionally followed by an ID
CCO.O=O>>CC(=O)O    US20010000001;12
CCBr.O>>CCO
```

Here the second reaction gets the ID `rxn_1`.

Run the indexer:

```bash
java -jar build/libs/*.jar \
  --spring.profiles.active=indexer \
  --smiles=./reactions.smi \
  --indexdir=./index \
  --limit=100000
```

Notes:
- **`--smiles`**: path to the input SMILES file
- **`--indexdir`**: directory to create/write the index into
- **`--limit`**: optional max number of lines to index (defaults to all)

The input is read one line at a time, twice: first to check the IDs, then to index the reactions in batches of 10,000. Memory use therefore does not grow with the file, apart from the IDs kept for the uniqueness check (roughly 100 bytes per reaction).

Stored reactions are written with Lucene's `BEST_COMPRESSION` mode, which keeps the index about 15% smaller (roughly 500 bytes per reaction) at no measurable cost to search time.

A reaction that cannot be parsed or fingerprinted is skipped and logged to `index.err` inside the index directory, one tab-separated `ID  SMILES  error` line per reaction; the rest is indexed. The file is always rewritten (empty when nothing failed), and the run ends with a summary such as `Indexed 39990 of 40008 reactions to ./index in 17 s; 18 failed, see ./index/index.err`.

## Serve the index

Serving uses the Spring profile **`web`**. The app loads a single shared `Searcher` bean (see `org.retrosynthesis.config.SearcherConfig`); both REST and optional MCP use that index.

### REST API

The REST API is provided by `org.retrosynthesis.restapi.SearchController` and requires an **existing** index directory.

Start the server:

```bash
java -jar build/libs/*.jar \
  --spring.profiles.active=web \
  --indexdir=./index
```

By default Spring Boot serves on `http://localhost:8080`. The index is memory-mapped and read on demand: only the parts that queries touch are loaded, into the OS page cache rather than the Java heap. Pass `--preload=true` (or set `INDEX_PRELOAD=true`) to read the whole index into memory at startup instead; on local disk this made no measurable difference to query latency. `GET /actuator/health` reports `UP` once the index is loaded and `GET /actuator/info` reports the number of indexed reactions.

### MCP server (optional)

The same process can expose a [Model Context Protocol](https://modelcontextprotocol.io/) server (Spring AI MCP WebMVC) so tools like Cursor can call similarity search over HTTP (SSE transport). MCP is **off** unless you enable it.

Enable MCP in either of these ways:

```bash
# Recommended: activate the mcp profile alongside web
java -jar build/libs/*.jar \
  --spring.profiles.active=web,mcp \
  --indexdir=./index
```

```bash
# Or set the property explicitly
java -jar build/libs/*.jar \
  --spring.profiles.active=web \
  --spring.ai.mcp.server.enabled=true \
  --indexdir=./index
```

With MCP enabled, clients typically connect to the SSE endpoint at `http://localhost:8080/sse` and send messages to `/mcp/message` (see `spring.ai.mcp.server` in `application.yml`). The tools, implemented in `org.retrosynthesis.mcp.SimilarityTools`, mirror the REST endpoints for a single SMILES and validate the same way: **`search_similar_reactions`** (reaction SMILES plus optional `knn`, `subgraph`, `rxnSmarts`, `productWeight` for `weights.product`, `rerank`) and **`search_reactions_by_molecule`** (molecule SMILES plus optional `knn`, `subgraph`, `rxnSmarts`, `rerank`).

## Query the index

Both search endpoints take a JSON body and use the HTTP **`QUERY`** method ([RFC 10008](https://www.rfc-editor.org/rfc/rfc10008)): like `POST` it carries a body, but it is safe and idempotent, so it can be retried. Other methods get `405` with `Allow: QUERY`. Both return indexed **reactions**; they differ in the query:

| Endpoint | `smiles` holds | Ranks reactions by |
|---|---|---|
| `QUERY /reactions/similarity/by-reaction` | reaction SMILES (`reactants>>products`) | reaction similarity, optionally blended with product similarity |
| `QUERY /reactions/similarity/by-product` | molecule SMILES | similarity of their product to the query molecule |

```bash
curl -sS -X QUERY http://localhost:8080/reactions/similarity/by-reaction \
  -H 'Content-Type: application/json' \
  -d '{"smiles": ["O=[N+]([O-])c1cc(Cl)cnc1F>>Nc1cc(Cl)cnc1F"], "knn": 3, "weights": {"product": 0.5}}'

curl -sS -X QUERY http://localhost:8080/reactions/similarity/by-product \
  -H 'Content-Type: application/json' \
  -d '{"smiles": ["Nc1cc(Cl)cnc1F"], "knn": 3}'
```

### Request

| Field | Endpoints | Default | Description |
|---|---|---|---|
| `smiles` | both | required | One or more query SMILES, each searched independently. |
| `knn` | both | `10` | Hits to retrieve per query, 1 to 1000. Applied **before** `rxnSmarts`, so fewer may be returned. |
| `subgraph` | both | none | Product substructure filter (SMILES): only reactions whose product contains it are considered. Blank means no filter. |
| `rxnSmarts` | both | none | SMARTS post-filter on each hit: reaction SMARTS (contains `>`) is matched against the whole reaction, otherwise against its molecules. Blank means no filter. |
| `weights` | `by-reaction` | none | `{"product": w}`, `w` in [0, 1], also scores product similarity (see below). Omit for reaction similarity only. |
| `rerank` | both | `true` | Sort hits by `sim` (descending); `false` keeps Lucene's hit order. |

### Response

One list of hits per entry in `smiles`, in the same order:

```json
[[{"smiles": "O=[N+:1]([O-])[c:2]1[cH:3][c:4]([Cl:5])[cH:6][n:7][c:8]1[Cl:9]>>[NH2:1][c:2]1[cH:3][c:4]([Cl:5])[cH:6][n:7][c:8]1[Cl:9]",
   "sim": 0.726, "xid": "rxn_700",
   "similarities": {"ecfp4": 0.619, "path_6": 0.886, "ecfp_prod": 0.7}}]]
```

- `smiles`: the matched reaction as indexed. `xid`: its ID, as given in the input file or `rxn_<i>`.
- `similarities`: Tanimoto scores. `ecfp4` and `path_6` compare whole reactions (ECFP4 and depth-6 path fingerprints); `ecfp_prod` compares products (ECFP4).
- `sim`, the score hits are ranked by:
  - `by-reaction`: `reaction_score = (ecfp4 + path_6) / 2`; with `weights.product = w`, `sim = (1 - w) * reaction_score + w * ecfp_prod`. Giving `weights` also adds product similarity to the Lucene retrieval, so it can change which reactions are found, not just their order.
  - `by-product`: `sim = ecfp_prod`, between the query molecule and the reaction's products combined.

### Errors

Invalid requests get `400` with an [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) problem details body (`application/problem+json`) whose `detail` names the problem, e.g. `{"status": 400, "detail": "knn must be at most 1000, got 1001", ...}`. This covers: unknown fields, e.g. `"Unknown field 'knnn'; allowed fields: knn, rerank, rxnSmarts, smiles, subgraph, weights"` (including `weights` sent to `by-product`); missing or empty `smiles`; invalid SMILES (including a molecule sent to `by-reaction` or a reaction to `by-product`), `subgraph` or `rxnSmarts`; `knn` outside 1 to 1000; `weights` without `product`, or `product` outside [0, 1]; `weights` for a reaction without products. One invalid entry in `smiles` fails the whole request.

### Health and info

- `GET /actuator/health`: `{"status":"UP"}` once the index is loaded (the app does not start without one); use it as the liveness/readiness probe.
- `GET /actuator/info`: the served index, e.g. `{"index":{"reactions":40008}}`.

## Docker (optional)

Build the image, then run the indexer and the web API with the index directory mounted at `/app/index`:

```bash
docker build -t sparsesim .

docker run --rm -v "$PWD/reactions.smi:/data/reactions.smi:ro" -v "$PWD/index:/app/index" sparsesim \
  sh -c 'java $JAVA_OPTS -jar app.jar --spring.profiles.active=indexer --smiles=/data/reactions.smi --indexdir=/app/index'

docker run --rm -p 8080:8080 -v "$PWD/index:/app/index:ro" sparsesim
```

The image serves the REST API (`web` profile) by default. To enable MCP, add `-e SPRING_PROFILES_ACTIVE=web,mcp`. `JAVA_OPTS` defaults to a fixed 4 GB heap (`-Xms4g -Xmx4g`); override it to suit the index size.

## Development

Run the tests after every change:

```bash
./gradlew test
```
