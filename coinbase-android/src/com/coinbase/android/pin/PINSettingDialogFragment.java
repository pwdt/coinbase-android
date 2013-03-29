package com.coinbase.android.pin;

import java.util.Arrays;
import java.util.List;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.DialogFragment;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;

import com.coinbase.android.Constants;
import com.coinbase.android.R;

public class PINSettingDialogFragment extends DialogFragment {

  private int mSelectedOption = 0;

  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState) {

    AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

    Integer[] items = new Integer[] {
                                     R.string.account_android_pin_all,
                                     R.string.account_android_pin_edit,
                                     R.string.account_android_pin_none,
    };
    final List<Integer> itemsList = Arrays.asList(items);
    String[] itemsText = new String[items.length];
    for(int i = 0; i < items.length; i++) {
      itemsText[i] = getString(items[i]);
    }

    final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
    final int activeAccount = prefs.getInt(Constants.KEY_ACTIVE_ACCOUNT, -1);
    final String pinKey = String.format(Constants.KEY_ACCOUNT_PIN, activeAccount);
    final String viewAllowedKey = String.format(Constants.KEY_ACCOUNT_PIN_VIEW_ALLOWED, activeAccount);

    // Update currently selected option
    if(prefs.getString(pinKey, null) == null) {
      // No PIN
      mSelectedOption = itemsList.indexOf(R.string.account_android_pin_none);
    } else if(prefs.getBoolean(viewAllowedKey, false)) {
      mSelectedOption = itemsList.indexOf(R.string.account_android_pin_edit);
    } else {
      mSelectedOption = itemsList.indexOf(R.string.account_android_pin_all);
    }

    builder.setSingleChoiceItems(itemsText, mSelectedOption, null);
    builder.setTitle(R.string.account_android_pin);
    builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {

      @Override
      public void onClick(DialogInterface dialog, int which) {

        Editor e = prefs.edit();

        // Save PIN setting in preferences
        mSelectedOption = ((AlertDialog) dialog).getListView().getCheckedItemPosition();
        if(mSelectedOption == itemsList.indexOf(R.string.account_android_pin_none)) {
          // Just disable the PIN
          e.putString(pinKey, null);
        } else if(mSelectedOption == itemsList.indexOf(R.string.account_android_pin_edit)) {

          e.putString(pinKey, null);
          e.putBoolean(viewAllowedKey, true);
        } else {

          e.putString(pinKey, null);
          e.putBoolean(viewAllowedKey, false);
        }

        e.commit();

        if(mSelectedOption != itemsList.indexOf(R.string.account_android_pin_none)) {
          startSetPinPrompt();
        }

        dialog.dismiss();
      }
    });
    builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {

      @Override
      public void onClick(DialogInterface dialog, int which) {
        dialog.dismiss();
      }
    });

    return builder.create();
  }

  private void startSetPinPrompt() {

    Intent intent = new Intent(getActivity(), PINPromptActivity.class);
    intent.setAction(PINPromptActivity.ACTION_SET);
    startActivity(intent);
  }
}
