package com.hedera.agentplatform.accounts.controller;

import com.hedera.agentplatform.accounts.dto.AccountResponse;
import com.hedera.agentplatform.accounts.service.AccountService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {
    private final AccountService service;

    public AccountController(AccountService service) { this.service = service; }

    @GetMapping
    List<AccountResponse> findAll() { return service.findAll(); }
}
