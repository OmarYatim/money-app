package com.moneyapp.backend.banking.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidBankConnectionStateException extends RuntimeException {

  public InvalidBankConnectionStateException() {
    super("Invalid state parameter");
  }
}
