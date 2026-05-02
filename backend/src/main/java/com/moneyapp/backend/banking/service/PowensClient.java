package com.moneyapp.backend.banking.service;

import com.moneyapp.backend.banking.dto.PowensAccessTokenResponse;
import com.moneyapp.backend.banking.dto.PowensAccountsResponse;
import com.moneyapp.backend.banking.dto.PowensTokenCodeResponse;

public interface PowensClient {

  PowensAccessTokenResponse createUserAccessToken();

  PowensTokenCodeResponse createTemporaryCode(String permanentAccessToken);

  PowensAccountsResponse fetchAccounts(String permanentAccessToken);
}
