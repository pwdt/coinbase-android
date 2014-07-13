package com.coinbase.android;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.text.Html;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.coinbase.android.db.DatabaseObject;
import com.coinbase.android.db.TransactionsDatabase;
import com.coinbase.android.db.TransactionsDatabase.TransactionEntry;
import com.coinbase.android.pin.PINManager;
import com.coinbase.api.RpcManager;

import org.acra.ACRA;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;

public class TransactionDetailsFragment extends Fragment {

  public static final String EXTRA_ID = "id";

  private static enum ActionType {
    RESEND,
    COMPLETE,
    CANCEL;
  }

  private String mPinReturnTransactionId;
  private ActionType mPinReturnActionType;

  private class TakeActionTask extends AsyncTask<Object, Void, String> {

    ProgressDialog mDialog;
    ActionType type;

    @Override
    protected void onPreExecute() {
      super.onPreExecute();

      mDialog = ProgressDialog.show(getActivity(), null, getString(R.string.transactiondetails_progress));
    }

    @Override
    protected String doInBackground(Object... params) {

      type = (ActionType) params[0];
      String transactionID = (String) params[1];

      String url = String.format("transactions/%s/", transactionID);

      try {

        JSONObject result = null;

        switch(type) {
        case RESEND:
          result = RpcManager.getInstance().callPut(getActivity(), url + "resend_request", null);
          break;
        case COMPLETE:
          result = RpcManager.getInstance().callPut(getActivity(), url + "complete_request", null);
          break;
        case CANCEL:
          result = RpcManager.getInstance().callDelete(getActivity(), url + "cancel_request", null);
          break;
        }

        boolean success = result.getBoolean("success");

        if(success) {
          return null;
        } else {
          String error = result.optString("error", null);
          if(error == null && result.has("errors")) {
            // Array
            error = Utils.getErrorStringFromJson(result, ", ");
          }
          return error;
        }
      } catch(Exception e) {
        // An error
        e.printStackTrace();
        return e.getMessage();
      }
    }

