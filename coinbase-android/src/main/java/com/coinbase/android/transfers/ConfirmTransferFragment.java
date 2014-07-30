package com.coinbase.android.transfers;

import android.app.Dialog;
import android.os.Bundle;

import com.coinbase.android.dialog.ConfirmationDialogFragment;

import org.joda.money.Money;

public abstract class ConfirmTransferFragment extends ConfirmationDialogFragment {

  public static final String COUNTERPARTY = "ConfirmTransferFragment_Counteryparty";
  public static final String NOTES = "ConfirmTransferFragment_Notes";
  public static final String AMOUNT = "ConfirmTransferFragment_Amount";

  protected String mCounterparty, mNotes;
  protected Money mAmount;

  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState) {
    mCounterparty = getArguments().getString(COUNTERPARTY);
    mNotes        = getArguments().getString(NOTES);
    mAmount       = (Money) getArguments().getSerializable(AMOUNT);

    return super.onCreateDialog(savedInstanceState);
  }
}
