package com.hedera.agentplatform.assistant.rag.document;

import com.hedera.agentplatform.assistant.rag.model.DocumentationSource;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class HederaDocumentationCatalog implements DocumentationCatalog {
    private static final List<DocumentationSource> SOURCES = List.of(
            new DocumentationSource("What is Hedera?", "https://docs.hedera.com/learn/getting-started/what-is-hedera.md"),
            new DocumentationSource("Hedera Token Service (HTS)", "https://docs.hedera.com/learn/core-concepts/tokens/hts-overview.md"),
            new DocumentationSource("Token types", "https://docs.hedera.com/learn/core-concepts/tokens/types-and-ids.md"),
            new DocumentationSource("Hedera glossary", "https://docs.hedera.com/support/glossary.md"),
            new DocumentationSource("Hiero CLI topic plugin (HCS)", "https://docs.hedera.com/solutions/tools/hiero-cli/plugins/topic-plugin.md"),
            new DocumentationSource("Create an HCS topic", "https://docs.hedera.com/native/consensus/create-topic"),
            new DocumentationSource("Submit an HCS message", "https://docs.hedera.com/native/consensus/submit-message"),
            new DocumentationSource("Read HCS messages", "https://docs.hedera.com/native/consensus/get-message"));

    @Override
    public List<DocumentationSource> sources() {
        return SOURCES;
    }
}
