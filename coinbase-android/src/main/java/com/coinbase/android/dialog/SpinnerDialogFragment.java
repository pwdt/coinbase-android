package com.coinbase.android.dialog;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;

import roboguice.fragment.RoboDialogFragment;

public abstract class SpinnerDialogFragment<T> extends RoboDialogFragment {
  public static final String SELECTED_INDEX = "SpinnerDialogFragment_Selected_Index";
  public static final String TITLE = "SpinnerDialogFragment_Title";

  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState) {
    AlertDialog.Builder alertBuilder = new AlertDialog.Builder(getActivity());

    final T[] options           = getOptions();
    String[] displayTexts       = new String[options.length];
    for (int i = 0; i < options.length; ++i) {
      displayTexts[i] = getOptionDisplayText(options[i]);
    }

    alertBuilder.setTitle(getArguments().getString(TITLE));

    int defaultSelection = getArguments().getInt(SELECTED_INDEX);
    alertBuilder.setSingleChoiceItems(displayTexts, defaultSelection, new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int which) {
        // Do Nothing
      }
    });

    alertBuilder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int which) {
        int selectedPosition = ((AlertDialog) dialog).getListView().getCheckedItemPosition();
        onChosenValue(options[selectedPosition]);
      }
    });

    alertBuilder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int which) {
        onCancel();
      }
    });

    return alertBuilder.create();
  }

  public abstract T[] getOptions();
  public abstract String getOptionDisplayText(T option);
  public abstract void onChosenValue(T chosenValue);
  public void onCancel() {
    // Do nothing
  }
}
