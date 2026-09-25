package com.hedera.agentplatform.assistant.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hedera.agentplatform.assistant.rag.model.DocumentChunk;
import com.hedera.agentplatform.assistant.rag.model.EmbeddedChunk;
import com.hedera.agentplatform.assistant.rag.store.JsonFileVectorStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JsonFileVectorStoreTest {
    @TempDir Path tempDir;

    @Test
    void persists_and_reloads_chunks_embeddings_and_source_metadata() {
        Path indexPath = tempDir.resolve("hedera-index.json");
        var chunk = new DocumentChunk("chunk-1", "Hedera is a distributed ledger.",
                "https://docs.hedera.com/learn/what-is-hedera", "What is Hedera?", "Overview");
        var firstRun = new JsonFileVectorStore(new ObjectMapper(), indexPath.toString());
        firstRun.replaceAll(List.of(new EmbeddedChunk(chunk, new float[]{1.0f, 0.0f})));

        var afterRestart = new JsonFileVectorStore(new ObjectMapper(), indexPath.toString());
        afterRestart.loadFromDisk();
        var matches = afterRestart.search(new float[]{1.0f, 0.0f}, 5, 0.2);

        assertThat(afterRestart.isEmpty()).isFalse();
        assertThat(matches).hasSize(1);
        assertThat(matches.getFirst().chunk().title()).isEqualTo("What is Hedera?");
        assertThat(matches.getFirst().chunk().section()).isEqualTo("Overview");
        assertThat(matches.getFirst().chunk().source()).isEqualTo(chunk.source());
        assertThat(indexPath).exists();
    }
}
