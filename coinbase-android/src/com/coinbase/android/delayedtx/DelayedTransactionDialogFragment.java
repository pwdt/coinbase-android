package com.coinbase.android.delayedtx;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;

import com.coinbase.android.MainActivity;
import com.coinbase.android.R;
import com.coinbase.android.TransactionsFragment;

import org.json.JSONException;
import org.json.JSONObject;

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

                // Insert delayed tx into database and animate onto transactions page
                try {
                  MainActivity parent = ((MainActivity) getActivity());
                  parent.getTransferFragment().clearForm();
                  TransactionsFragment transactionsFragment = parent.getTransactionsFragment();
                  JSONObject json = delayedTransaction.createTransaction(getActivity());
                  transactionsFragment.insertTransactionAnimated(0, json, delayedTransaction.getCategory(), "delayed");
                  parent.switchTo(MainActivity.FRAGMENT_INDEX_TRANSACTIONS);
                } catch (JSONException e) {
                  throw new RuntimeException(e);
                }

                // Enable broadcast receiver
                PackageManager pm = getActivity().getPackageManager();
                pm.setComponentEnabledSetting(new ComponentName(getActivity(), ConnectivityChangeReceiver.class),
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
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
