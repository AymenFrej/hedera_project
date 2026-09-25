package com.hedera.agentplatform.assistant.rag.controller;

import com.hedera.agentplatform.assistant.rag.service.RagIndexingService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/assistant/rag")
public class AssistantIndexingController {
    private final RagIndexingService indexingService;

    public AssistantIndexingController(RagIndexingService indexingService) {
        this.indexingService = indexingService;
    }

    @PostMapping("/index")
    public IndexResponse index() {
        return new IndexResponse(indexingService.index());
    }

    public record IndexResponse(int indexedChunks) {}
}
