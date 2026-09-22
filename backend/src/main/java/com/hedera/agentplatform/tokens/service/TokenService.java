package com.hedera.agentplatform.tokens.service;
import com.hedera.agentplatform.tokens.dto.TokenResponse;
import java.util.List;
import org.springframework.stereotype.Service;
@Service public class TokenService { public List<TokenResponse> findAll() { return List.of(new TokenResponse("token_demo", "DEMO", "Demo Loyalty Token", "MOCK")); } }
