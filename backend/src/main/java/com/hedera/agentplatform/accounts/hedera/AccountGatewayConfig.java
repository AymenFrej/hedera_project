package com.hedera.agentplatform.accounts.hedera;

import com.hedera.hashgraph.sdk.Client;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AccountGatewayConfig {
    @Bean
    @ConditionalOnExpression("!'${hedera.operator-id:}'.trim().isEmpty()")
    HederaAccountGateway sdkAccountGateway(Client client) { return new SdkHederaAccountGateway(client); }

    @Bean
    @ConditionalOnExpression("'${hedera.operator-id:}'.trim().isEmpty()")
    HederaAccountGateway mockAccountGateway() { return new MockHederaAccountGateway(); }
}
