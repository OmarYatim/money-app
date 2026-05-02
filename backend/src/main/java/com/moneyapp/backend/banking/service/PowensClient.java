package com.moneyapp.backend.banking.service;

import com.moneyapp.backend.banking.dto.PowensAccessTokenResponse;

public interface PowensClient {

  PowensAccessTokenResponse createUserAccessToken();
}
