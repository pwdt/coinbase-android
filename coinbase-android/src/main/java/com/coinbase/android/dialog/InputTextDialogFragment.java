package com.coinbase.android.dialog;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

import com.coinbase.android.R;

import roboguice.fragment.RoboDialogFragment;

public abstract class InputTextDialogFragment extends RoboDialogFragment {
  public static final String VALUE = "InputTextDialogFragment_Input";
  public static final String TITLE = "InputTextDialogFragment_Title";

  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState) {
    LayoutInflater li = LayoutInflater.from(getActivity());
    View textDialogView = li.inflate(R.layout.fragment_text_dialog, null);

    final EditText userInput = (EditText) textDialogView
            .findViewById(R.id.text_dialog_input);

    userInput.setInputType(getInputType());

    if (savedInstanceState == null) {
      userInput.setText(getArguments().getString(VALUE));
    }

    AlertDialog.Builder alertBuilder = new AlertDialog.Builder(getActivity());
    alertBuilder.setView(textDialogView);
    alertBuilder.setTitle(getArguments().getString(TITLE));
    alertBuilder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int which) {
        onSubmit(userInput.getText().toString());
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

  public int getInputType() {
    return InputType.TYPE_CLASS_TEXT;
  }
  public abstract void onSubmit(String enteredValue);
  public void onCancel() {
    // Do nothing
  }
}
