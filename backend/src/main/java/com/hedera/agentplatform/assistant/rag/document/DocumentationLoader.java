package com.hedera.agentplatform.assistant.rag.document;

import com.hedera.agentplatform.assistant.rag.model.DocumentationDocument;
import com.hedera.agentplatform.assistant.rag.model.DocumentationSource;
import java.util.List;

public interface DocumentationLoader {
    List<DocumentationDocument> load(List<DocumentationSource> sources);
}
