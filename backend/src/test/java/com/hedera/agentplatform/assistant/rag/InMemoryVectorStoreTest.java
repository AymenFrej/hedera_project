package com.hedera.agentplatform.assistant.rag;

import com.hedera.agentplatform.assistant.rag.model.DocumentChunk;
import com.hedera.agentplatform.assistant.rag.model.EmbeddedChunk;
import com.hedera.agentplatform.assistant.rag.store.InMemoryVectorStore;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryVectorStoreTest {
    @Test
    void returns_top_k_chunks_by_cosine_similarity_and_filters_low_scores() {
        var store = new InMemoryVectorStore();
        store.replaceAll(List.of(
                embedded("best", new float[]{1, 0}),
                embedded("second", new float[]{0.8f, 0.6f}),
                embedded("weak", new float[]{0, 1})));

        var results = store.search(new float[]{1, 0}, 1, 0.5);

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().chunk().text()).isEqualTo("best");
        assertThat(results.getFirst().score()).isEqualTo(1.0);
    }

    private EmbeddedChunk embedded(String text, float[] vector) {
        return new EmbeddedChunk(new DocumentChunk(text, text, "https://docs.hedera.com", "Hedera", "Section"), vector);
    }
}
