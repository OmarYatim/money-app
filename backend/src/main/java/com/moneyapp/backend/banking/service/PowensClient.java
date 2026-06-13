package com.moneyapp.backend.banking.service;

import com.moneyapp.backend.banking.dto.PowensAccessTokenResponse;
import com.moneyapp.backend.banking.dto.PowensAccountsResponse;
import com.moneyapp.backend.banking.dto.PowensConnectionsResponse;
import com.moneyapp.backend.banking.dto.PowensTokenCodeResponse;
import com.moneyapp.backend.transaction.dto.PowensTransactionsResponse;

public interface PowensClient {

  PowensAccessTokenResponse createUserAccessToken();

  PowensTokenCodeResponse createTemporaryCode(String permanentAccessToken);

  PowensAccountsResponse fetchAccounts(String permanentAccessToken);

  PowensConnectionsResponse fetchConnections(String permanentAccessToken);

  default void deleteConnection(String permanentAccessToken, Long connectionId) {
    throw new UnsupportedOperationException("Connection deletion is not supported");
  }

  default void deleteUser(String permanentAccessToken, String powensUserId) {
    throw new UnsupportedOperationException("User deletion is not supported");
  }

  default PowensTransactionsResponse fetchTransactions(String permanentAccessToken) {
    throw new UnsupportedOperationException("Transaction sync is not supported");
  }
}
