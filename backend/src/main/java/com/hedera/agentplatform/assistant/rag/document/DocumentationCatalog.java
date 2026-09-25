package com.hedera.agentplatform.assistant.rag.document;

import com.hedera.agentplatform.assistant.rag.model.DocumentationSource;
import java.util.List;

public interface DocumentationCatalog {
    List<DocumentationSource> sources();
}
