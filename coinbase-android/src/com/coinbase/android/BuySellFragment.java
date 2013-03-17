package com.coinbase.android;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.apache.http.message.BasicNameValuePair;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.ListFragment;
import android.support.v4.widget.CursorAdapter;
import android.support.v4.widget.SimpleCursorAdapter;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.coinbase.android.Utils.CurrencyType;
import com.coinbase.android.db.DatabaseObject;
import com.coinbase.android.db.TransactionsDatabase;
import com.coinbase.android.db.TransactionsDatabase.TransactionEntry;
import com.coinbase.android.pin.PINManager;
import com.coinbase.api.RpcManager;

public class BuySellFragment extends ListFragment implements CoinbaseFragment {

  private enum BuySellType {
    BUY(R.string.buysell_type_buy, "buy"),
    SELL(R.string.buysell_type_sell, "sell");

    private int mFriendlyName;
    private String mRequestType;

    private BuySellType(int friendlyName, String requestType) {

      mFriendlyName = friendlyName;
      mRequestType = requestType;
    }

    public String getRequestType() {

      return mRequestType;
    }

    public int getName() {

      return mFriendlyName;
    }

    public static BuySellType fromApiType(String apiType) {
      if("AchCredit".equals(apiType)) {
        return SELL;
      } else if("AchDebit".equals(apiType)) {
        return BUY;
      } else {
        return null;
      }
    }
  }

