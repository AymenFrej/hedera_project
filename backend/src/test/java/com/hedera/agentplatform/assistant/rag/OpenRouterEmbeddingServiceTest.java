package com.hedera.agentplatform.assistant.rag;

import com.hedera.agentplatform.assistant.rag.embedding.OpenRouterEmbeddingService;
import com.hedera.agentplatform.assistant.service.OpenRouterGateway;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpenRouterEmbeddingServiceTest {
    @Test
    void embeds_documents_in_batches_and_preserves_order() {
        OpenRouterGateway openRouter = mock(OpenRouterGateway.class);
        OpenRouterEmbeddingService embeddings = new OpenRouterEmbeddingService(openRouter, "nvidia/nemotron-3-embed-1b:free");
        List<String> texts = IntStream.range(0, 205).mapToObj(i -> "doc-" + i).toList();
        when(openRouter.embed("nvidia/nemotron-3-embed-1b:free", texts.subList(0, 100)))
                .thenReturn(vectors(0, 100));
        when(openRouter.embed("nvidia/nemotron-3-embed-1b:free", texts.subList(100, 200)))
                .thenReturn(vectors(100, 100));
        when(openRouter.embed("nvidia/nemotron-3-embed-1b:free", texts.subList(200, 205)))
                .thenReturn(vectors(200, 5));

        List<float[]> result = embeddings.embed(texts);

        assertThat(result).hasSize(205);
        assertThat(result.get(0)).containsExactly(0);
        assertThat(result.get(100)).containsExactly(100);
        assertThat(result.get(204)).containsExactly(204);
        verify(openRouter).embed("nvidia/nemotron-3-embed-1b:free", texts.subList(0, 100));
        verify(openRouter).embed("nvidia/nemotron-3-embed-1b:free", texts.subList(100, 200));
        verify(openRouter).embed("nvidia/nemotron-3-embed-1b:free", texts.subList(200, 205));
    }

    private List<float[]> vectors(int start, int count) {
        return IntStream.range(start, start + count).mapToObj(value -> new float[] {value}).toList();
    }
}
