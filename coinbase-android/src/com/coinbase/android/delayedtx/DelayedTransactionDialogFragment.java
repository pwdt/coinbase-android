package com.coinbase.android.delayedtx;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;

import com.coinbase.android.R;

/**
 * Notifies the user that internet is not available and offers to create a delayed transaction.
 */
public class DelayedTransactionDialogFragment extends DialogFragment {

  private DelayedTransaction delayedTransaction;

  public DelayedTransactionDialogFragment(DelayedTransaction tx) {
    this.delayedTransaction = tx;
  }

  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState) {
    return new AlertDialog.Builder(getActivity())
            .setTitle(R.string.delayed_tx_dialog_title)
            .setMessage(R.string.delayed_tx_dialog_message)
            .setPositiveButton(R.string.delayed_tx_dialog_ok, new DialogInterface.OnClickListener() {
              @Override
              public void onClick(DialogInterface dialogInterface, int i) {

              }
            })
            .setNegativeButton(R.string.delayed_tx_dialog_cancel, new DialogInterface.OnClickListener() {
              @Override
              public void onClick(DialogInterface dialogInterface, int i) {

              }
            })
            .create();
  }
}
