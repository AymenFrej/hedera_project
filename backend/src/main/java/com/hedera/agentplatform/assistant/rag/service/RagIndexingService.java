package com.hedera.agentplatform.assistant.rag.service;

import com.hedera.agentplatform.assistant.rag.chunking.DocumentChunker;
import com.hedera.agentplatform.assistant.rag.document.DocumentationCatalog;
import com.hedera.agentplatform.assistant.rag.document.DocumentationLoader;
import com.hedera.agentplatform.assistant.rag.embedding.EmbeddingService;
import com.hedera.agentplatform.assistant.rag.model.DocumentChunk;
import com.hedera.agentplatform.assistant.rag.model.DocumentationDocument;
import com.hedera.agentplatform.assistant.rag.model.EmbeddedChunk;
import com.hedera.agentplatform.assistant.rag.store.VectorStore;
import com.hedera.agentplatform.assistant.service.OpenRouterProviderException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RagIndexingService {
    private static final Logger logger = LoggerFactory.getLogger(RagIndexingService.class);

    private final DocumentationCatalog catalog;
    private final DocumentationLoader loader;
    private final DocumentChunker chunker;
    private final EmbeddingService embeddings;
    private final VectorStore vectorStore;

    public RagIndexingService(
            DocumentationCatalog catalog,
            DocumentationLoader loader,
            DocumentChunker chunker,
            EmbeddingService embeddings,
            VectorStore vectorStore) {
        this.catalog = catalog;
        this.loader = loader;
        this.chunker = chunker;
        this.embeddings = embeddings;
        this.vectorStore = vectorStore;
    }

    public int index() {
        try {
            return indexDocumentation();
        } catch (RuntimeException exception) {
            if (exception instanceof OpenRouterProviderException providerException) {
                logger.warn("Hedera documentation indexing failed (providerStatus={}, providerCode={})",
                        providerException.statusCode(), providerException.providerCode());
            } else {
                logger.warn("Hedera documentation indexing failed (exceptionType={})",
                        exception.getClass().getSimpleName());
            }
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Hedera documentation indexing is temporarily unavailable.");
        }
    }

    private int indexDocumentation() {
        List<DocumentationDocument> documents = new ArrayList<>();
        for (var source : catalog.sources()) {
            try {
                documents.addAll(loader.load(List.of(source)));
            } catch (RuntimeException exception) {
                logger.warn("Skipping documentation source during indexing (url={}, exceptionType={})",
                        source.url(), exception.getClass().getSimpleName());
            }
        }

        List<DocumentChunk> chunks = documents.stream().flatMap(document -> chunker.chunk(document).stream()).toList();
        if (chunks.isEmpty()) {
            throw new IllegalStateException("No documentation chunks were available for indexing");
        }

        List<float[]> vectors = embeddings.embed(chunks.stream()
                .map(chunk -> "Title: " + chunk.title() + "\nSection: " + chunk.section() + "\n" + chunk.text())
                .toList());
        if (vectors.size() != chunks.size()) {
            throw new IllegalStateException("Embedding provider returned an unexpected number of vectors");
        }

        List<EmbeddedChunk> embedded = new ArrayList<>(chunks.size());
        for (int index = 0; index < chunks.size(); index++) {
            embedded.add(new EmbeddedChunk(chunks.get(index), vectors.get(index)));
        }
        vectorStore.replaceAll(embedded);
        logger.info("Indexed Hedera documentation (documents={}, chunks={})", documents.size(), embedded.size());
        return embedded.size();
    }
}
