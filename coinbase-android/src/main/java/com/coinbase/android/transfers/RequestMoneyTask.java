package com.coinbase.android.transfers;

import android.content.Context;

import com.coinbase.android.ApiTask;
import com.coinbase.api.entity.Transaction;

import org.joda.money.Money;

class RequestMoneyTask extends ApiTask<Transaction> {
  Money mAmount;
  String mRecipient, mNotes;

  public RequestMoneyTask(Context context, String recipient, Money amount, String notes) {
    super(context);
    mAmount = amount;
    mRecipient = recipient;
    mNotes = notes;
  }

  @Override
  public Transaction call() throws Exception {
    Transaction requestMoney = new Transaction();
    requestMoney.setFrom(mRecipient);
    requestMoney.setNotes(mNotes);
    requestMoney.setAmount(mAmount);
    return getClient().requestMoney(requestMoney);
  }
}
