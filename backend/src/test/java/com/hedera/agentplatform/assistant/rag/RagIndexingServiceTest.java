package com.hedera.agentplatform.assistant.rag;

import com.hedera.agentplatform.assistant.rag.chunking.DocumentChunker;
import com.hedera.agentplatform.assistant.rag.document.DocumentationCatalog;
import com.hedera.agentplatform.assistant.rag.document.DocumentationLoader;
import com.hedera.agentplatform.assistant.rag.embedding.EmbeddingService;
import com.hedera.agentplatform.assistant.rag.model.DocumentationDocument;
import com.hedera.agentplatform.assistant.rag.model.DocumentationSection;
import com.hedera.agentplatform.assistant.rag.model.DocumentationSource;
import com.hedera.agentplatform.assistant.rag.service.RagIndexingService;
import com.hedera.agentplatform.assistant.rag.store.VectorStore;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

class RagIndexingServiceTest {
    @Test
    void index_embeds_metadata_and_replaces_local_index() {
        var source = new DocumentationSource("HCS topic", "https://docs.hedera.com/hcs");
        var catalog = mock(DocumentationCatalog.class);
        var loader = mock(DocumentationLoader.class);
        var embeddings = mock(EmbeddingService.class);
        var store = mock(VectorStore.class);
        when(catalog.sources()).thenReturn(List.of(source));
        when(loader.load(List.of(source))).thenReturn(List.of(new DocumentationDocument(
                "HCS topic", source.url(), List.of(new DocumentationSection("Create topic", "A topic orders messages.")))));
        when(embeddings.embed(anyList())).thenReturn(List.of(new float[]{0.1f, 0.2f}));
        var indexing = new RagIndexingService(catalog, loader, new DocumentChunker(100, 10), embeddings, store);

        assertThat(indexing.index()).isEqualTo(1);
        verify(embeddings).embed(argThat((List<String> texts) -> texts.size() == 1
                && texts.getFirst().contains("Title: HCS topic")
                && texts.getFirst().contains("Section: Create topic")
                && texts.getFirst().contains("A topic orders messages.")));
        verify(store).replaceAll(argThat(chunks -> chunks.size() == 1
                && chunks.getFirst().chunk().source().equals(source.url())));
    }
}
