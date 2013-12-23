package com.coinbase.android;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.DialogFragment;

public class MerchantToolsMovedDialogFragment extends DialogFragment {

  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState) {

    AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
    builder.setTitle(R.string.merchant_tools_moved_title);
    builder.setMessage(R.string.merchant_tools_moved);

    builder.setPositiveButton(R.string.merchant_tools_moved_play_store, new DialogInterface.OnClickListener() {
      public void onClick(DialogInterface dialog, int id) {

        dismiss();
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse("http://play.google.com/store/apps/details?id=com.coinbase.android.merchant"));
        startActivity(intent);
      }
    });

    builder.setNegativeButton(android.R.string.ok, new DialogInterface.OnClickListener() {
      public void onClick(DialogInterface dialog, int id) {

        // Disable merchant tools permanently so this dialog never appears again.
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        int activeAccount = prefs.getInt(Constants.KEY_ACTIVE_ACCOUNT, -1);
        String key = String.format(Constants.KEY_ACCOUNT_ENABLE_MERCHANT_TOOLS, activeAccount);
        SharedPreferences.Editor e = prefs.edit();
        e.putBoolean(key, false);
        e.commit();
        dismiss();
      }
    });

    return builder.create();
  }
}
