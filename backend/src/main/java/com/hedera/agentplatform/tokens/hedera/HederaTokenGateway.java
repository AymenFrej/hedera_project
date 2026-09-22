package com.hedera.agentplatform.tokens.hedera;
public interface HederaTokenGateway { TokenResult createToken(String name, String symbol); TokenResult transfer(String tokenId, String destination, String amount); record TokenResult(String tokenId, String status, boolean mock) {} }
