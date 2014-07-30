package com.coinbase.android.dialog;


import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.os.Bundle;
import android.util.TypedValue;
import android.widget.TextView;

import roboguice.fragment.RoboDialogFragment;

public abstract class ConfirmationDialogFragment extends RoboDialogFragment {
  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState) {
    final TextView message = new TextView(getActivity());
    message.setBackgroundColor(Color.WHITE);
    message.setTextColor(Color.BLACK);
    message.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);

    float scale = getResources().getDisplayMetrics().density;
    int paddingPx = (int) (15 * scale + 0.5f);
    message.setPadding(paddingPx, paddingPx, paddingPx, paddingPx);

    message.setText(getMessage());

    AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
    builder.setView(message)
            .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
              public void onClick(DialogInterface dialog, int id) {
                onUserConfirm();
              }
            })
            .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
              public void onClick(DialogInterface dialog, int id) {
                onUserCancel();
              }
            });

    return builder.create();
  }

  public abstract String getMessage();
  public abstract void onUserConfirm();
  public void onUserCancel() {}
}
