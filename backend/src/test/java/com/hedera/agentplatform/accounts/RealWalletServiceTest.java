package com.hedera.agentplatform.accounts;

import com.hedera.agentplatform.accounts.hedera.HederaAccountGateway;
import com.hedera.agentplatform.accounts.hedera.UnavailableHederaAccountGateway;
import com.hedera.agentplatform.accounts.hedera.WalletProvisioningException;
import com.hedera.agentplatform.accounts.service.AccountKeyProtector;
import com.hedera.agentplatform.accounts.service.RealWalletService;
import com.hedera.agentplatform.shared.config.HederaProperties;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class RealWalletServiceTest {
    @Test void missingSecureStorageFailsBeforeAnyNetworkCall() {
        var gateway = mock(HederaAccountGateway.class);
        var service = new RealWalletService(gateway, new AccountKeyProtector(new HederaProperties()));
        assertThatThrownBy(() -> service.createAccount("0")).isInstanceOf(WalletProvisioningException.class);
        verifyNoInteractions(gateway);
    }
    @Test void offlineGatewayNeverReturnsAMockWallet() {
        assertThatThrownBy(() -> new UnavailableHederaAccountGateway().createAccount("0"))
            .isInstanceOf(WalletProvisioningException.class).hasMessageContaining("No account was created");
    }
}
