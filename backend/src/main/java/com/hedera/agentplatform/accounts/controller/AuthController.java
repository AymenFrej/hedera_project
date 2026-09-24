package com.hedera.agentplatform.accounts.controller;

import com.hedera.agentplatform.accounts.dto.AuthRequest;
import com.hedera.agentplatform.accounts.dto.AuthResponse;
import com.hedera.agentplatform.accounts.dto.PasswordChangeRequest;
import com.hedera.agentplatform.accounts.dto.ProfileUpdateRequest;
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
    @PutMapping("/me/profile") public AuthResponse updateProfile(@RequestHeader(value = "Authorization", required = false) String authorization, @RequestBody ProfileUpdateRequest request) { return service.updateProfile(authorization, request); }
    @PutMapping("/me/password") public void changePassword(@RequestHeader(value = "Authorization", required = false) String authorization, @RequestBody PasswordChangeRequest request) { service.changePassword(authorization, request); }
    @PostMapping("/logout") public void logout(@RequestHeader(value = "Authorization", required = false) String authorization) { service.logout(authorization); }
    @DeleteMapping("/me") public void deleteAccount(@RequestHeader(value = "Authorization", required = false) String authorization) { service.deleteAccount(authorization); }
}
