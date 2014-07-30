package com.coinbase.android.transfers;

import android.app.Dialog;
import android.os.Bundle;

import com.coinbase.android.R;
import com.coinbase.android.Utils;

import org.joda.money.CurrencyUnit;
import org.joda.money.Money;

import roboguice.inject.InjectResource;

public class ConfirmSendTransferFragment extends ConfirmTransferFragment {
  public static final String FEE = "ConfirmSendTransferFragment_Fee";

  @InjectResource(R.string.transfer_confirm_message_send)
  protected String confirmationMessageFormat;

  @InjectResource(R.string.transfer_confirm_message_send_fee)
  protected String feeConfirmationMessageFormat;

  protected Money mFee;

  public ConfirmSendTransferFragment() {}

  public static ConfirmSendTransferFragment newInstance(String recipient, Money amount, Money fee, String notes) {
    ConfirmSendTransferFragment dialog = new ConfirmSendTransferFragment();
    Bundle args = new Bundle();
    args.putString(ConfirmTransferFragment.COUNTERPARTY, recipient);
    args.putSerializable(ConfirmTransferFragment.AMOUNT, amount);
    args.putSerializable(ConfirmSendTransferFragment.FEE, fee);
    args.putString(ConfirmTransferFragment.NOTES, notes);
    dialog.setArguments(args);
    return dialog;
  }

  @Override
  public String getMessage() {
    if (null == mFee) {
      return String.format(confirmationMessageFormat, Utils.formatMoney(mAmount), mCounterparty);
    } else {
      return String.format(
              feeConfirmationMessageFormat,
              Utils.formatMoney(mAmount),
              mCounterparty,
              Utils.formatMoney(mFee)
      );
    }
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    mFee = (Money) getArguments().getSerializable(FEE);
    super.onCreate(savedInstanceState);
  }

  @Override
  public void onUserConfirm() {
    new SendMoneyTask(getActivity(), mCounterparty, mAmount, mFee, mNotes).execute();
  }
}
