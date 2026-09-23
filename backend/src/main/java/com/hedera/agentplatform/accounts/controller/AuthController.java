package com.hedera.agentplatform.accounts.controller;

import com.hedera.agentplatform.accounts.dto.AuthRequest;
import com.hedera.agentplatform.accounts.dto.AuthResponse;
import com.hedera.agentplatform.accounts.service.AuthService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService service;
    public AuthController(AuthService service) { this.service = service; }
    @PostMapping("/register") public AuthResponse register(@RequestBody AuthRequest request) { return service.register(request); }
    @PostMapping("/login") public AuthResponse login(@RequestBody AuthRequest request) { return service.login(request); }
    @GetMapping("/me") public AuthResponse me(@RequestHeader(value = "Authorization", required = false) String authorization) { return service.me(authorization); }
}
