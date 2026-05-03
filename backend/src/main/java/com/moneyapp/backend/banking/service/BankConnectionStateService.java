package com.moneyapp.backend.banking.service;

import com.moneyapp.backend.banking.entity.BankConnectionState;
import com.moneyapp.backend.banking.exception.InvalidBankConnectionStateException;
import com.moneyapp.backend.banking.repository.BankConnectionStateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BankConnectionStateService {

  private final BankConnectionStateRepository bankConnectionStateRepository;

  @Transactional
  public void create(Long userId, String state) {
    bankConnectionStateRepository.save(
        BankConnectionState.builder().userId(userId).state(state).build());
  }

  @Transactional
  public void consume(Long userId, String state) {
    if (state == null || state.isBlank()) {
      throw new InvalidBankConnectionStateException();
    }

    consume(
        bankConnectionStateRepository
            .findByUserIdAndStateAndConsumedFalse(userId, state)
            .orElseThrow(InvalidBankConnectionStateException::new));
  }

  @Transactional
  public Long consume(String state) {
    if (state == null || state.isBlank()) {
      throw new InvalidBankConnectionStateException();
    }

    BankConnectionState bankConnectionState =
        bankConnectionStateRepository
            .findByStateAndConsumedFalse(state)
            .orElseThrow(InvalidBankConnectionStateException::new);
    consume(bankConnectionState);
    return bankConnectionState.getUserId();
  }

  private void consume(BankConnectionState bankConnectionState) {
    bankConnectionState.setConsumed(true);
    bankConnectionStateRepository.save(bankConnectionState);
  }
}
