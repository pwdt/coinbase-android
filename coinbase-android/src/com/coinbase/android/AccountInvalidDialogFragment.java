package com.coinbase.android;

import com.coinbase.android.AccountsFragment.ParentActivity;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;

public class AccountInvalidDialogFragment extends DialogFragment {

  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState) {

    AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
    builder.setTitle(R.string.account_invalid_title);
    builder.setMessage(R.string.account_invalid);

    builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
      public void onClick(DialogInterface dialog, int id) {

        // Sign out
        ParentActivity activity = (ParentActivity) getActivity();
        activity.onAccountChosen(-1);
      }
    });

    return builder.create();
  }
}
