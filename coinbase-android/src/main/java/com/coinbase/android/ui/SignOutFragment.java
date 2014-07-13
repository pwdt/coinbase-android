package com.coinbase.android.ui;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;

import com.coinbase.android.MainActivity;
import com.coinbase.android.R;

public class SignOutFragment extends DialogFragment {


  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState) {

    AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
    builder.setMessage(R.string.sign_out_confirm);

    builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
      public void onClick(DialogInterface dialog, int id) {
        // Sign out
        ((MainActivity) getActivity()).changeAccount(-1);
      }
    });

    builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
      public void onClick(DialogInterface dialog, int id) {
        // Dismiss
      }
    });

    return builder.create();
  }
}