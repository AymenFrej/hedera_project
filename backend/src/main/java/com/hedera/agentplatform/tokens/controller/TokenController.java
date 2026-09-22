package com.hedera.agentplatform.tokens.controller;
import com.hedera.agentplatform.tokens.dto.TokenResponse;
import com.hedera.agentplatform.tokens.service.TokenService;
import java.util.List;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/tokens") public class TokenController { private final TokenService service; public TokenController(TokenService service) { this.service = service; } @GetMapping public List<TokenResponse> findAll() { return service.findAll(); } }
