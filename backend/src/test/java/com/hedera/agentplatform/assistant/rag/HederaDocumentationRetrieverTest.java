package com.hedera.agentplatform.assistant.rag;

import com.hedera.agentplatform.assistant.rag.embedding.EmbeddingService;
import com.hedera.agentplatform.assistant.rag.model.DocumentChunk;
import com.hedera.agentplatform.assistant.rag.model.RetrievedChunk;
import com.hedera.agentplatform.assistant.rag.retrieval.HederaDocumentationRetriever;
import com.hedera.agentplatform.assistant.rag.store.VectorStore;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class HederaDocumentationRetrieverTest {
    @Test
    void embeds_question_and_requests_configured_top_k() {
        var embeddings = mock(EmbeddingService.class);
        var store = mock(VectorStore.class);
        when(store.isEmpty()).thenReturn(false);
        when(embeddings.embedQuery("What is HCS?")).thenReturn(new float[]{1, 2});
        var expected = List.of(new RetrievedChunk(
                new DocumentChunk("id", "topic messages", "https://docs.hedera.com", "Topic Plugin", "Overview"), 0.9));
        when(store.search(new float[]{1, 2}, 3, 0.35)).thenReturn(expected);
        var retriever = new HederaDocumentationRetriever(embeddings, store, 3, 0.35);

        assertThat(retriever.retrieve("What is HCS?")).isEqualTo(expected);
        verify(store).search(new float[]{1, 2}, 3, 0.35);
    }

    @Test
    void empty_index_skips_embedding_call() {
        var embeddings = mock(EmbeddingService.class);
        var store = mock(VectorStore.class);
        when(store.isEmpty()).thenReturn(true);
        var retriever = new HederaDocumentationRetriever(embeddings, store, 5, 0.2);

        assertThat(retriever.retrieve("What is HTS?")).isEmpty();
        verifyNoInteractions(embeddings);
        verify(store, never()).search(any(), anyInt(), anyDouble());
    }
}
