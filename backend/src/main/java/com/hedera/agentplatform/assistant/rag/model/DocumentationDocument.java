package com.hedera.agentplatform.assistant.rag.model;

import java.util.List;

public record DocumentationDocument(String title, String source, List<DocumentationSection> sections) {
    public DocumentationDocument {
        sections = sections == null ? List.of() : List.copyOf(sections);
    }
}
