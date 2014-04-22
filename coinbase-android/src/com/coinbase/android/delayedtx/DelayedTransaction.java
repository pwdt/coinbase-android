package com.coinbase.android.delayedtx;

/**
 * Transaction that has not yet been sent
 */
public class DelayedTransaction {

  public static enum Type {
    SEND,
    REQUEST,
    BUY,
    SELL;
  }

  public Type type;
  public String amount;
  public String otherUser;
  public String notes;

  public DelayedTransaction(Type type, String amount, String otherUser, String notes) {
    this.type = type;
    this.amount = amount;
    this.otherUser = otherUser;
    this.notes = notes;
  }
}
