package com.hedera.agentplatform.assistant.rag;

import com.hedera.agentplatform.assistant.rag.chunking.DocumentChunker;
import com.hedera.agentplatform.assistant.rag.model.DocumentationDocument;
import com.hedera.agentplatform.assistant.rag.model.DocumentationSection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentChunkerTest {
    @Test
    void splits_text_with_overlap_and_preserves_document_metadata() {
        var chunker = new DocumentChunker(8, 2);
        var document = new DocumentationDocument("HCS guide", "https://docs.hedera.com/hcs", List.of(
                new DocumentationSection("Topics", "Hedera Consensus Service orders messages. Topics hold messages. " +
                        "Applications submit messages to topics. Mirror nodes expose topic history.")));

        var chunks = chunker.chunk(document);

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).allSatisfy(chunk -> {
            assertThat(chunk.source()).isEqualTo("https://docs.hedera.com/hcs");
            assertThat(chunk.title()).isEqualTo("HCS guide");
            assertThat(chunk.section()).isEqualTo("Topics");
            assertThat(chunk.chunkId()).isNotBlank();
            assertThat(chunk.text()).isNotBlank();
        });
        assertThat(chunks.get(0).chunkId()).isNotEqualTo(chunks.get(1).chunkId());
    }
}
