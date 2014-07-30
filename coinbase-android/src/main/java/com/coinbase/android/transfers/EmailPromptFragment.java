package com.coinbase.android.transfers;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;

import com.coinbase.android.PlatformUtils;
import com.coinbase.android.R;
import com.coinbase.android.Utils;

import org.joda.money.Money;

import roboguice.fragment.RoboDialogFragment;
import roboguice.inject.InjectResource;

public class EmailPromptFragment extends RoboDialogFragment {
  public static final String NOTES = "EmailPromptFragment_Notes";
  public static final String AMOUNT = "EmailPromptFragment_Amount";

  @InjectResource(R.string.transfer_email_prompt_text)
  String messageFormat;

  String mNotes;
  Money mAmount;

  protected Utils.ContactsAutoCompleteAdapter mAutocompleteAdapter;

  public EmailPromptFragment() {}

  public static EmailPromptFragment newInstance(Money amount, String notes) {
    EmailPromptFragment dialog = new EmailPromptFragment();
    Bundle args = new Bundle();
    args.putSerializable(EmailPromptFragment.AMOUNT, amount);
    args.putString(EmailPromptFragment.NOTES, notes);
    dialog.setArguments(args);
    return dialog;
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    mNotes  = getArguments().getString(NOTES);
    mAmount = (Money) getArguments().getSerializable(AMOUNT);
    super.onCreate(savedInstanceState);
  }

  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState) {
    String message = String.format(messageFormat, Utils.formatMoney(mAmount));

    View view = View.inflate(getActivity(), R.layout.transfer_email_prompt, null);
    TextView messageView = (TextView) view.findViewById(R.id.transfer_email_prompt_text);
    final AutoCompleteTextView field = (AutoCompleteTextView) view.findViewById(R.id.transfer_email_prompt_field);

    mAutocompleteAdapter = Utils.getEmailAutocompleteAdapter(getActivity());
    field.setAdapter(mAutocompleteAdapter);
    field.setThreshold(0);

    messageView.setText(message);

    if(!PlatformUtils.hasHoneycomb()) {
      messageView.setTextColor(Color.WHITE);
    }

    AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
    builder.setView(view);
    builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
      public void onClick(DialogInterface dialog, int id) {
        /* TODO delayed transaction
        if(!Utils.isConnectedOrConnecting(getActivity())) {
          // Internet is not available
          // Show error message and display option to do a delayed transaction
          new DelayedTransactionDialogFragment(
                  new DelayedRequestTransaction(field.getText().toString(), mAmount, mNotes))
                  .show(getFragmentManager(), "delayed_request");
          return;
        }
        */
        AutoCompleteTextView field = (AutoCompleteTextView) ((AlertDialog) dialog).findViewById(R.id.transfer_email_prompt_field);
        new RequestMoneyTask(getActivity(), field.getText().toString(), mAmount, mNotes).execute();
      }
    });
    builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
      public void onClick(DialogInterface dialog, int id) {
        // User cancelled the dialog
      }
    });

    return builder.create();
  }
}