  public static class ConfirmBuySellDialogFragment extends DialogFragment {

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {

      final BuySellType type = (BuySellType) getArguments().getSerializable("type");
      final String amount1 = getArguments().getString("amount1"),
          amount2 = getArguments().getString("amount2"),
          amount2currency = getArguments().getString("amount2currency");

      int messageResource = type == BuySellType.BUY ? R.string.buysell_confirm_message_buy : R.string.buysell_confirm_message_sell;
      String message = String.format(getString(messageResource), amount1, amount2, amount2currency);

      AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
      builder.setMessage(message)
      .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int id) {

          // Buy / sell!
          BuySellFragment parent = getActivity() == null ? null : ((MainActivity) getActivity()).getBuySellFragment();

          if(parent != null) {
            parent.startBuySellTask(type, amount1);
          }
        }
      })
      .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int id) {
          // User cancelled the dialog
        }
      });

      return builder.create();
    }
  }

  private class DoBuySellTask extends AsyncTask<Object, Void, Object[]> {

    private ProgressDialog mDialog;

    @Override
    protected void onPreExecute() {

      super.onPreExecute();
      mDialog = ProgressDialog.show(mParent, null, getString(R.string.buysell_progress));
    }

    protected Object[] doInBackground(Object... params) {

      return doBuySell((BuySellType) params[0], (String) params[1]);
    }

    protected void onPostExecute(Object[] result) {

      try {
        mDialog.dismiss();
      } catch (Exception e) {
        // ProgressDialog has been destroyed already
      }

      boolean success = (Boolean) result[0];
      if(success) {

        BuySellType type = (BuySellType) result[2];

        int messageId = type == BuySellType.BUY ? R.string.buysell_success_buy : R.string.buysell_success_sell;
        String text = String.format(getString(messageId), (String) result[1]);
        Toast.makeText(mParent, text, Toast.LENGTH_SHORT).show();

        // Sync transactions
        mParent.refresh();
      } else {

        Utils.showMessageDialog(getFragmentManager(), (String) result[1]);
      }
    }
  }

  private class UpdatePriceTask extends AsyncTask<String, Void, String[]> {

    @Override
    protected void onPreExecute() {

      super.onPreExecute();
      mTotal.setText(null);
      mSubmitButton.setEnabled(false);
    }

    boolean mIsSingleUpdate;

    protected String[] doInBackground(String... params) {

      try {

        String amount = params[0], type = params[1];

        mIsSingleUpdate = false;

        if(amount == null) {
          // Get single
          mIsSingleUpdate = true;
          amount = "1";
        }

        if(amount.isEmpty() || ".".equals(amount) || new BigDecimal(amount).doubleValue() == 0) {
          return new String[] { null };
        }

        Collection<BasicNameValuePair> requestParams = new ArrayList<BasicNameValuePair>();
        requestParams.add(new BasicNameValuePair("qty", amount));

        JSONObject result = RpcManager.getInstance().callGet(mParent, "prices/" + type, requestParams);
        return new String[] { result.getString("amount"), result.getString("currency") };

      } catch (IOException e) {

        e.printStackTrace();
      } catch (JSONException e) {

        e.printStackTrace();
      }

      return null;
    }

    protected void onPostExecute(String[] result) {

      TextView target = mIsSingleUpdate ? mText2 : mTotal;
      int format = mIsSingleUpdate ? R.string.buysell_text2 : R.string.buysell_total;
      int error = mIsSingleUpdate ? R.string.buysell_text2_blank : R.string.buysell_total_error;

      if(target != null) {

        if(result == null) {

          target.setText(error);
        } else {

          if(result[0] == null) {
            target.setText(null);
          } else {

            String currentPrice = Utils.formatCurrencyAmount(new BigDecimal(result[0]), false, CurrencyType.TRADITIONAL);

            if(mIsSingleUpdate) {
            } else {

              mSubmitButton.setEnabled(true);
              mCurrentPrice = currentPrice;
              mCurrentPriceCurrency = result[1];
            }

            target.setText(String.format(mParent.getString(format), currentPrice, result[1]));

            return;
          }
        }
      }

      if(!mIsSingleUpdate) {
        mCurrentPrice = null;
        mSubmitButton.setEnabled(false);
      }
    }
  }

  private class LoadTransferHistoryTask extends AsyncTask<Void, Void, Cursor> {

    @Override
    protected Cursor doInBackground(Void... params) {

      SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mParent);
      int activeAccount = prefs.getInt(Constants.KEY_ACTIVE_ACCOUNT, -1);

      Cursor c = DatabaseObject.getInstance().query(mParent, TransactionsDatabase.TransactionEntry.TABLE_NAME,
          null, TransactionEntry.COLUMN_NAME_ACCOUNT + " = ? " +
              "AND " + TransactionEntry.COLUMN_NAME_IS_TRANSFER + " = 1",
              new String[] { Integer.toString(activeAccount) }, null, null, null);
      return c;
    }

    @Override
    protected void onPostExecute(Cursor result) {

      if(getView() != null && getListView() != null) {

        setHeaderPinned(!result.moveToFirst());

        if(getListView().getAdapter() != null) {

          // Just update existing adapter
          ((CursorAdapter) getListAdapter()).changeCursor(result);
          return;
        }

        String[] from = { TransactionEntry.COLUMN_NAME_TRANSFER_JSON, TransactionEntry.COLUMN_NAME_TRANSFER_JSON,
            TransactionEntry.COLUMN_NAME_TRANSFER_JSON, TransactionEntry.COLUMN_NAME_TRANSFER_JSON };
        int[] to = { R.id.buysell_history_title, R.id.buysell_history_amount,
            R.id.buysell_history_status, R.id.buysell_history_currency };
        SimpleCursorAdapter adapter = new SimpleCursorAdapter(mParent, R.layout.fragment_buysell_history_item, result,
            from, to, 0);
        adapter.setViewBinder(new TransferViewBinder());
        setListAdapter(adapter);
      }
    }
  }

  private class TransferViewBinder implements SimpleCursorAdapter.ViewBinder {

    @Override
    public boolean setViewValue(View arg0, Cursor arg1, int arg2) {

      try {
        JSONObject item = new JSONObject(new JSONTokener(arg1.getString(arg2)));
        BuySellType type = BuySellType.fromApiType(item.optString("_type"));

        switch(arg0.getId()) {

        case R.id.buysell_history_title:

          String btcAmount = item.getJSONObject("btc").optString("amount");
          int format = type == BuySellType.SELL ? R.string.buysell_history_sell : R.string.buysell_history_buy;
          String text = String.format(mParent.getString(format), Utils.formatCurrencyAmount(btcAmount));

          ((TextView) arg0).setText(text);
          return true;

        case R.id.buysell_history_amount:

          String total = item.getJSONObject("total").getString("amount");
          String totalString = Utils.formatCurrencyAmount(new BigDecimal(total), true, CurrencyType.TRADITIONAL);

          int color = type == BuySellType.BUY ? R.color.transaction_negative : R.color.transaction_positive;

          ((TextView) arg0).setText(totalString);
          ((TextView) arg0).setTextColor(getResources().getColor(color));
          return true;

        case R.id.buysell_history_currency:

          ((TextView) arg0).setText(item.getJSONObject("total").getString("currency"));
          return true;

        case R.id.buysell_history_status:

          String status = item.optString("status", getString(R.string.transaction_status_error));

          String readable = status;
          int background = R.drawable.transaction_unknown;
          if("complete".equalsIgnoreCase(status) || "completed".equalsIgnoreCase(status)) {
            readable = getString(R.string.transaction_status_complete);
            background = R.drawable.transaction_complete;
          } else if("pending".equalsIgnoreCase(status)) {
            readable = getString(R.string.transaction_status_pending);
            background = R.drawable.transaction_pending;
          }

          ((TextView) arg0).setText(readable);
          ((TextView) arg0).setBackgroundResource(background);
          return true;
        }

        return false;
      } catch (JSONException e) {
        // Malformed transaction JSON.
        Log.e("Coinbase", "Corrupted database entry! " + arg1.getInt(arg1.getColumnIndex(TransactionEntry._ID)));
        e.printStackTrace();

        return true;
      }
    }
  }

  private MainActivity mParent;

  private UpdatePriceTask mUpdatePriceTask, mUpdateSinglePriceTask;
  private String mCurrentPrice, mCurrentPriceCurrency;

  private Spinner mBuySellSpinner;
  private TextView mTypeText, mTotal, mText2;
  private Button mSubmitButton;
  private EditText mAmount;

  private ViewGroup mRootView;
  private RelativeLayout mHeader;
  private FrameLayout mListHeaderContainer;

  @Override
  public void onCreate(Bundle savedInstanceState) {

    super.onCreate(savedInstanceState);
  }


  @Override
  public void onAttach(Activity activity) {

    super.onAttach(activity);
    mParent = (MainActivity) activity;
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container,
      Bundle savedInstanceState) {

    // Inflate the layout for this fragment
    View view = inflater.inflate(R.layout.fragment_buysell, container, false);
    mRootView = (ViewGroup) view;
    mHeader = (RelativeLayout) view.findViewById(R.id.buysell_header);

    mBuySellSpinner = (Spinner) view.findViewById(R.id.buysell_type);
    mBuySellSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {

      @Override
      public void onItemSelected(AdapterView<?> arg0, View arg1, int arg2,
          long arg3) {

        onTypeChanged();
      }

      @Override
      public void onNothingSelected(AdapterView<?> arg0) {
        // Will never happen.
        throw new RuntimeException("onNothingSelected triggered on buy / sell spinner");
      }
    });
    initializeTypeSpinner();

    mTypeText = (TextView) view.findViewById(R.id.buysell_type_text);
    mTotal = (TextView) view.findViewById(R.id.buysell_total);
    mText2 = (TextView) view.findViewById(R.id.buysell_text2);

    mSubmitButton = (Button) view.findViewById(R.id.buysell_submit);
    mSubmitButton.setOnClickListener(new View.OnClickListener() {

      @Override
      public void onClick(View v) {

        if(!PINManager.getInstance().checkForEditAccess(getActivity())) {
          return;
        }

        ConfirmBuySellDialogFragment dialog = new ConfirmBuySellDialogFragment();

        Bundle b = new Bundle();

        BuySellType type = BuySellType.values()[mBuySellSpinner.getSelectedItemPosition()];
        b.putSerializable("type", type);

        b.putString("amount1", mAmount.getText().toString());
        b.putString("amount2", mCurrentPrice);
        b.putString("amount2currency", mCurrentPriceCurrency);

        dialog.setArguments(b);

        dialog.show(getFragmentManager(), "confirm");
      }
    });

    mAmount = (EditText) view.findViewById(R.id.buysell_amount);
    mAmount.addTextChangedListener(new TextWatcher() {

      @Override
      public void afterTextChanged(Editable s) {
      }

      @Override
      public void beforeTextChanged(CharSequence s, int start, int count,
          int after) {
      }

      @Override
      public void onTextChanged(CharSequence s, int start, int before, int count) {

        updatePrice();
      }
    });

    mListHeaderContainer = new FrameLayout(mParent);
    ((ListView) view.findViewById(android.R.id.list)).addHeaderView(mListHeaderContainer);

    // Load list adapter
    Utils.runAsyncTaskConcurrently(new LoadTransferHistoryTask());

    return view;
  }

  private void onTypeChanged() {

    BuySellType type = BuySellType.values()[mBuySellSpinner.getSelectedItemPosition()];

    int typeText = type == BuySellType.BUY ? R.string.buysell_type_buy_text : R.string.buysell_type_sell_text;
    mTypeText.setText(typeText);

    int submitLabel = type == BuySellType.BUY ? R.string.buysell_submit_buy : R.string.buysell_submit_sell;
    mSubmitButton.setText(submitLabel);

    if(mUpdateSinglePriceTask != null) {
      mUpdateSinglePriceTask.cancel(true);
    }

    mText2.setText(R.string.buysell_text2_blank);
    mUpdateSinglePriceTask = new UpdatePriceTask();
    Utils.runAsyncTaskConcurrently(mUpdateSinglePriceTask, null, type.getRequestType());

    updatePrice();
  }

  private void updatePrice() {

    BuySellType type = BuySellType.values()[mBuySellSpinner.getSelectedItemPosition()];

    if(mUpdatePriceTask != null) {

      mUpdatePriceTask.cancel(true);
    }

    mUpdatePriceTask = new UpdatePriceTask();
    Utils.runAsyncTaskConcurrently(mUpdatePriceTask, mAmount.getText().toString(), type.getRequestType());
  }

  private void initializeTypeSpinner() {

    ArrayAdapter<BuySellType> arrayAdapter = new ArrayAdapter<BuySellType>(
        mParent, R.layout.fragment_transfer_type, Arrays.asList(BuySellType.values())) {

      @Override
      public View getView(int position, View convertView, ViewGroup parent) {

        TextView view = (TextView) super.getView(position, convertView, parent);
        view.setText(mParent.getString(BuySellType.values()[position].getName()));
        return view;
      }

      @Override
      public View getDropDownView(int position, View convertView, ViewGroup parent) {

        TextView view = (TextView) super.getDropDownView(position, convertView, parent);
        view.setText(mParent.getString(BuySellType.values()[position].getName()));
        return view;
      }
    };
    arrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    mBuySellSpinner.setAdapter(arrayAdapter);
  }

  protected void startBuySellTask(BuySellType type, String amount) {

    Utils.runAsyncTaskConcurrently(new DoBuySellTask(), type, amount);
  }

  private Object[] doBuySell(BuySellType type, String amount) {

    List<BasicNameValuePair> params = new ArrayList<BasicNameValuePair>();
    params.add(new BasicNameValuePair("qty", amount));

    try {
      JSONObject response = RpcManager.getInstance().callPost(mParent, type.getRequestType() + "s", params);

      boolean success = response.getBoolean("success");

      if(success) {

        return new Object[] { true, amount, type };
      } else {

        String errorMessage = response.getJSONArray("errors").optString(0);
        return new Object[] { false, String.format(getString(R.string.buysell_error_api), errorMessage) };
      }
    } catch (IOException e) {
      e.printStackTrace();
    } catch (JSONException e) {
      e.printStackTrace();
    }

    // There was an exception
    return new Object[] { false, getString(R.string.buysell_error_exception) };
  }

  private void setHeaderPinned(boolean pinned) {

    boolean isPinned = mListHeaderContainer.getChildCount() == 0;

    if(isPinned == pinned) {
      return;
    }

    if(pinned) {

      mListHeaderContainer.removeAllViews();
      mRootView.addView(mHeader);
    } else {

      mRootView.removeView(mHeader);
      mListHeaderContainer.addView(mHeader);
    }
  }

  @Override
  public void onListItemClick(ListView l, View v, int position, long id) {

    if(position == 0) {
      return; // Header view
    }

    // Compensate for header
    position--;

    Cursor c = ((CursorAdapter) getListAdapter()).getCursor();
    c.moveToPosition(position);

    String transactionId = c.getString(c.getColumnIndex(TransactionEntry._ID));
    Intent intent = new Intent(mParent, TransactionDetailsActivity.class);
    intent.putExtra(TransactionDetailsFragment.EXTRA_ID, transactionId);
    mParent.startActivityForResult(intent, 1);
  }

  public void onTransactionsSynced() {

    // Refresh the history view
    Utils.runAsyncTaskConcurrently(new LoadTransferHistoryTask());
  }

  public void refresh() {
    // Nothing to refresh in this fragment
  }

  @Override
  public void onSwitchedTo() {

    // Focus text field
    mAmount.requestFocus();
  }
}