    @Override
    protected void onPostExecute(String result) {

      try {
        mDialog.dismiss();
      } catch (Exception e) {
        // ProgressDialog has been destroyed already
      }

      if(getActivity() == null) {
        return;
      }

      if(result != null) {

        Log.i("Coinbase", "Transacation action not successful.");
        Toast.makeText(getActivity(),
            String.format(getActivity().getString(R.string.transactiondetails_action_error), result), Toast.LENGTH_LONG).show();
      } else {

        int msg = 0;

        switch(type) {
          case RESEND:
            msg = R.string.transactiondetails_action_success_resend;
            break;
          case COMPLETE:
            msg = R.string.transactiondetails_action_success_complete;
            break;
          case CANCEL:
            msg = R.string.transactiondetails_action_success_cancel;
            break;
        }

        Toast.makeText(getActivity(), msg, Toast.LENGTH_SHORT).show();

        if(type != ActionType.RESEND) {
          if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).refresh();
            ((TransactionsFragment) getParentFragment()).hideDetails(true);
          } else {
            getActivity().finish();
          }
        }
      }
    }

  }

  private class LoadTransactionFromInternetTask extends AsyncTask<String, Void, JSONObject> {

    String mId;

    @Override
    protected JSONObject doInBackground(String... params) {

      mId = params[0];

      Log.i("Coinbase", "Loading transaction " + mId + " from internet...");
      try {
        return RpcManager.getInstance().callGet(getActivity(), "transactions/" + mId).getJSONObject("transaction");
      } catch (JSONException e) {
        ACRA.getErrorReporter().handleException(new RuntimeException("LoadTransactionFromInternet", e));
        e.printStackTrace();
      } catch (IOException e) {
        e.printStackTrace();
      }

      return null;
    }

    @Override
    protected void onPostExecute(JSONObject result) {

      if(mView != null && getActivity() != null) {

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        int activeAccount = prefs.getInt(Constants.KEY_ACTIVE_ACCOUNT, -1);
        String currentUserId = prefs.getString(String.format(Constants.KEY_ACCOUNT_ID, activeAccount), null);

        try {
          if(result != null) {
            mContainer.setVisibility(View.VISIBLE);
            fillViewsForJson(mView, result, currentUserId, mId);
            return;
          }
        } catch (JSONException e) {
          e.printStackTrace();
        }

        // Error
        Toast.makeText(getActivity(), R.string.transactiondetails_error, Toast.LENGTH_SHORT).show();
        if (getActivity() instanceof MainActivity) {
          ((TransactionsFragment) getParentFragment()).hideDetails(true);
        } else {
          // Transaction details activity
          getActivity().finish();
        }
      }
    }

  }

  ViewGroup mView;
  View mContainer;

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    mView = null;
    mContainer = null;
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container,
      Bundle savedInstanceState) {

    // Inflate base layout
    ViewGroup view = (ViewGroup) inflater.inflate(R.layout.fragment_transactiondetails, container, false);
    mView = view;
    mContainer = view.findViewById(R.id.transactiondetails_container);

    // Get user ID
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
    int activeAccount = prefs.getInt(Constants.KEY_ACTIVE_ACCOUNT, -1);
    String currentUserId = prefs.getString(String.format(Constants.KEY_ACCOUNT_ID, activeAccount), null);

    // Get arguments
    Bundle args = getArguments();
    if(args.containsKey("data")) {
      // From browser link
      // Fetch transaction information from internet.
      Uri uri = args.getParcelable("data");
      String transactionId = uri.getPath().substring("/transactions/".length());
      new LoadTransactionFromInternetTask().execute(transactionId);
      mContainer.setVisibility(View.GONE);
    } else {

      // Fetch transaction JSON from database
      final String transactionId = getArguments().getString(EXTRA_ID);
      Cursor c = DatabaseObject.getInstance().query(getActivity(), TransactionEntry.TABLE_NAME,
          new String[] { TransactionEntry.COLUMN_NAME_TRANSACTION_JSON, },
          TransactionEntry._ID + " = ? AND " + TransactionEntry.COLUMN_NAME_ACCOUNT + " = ?",
          new String[] { transactionId, Integer.toString(activeAccount) }, null, null, null);
      if(!c.moveToFirst()) {
        // Transaction not found
        Toast.makeText(getActivity(), R.string.transactiondetails_error, Toast.LENGTH_SHORT).show();
        getActivity().finish();
        return view;
      }
      if (c.getCount() > 1) {
        Log.w("Coinbase", "Warning: a query for a single tranasction returned " + c.getCount() + " results.");
      }

      String stringData;
      try {
        stringData = c.getString(c.getColumnIndex(TransactionEntry.COLUMN_NAME_TRANSACTION_JSON));
      } catch(Exception e) {
        stringData = null;
      }

      if(stringData == null) {
        // No data for this transaction
        new LoadTransactionFromInternetTask().execute(transactionId);
        mContainer.setVisibility(View.GONE);
        return view;
      }

      JSONObject data;
      try {
        data = new JSONObject(new JSONTokener(stringData));

        c.close();

        fillViewsForJson(view, data, currentUserId, transactionId);

      } catch (JSONException e) {
        Toast.makeText(getActivity(), R.string.transactiondetails_error, Toast.LENGTH_LONG).show();
        e.printStackTrace();
        getActivity().finish();
      } finally {

        c.close();
      }
    }

    return view;
  }

  @SuppressLint("SimpleDateFormat")
  private void fillViewsForJson(ViewGroup view, JSONObject data, String currentUserId,
      final String transactionId) throws JSONException {

    // Fill views
    TextView amount = (TextView) view.findViewById(R.id.transactiondetails_amount),
        amountLabel = (TextView) view.findViewById(R.id.transactiondetails_label_amount),
        from = (TextView) view.findViewById(R.id.transactiondetails_from),
        to = (TextView) view.findViewById(R.id.transactiondetails_to),
        date = (TextView) view.findViewById(R.id.transactiondetails_date),
        status = (TextView) view.findViewById(R.id.transactiondetails_status),
        notes = (TextView) view.findViewById(R.id.transactiondetails_notes);
    TextView positive = (TextView) view.findViewById(R.id.transactiondetails_action_positive),
        negative = (TextView) view.findViewById(R.id.transactiondetails_action_negative);
    View actions = view.findViewById(R.id.transactiondetails_actions);

    boolean sentToMe = data.optJSONObject("sender") == null || !currentUserId.equals(data.getJSONObject("sender").optString("id"));
    boolean isRequest = data.getBoolean("request");

    // Amount
    String amountText = Utils.formatCurrencyAmount(data.getJSONObject("amount").getString("amount"), true);
    amount.setText(amountText);
    int amountLabelResource = R.string.transactiondetails_amountsent;
    if(isRequest) {
      amountLabelResource = R.string.transactiondetails_amountrequested;
    } else if(sentToMe) {
      amountLabelResource = R.string.transactiondetails_amountreceived;
    }
    amountLabel.setText(amountLabelResource);

    // To / From
    String recipient = getName(data.optJSONObject("recipient"), data.optString("recipient_address"), currentUserId);
    from.setText(getName(data.optJSONObject("sender"), null, currentUserId));
    to.setText(recipient);
    if (Build.VERSION.SDK_INT >= 11) {
      from.setTextIsSelectable(true);
      to.setTextIsSelectable(true);
    }

    // Date
    SimpleDateFormat dateFormat = new SimpleDateFormat("MMMM dd, yyyy, 'at' hh:mma zzz");
    try {
      String createdAt = data.optString("created_at");
      date.setText(dateFormat.format(ISO8601.toCalendar(createdAt).getTime()));
    } catch (ParseException e) {
      date.setText(null);
    }
    date.setTypeface(FontManager.getFont(getActivity(), "Roboto-Light"));

    // Status
    String transactionStatus = data.optString("status", getString(R.string.transaction_status_error));
    String readable = transactionStatus;
    int background = R.drawable.transaction_unknown;
    if("complete".equals(transactionStatus)) {
      readable = getString(R.string.transaction_status_complete);
      background = R.drawable.transaction_complete;
    } else if("pending".equals(transactionStatus)) {
      readable = getString(R.string.transaction_status_pending);
      background = R.drawable.transaction_pending;
    } else if("delayed".equals(transactionStatus)) {
      readable = getString(R.string.transaction_status_delayed);
      background = R.drawable.transaction_delayed;
    }
    status.setText(readable);
    status.setBackgroundResource(background);

    // Notes
    String notesText = data.optString("notes");

    boolean noNotes = "null".equals(notesText) || notesText == null || "".equals(notesText);
    notes.setText(noNotes ? null : Html.fromHtml(notesText.replace("\n", "<br>")));
    notes.setVisibility(noNotes ? View.GONE : View.VISIBLE);

    view.findViewById(R.id.transactiondetails_label_notes).setVisibility(noNotes ? View.INVISIBLE : View.VISIBLE);

    // Buttons
    boolean senderOrRecipientIsExternal = data.optJSONObject("sender") == null || data.optJSONObject("recipient") == null;
    negative.setTypeface(FontManager.getFont(getActivity(), "Roboto-Light"));
    positive.setTypeface(FontManager.getFont(getActivity(), "Roboto-Light"));
    if("delayed".equals(transactionStatus)) {
      // Transaction has not actually been sent - show cancel button

      positive.setText(R.string.transactiondetails_delayed_cancel);
      negative.setVisibility(View.GONE);
      positive.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
          cancelDelayedTransaction(transactionId);
        }
      });
    } else if(!isRequest || senderOrRecipientIsExternal || !"pending".equals(transactionStatus)) {
      // No actions
      actions.setVisibility(View.GONE);
    } else if(sentToMe) {

      positive.setText(R.string.transactiondetails_request_resend);
      negative.setText(R.string.transactiondetails_request_cancel);

      negative.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
          takeAction(ActionType.CANCEL, transactionId);
        }
      });

      positive.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
          takeAction(ActionType.RESEND, transactionId);
        }
      });
    } else {

      positive.setText(String.format(getString(R.string.transactiondetails_request_send), amountText, recipient));
      negative.setText(R.string.transactiondetails_request_decline);

      positive.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
          takeAction(ActionType.COMPLETE, transactionId);
        }
      });

      negative.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
          takeAction(ActionType.CANCEL, transactionId);
        }
      });
    }
  }

  private void takeAction(ActionType type, String transactionId) {

    if(!PINManager.getInstance().checkForEditAccess(getActivity())) {
      mPinReturnTransactionId = transactionId;
      mPinReturnActionType = type;
      return;
    }

    new TakeActionTask().execute(type, transactionId);
  }

  public void onPINPromptSuccessfulReturn() {

    if (mPinReturnActionType != null) {

      takeAction(mPinReturnActionType, mPinReturnTransactionId);
      mPinReturnActionType = null;
    }
  }

  private String getName(JSONObject person, String address, String currentUserId) {

    String name = person == null ? null : person.optString("name");
    String email = person == null ? null : person.optString("email");
    boolean hasEmail = email != null && !email.equals("");

    if(person != null && currentUserId.equals(person.optString("id"))) {
      return getString(R.string.transaction_user_you);
    }

    if(name != null) {

      String addition = "";

      if(!name.equals(email)) {
        addition = (hasEmail ? String.format(" (%s)", email) : "");
      }

      return name + addition;
    } else if(hasEmail) {
      return email;
    } else if(address != null) {
      return address;
    } else {
      return getString(R.string.transaction_user_external);
    }
  }

  private void cancelDelayedTransaction(String transactionId) {

    // Delete transaction from database and update transactions list
    DatabaseObject db = DatabaseObject.getInstance();
    Context c = getActivity();
    synchronized(db.databaseLock) {
      db.delete(c, TransactionEntry.TABLE_NAME, TransactionEntry._ID + " = ?", new String[] { transactionId });
    }

    // Show toast
    Toast.makeText(c, R.string.transactiondetails_delayed_canceled, Toast.LENGTH_SHORT).show();

    // Return to transactions list
    if (getActivity() instanceof MainActivity) {
      ((MainActivity) getActivity()).refresh();
      ((TransactionsFragment) getParentFragment()).loadTransactionsList();
      ((TransactionsFragment) getParentFragment()).hideDetails(true);
    } else {
      getActivity().finish();
    }
  }
}
