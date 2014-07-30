package com.coinbase.android.transfers;

import android.content.Context;

import com.coinbase.android.ApiTask;
import com.coinbase.api.entity.Transaction;

import org.joda.money.Money;

import java.math.BigDecimal;

class SendMoneyTask extends ApiTask<Transaction> {
  Money mAmount, mFee;
  String mRecipient, mNotes;

  public SendMoneyTask(Context context, String recipient, Money amount, Money fee, String notes) {
    super(context);
    mAmount = amount;
    mRecipient = recipient;
    mFee = fee;
    mNotes = notes;
  }

  @Override
  public Transaction call() throws Exception {
    Transaction sendMoney = new Transaction();
    sendMoney.setTo(mRecipient);
    sendMoney.setNotes(mNotes);
    sendMoney.setAmount(mAmount);

    if (mFee != null) {
      if (!mFee.getCurrencyUnit().getCurrencyCode().equalsIgnoreCase("BTC")) {
        throw new AssertionError();
      }
      if (mFee.getAmount().compareTo(BigDecimal.ZERO) > 0) {
        sendMoney.setUserFee(mFee.getAmount());
      }
    }

    return getClient().sendMoney(sendMoney);
  }
}
