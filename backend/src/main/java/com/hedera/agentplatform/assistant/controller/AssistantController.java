package com.hedera.agentplatform.assistant.controller;

import com.hedera.agentplatform.assistant.dto.ChatRequest;
import com.hedera.agentplatform.assistant.dto.ChatResponse;
import com.hedera.agentplatform.assistant.service.AssistantService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/assistant")
public class AssistantController {
    private final AssistantService service;

    public AssistantController(AssistantService service) {
        this.service = service;
    }

    @PostMapping("/chat")
    public ChatResponse chat(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody(required = false) ChatRequest request) {
        return service.chat(authorization, request);
    }
}
