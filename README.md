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

- **Input file**: one reaction SMILES per line (blank lines and `#` comments are ignored)
- **Output**: a directory containing the Lucene index

Example input file `reactions.smi`:

```text
# one reaction SMILES per line
CCO.O=O>>CC(=O)O
CCBr.O>>CCO
```

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

By default Spring Boot serves on `http://localhost:8080`.

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

With MCP enabled, clients typically connect to the SSE endpoint at `http://localhost:8080/sse` and send messages to `/mcp/message` (see `spring.ai.mcp.server` in `application.yml`). The exposed tool is **`search_similar_reactions`** (reaction SMILES plus optional `knn`, `subgraph`, `productsim`, `productBoost`, `rerank`), implemented in `org.retrosynthesis.mcp.SimilarityTools`.

## Query the index

Both search endpoints use the HTTP **QUERY** method ([RFC 10008](https://www.rfc-editor.org/rfc/rfc10008)): a safe, idempotent request with a JSON body. Other methods get `405` with `Allow: QUERY`. Invalid requests (bad SMILES/SMARTS, `knn` < 1, ...) get `400` with an RFC 9457 problem details body. Full reference: `SPARSESIM_ENDPOINT.md`; `QUERY /molsimilarity` searches by molecule SMILES against indexed products.

### QUERY `/similarity`

Send a JSON body with one or more reaction SMILES strings.

```bash
curl -sS -X QUERY "http://localhost:8080/similarity" \
  -H "Content-Type: application/json" \
  -d '{
    "smiles": ["CCO.O=O>>CC(=O)O"],
    "knn": 10,
    "productsim": false,
    "subgraph": null,
    "productBoost": 0.2
  }'
```

Request fields:
- **`smiles`** (required): list of reaction SMILES to search for
- **`knn`** (optional): number of nearest neighbors to return (default: 10)
- **`productsim`** (optional): include product similarity signal (default: false)
- **`subgraph`** (optional): product substructure filter SMILES (default: null)
- **`productBoost`** (optional): weighting for product similarity (default: 0.0)
- **`rerank`** (optional): rerank hits by exact Tanimoto similarity (default: true)

Response: a JSON array of arrays (one inner array per input SMILES). Each element is a result object:

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

| Field          | Type                | Description                                                        |
|----------------|---------------------|--------------------------------------------------------------------|
| `smiles`       | `string`            | Matched reaction SMILES                                            |
| `sim`          | `number`            | Overall similarity score (average of the individual fingerprints)  |
| `xid`          | `integer`           | Internal index ID of the matched reaction                          |
| `similarities` | `map<string,number>` | Per-fingerprint Tanimoto scores (e.g. `ecfp4`, `path_6`)         |

## Docker (optional)

Build and run the web API with an index mounted from `./index`:

```bash
docker compose up --build
```

The Compose file activates **`web` only** (REST). To enable MCP in Docker, set `SPRING_PROFILES_ACTIVE=web,mcp` or `SPRING_AI_MCP_SERVER_ENABLED=true` in the service environment.
