package com.coinbase.android.event;

import com.coinbase.api.entity.Transaction;

public class NewTransactionEvent {
  public Transaction transaction;

  public NewTransactionEvent(Transaction newTransaction) {
    this.transaction = newTransaction;
  }
}
