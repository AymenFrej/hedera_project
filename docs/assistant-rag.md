# Hedera Assistant documentation RAG (local development)

The Assistant can ground responses in a small curated set of official Hedera documentation pages. Indexing is an explicit administrator operation; it is not performed during chat. The local vector index is persisted and reloaded at backend startup.

## Configuration

Set these in `backend/.env` (never in the frontend):

```dotenv
OPENROUTER_API_KEY=
OPENROUTER_BASE_URL=https://openrouter.ai/api/v1
OPENROUTER_MODEL=openrouter/free
OPENROUTER_EMBEDDING_MODEL=nvidia/nemotron-3-embed-1b:free
RAG_CHUNK_SIZE=700
RAG_CHUNK_OVERLAP=100
RAG_TOP_K=5
RAG_MINIMUM_SCORE=0.20
RAG_DOCUMENT_TIMEOUT_MS=15000
RAG_DOCUMENT_CACHE_PATH=data/hedera-documents
RAG_INDEX_PATH=data/hedera-rag-index.json
```

The backend imports `backend/.env` when launched from the repository root and `./.env` when launched from `backend`. The server-side OpenRouter key is used for both chat completions and embeddings. Never copy this file to the frontend. The chat model defaults to OpenRouter's free router; the embedding model defaults to Nemotron 3 Embed 1B Free so it can query the existing 2048-dimensional index.

## Start and index (PowerShell)

From the repository root:

```powershell
cd backend
./mvnw.cmd spring-boot:run
```

In a second PowerShell window, obtain a development admin session and index the curated docs. Keep the token in a local variable; do not paste it into logs or source files:

```powershell
$login = Invoke-RestMethod -Method Post http://localhost:8080/api/v1/auth/login -ContentType 'application/json' -Body '{"email":"admin@example.com","password":"AdminPass123!"}'
Invoke-RestMethod -Method Post http://localhost:8080/api/v1/admin/assistant/rag/index -Headers @{ Authorization = "Bearer $($login.token)" }
```

The index response reports `indexedChunks`. The vector index (including chunk text, source URLs, metadata and vectors) is stored at `backend/data/hedera-rag-index.json` by default (override with `RAG_INDEX_PATH`) and automatically reloads after backend restarts. Extracted official source documents are cached separately at `backend/data/hedera-documents/` by default (override with `RAG_DOCUMENT_CACHE_PATH`). The loader checks this local cache before contacting Hedera docs, so later indexing runs can work from the saved documents without redownloading them. Both paths are git-ignored local data and survive normal backend restarts; deleting `backend/data` removes both. To refresh source text, remove only `backend/data/hedera-documents` and run indexing again.

The saved vector index contains 2048-dimensional embeddings from the previous OpenRouter embedding model. Keep `OPENROUTER_EMBEDDING_MODEL` set to the same model used to create it to reuse it without reindexing. If you change embedding models, reindex the local documents before chatting; vector spaces cannot be mixed. Indexing builds the replacement in memory and only swaps the persisted index after every document has been embedded, so a provider failure leaves the previous index intact. Document embeddings are sent in batches of 100. The indexing endpoint is protected by the existing `/api/v1/admin/**` authorization rule.

Start the frontend from another window:

```powershell
cd frontend
npm run dev
```

Sign in at `http://localhost:5173` and ask the Assistant about Hedera. The chat endpoint embeds each question, searches the local cosine-similarity index, sends up to `RAG_TOP_K` qualifying chunks to OpenRouter and returns the answer plus unique source titles/URLs. If the index file is missing or unreadable, check the backend log and run the indexing command again. If no chunks meet the configured similarity threshold, the model is instructed to say when official source material is insufficient.

The initial curated catalog is maintained in `HederaDocumentationCatalog`; it uses official Hedera Markdown pages and can be extended with more official documentation sources there. Scheduled refresh and document version tracking are not included.
