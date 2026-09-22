package com.hedera.agentplatform.tokens.hedera;
import org.springframework.stereotype.Component;
@Component public class MockHederaTokenGateway implements HederaTokenGateway { public TokenResult createToken(String name, String symbol) { return new TokenResult("0.0.mock", "CREATED", true); } public TokenResult transfer(String tokenId, String destination, String amount) { return new TokenResult(tokenId, "CONFIRMED", true); } }
