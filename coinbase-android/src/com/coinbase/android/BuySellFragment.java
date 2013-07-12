package com.coinbase.android;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import org.acra.ACRA;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.ListFragment;
import android.support.v4.widget.CursorAdapter;
import android.support.v4.widget.SimpleCursorAdapter;
import android.text.Editable;
import android.text.Html;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
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
      final String amount1 = getArguments().getString("amount1");
      final boolean agreeBtcAmountVaries = getArguments().getBoolean("agreeBtcAmountVaries", false);

      final TextView message = new TextView(getActivity());
      message.setBackgroundColor(Color.WHITE);
      message.setTextColor(Color.BLACK);
      message.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
      message.setLinksClickable(true);
      message.setMovementMethod(LinkMovementMethod.getInstance());

      float scale = getResources().getDisplayMetrics().density;
      int paddingPx = (int) (15 * scale + 0.5f);
      message.setPadding(paddingPx, paddingPx, paddingPx, paddingPx);

      if(agreeBtcAmountVaries) {

        message.setText(Html.fromHtml(getString(R.string.buysell_confirm_message_buy_varies)));
      } else {

        final String amount2 = getArguments().getString("amount2"),
            amount2currency = getArguments().getString("amount2currency");

        int messageResource = type == BuySellType.BUY ? R.string.buysell_confirm_message_buy : R.string.buysell_confirm_message_sell;
        message.setText(String.format(getString(messageResource), amount1, amount2, amount2currency));
      }

      AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
      builder.setView(message)
      .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int id) {

          // Buy / sell!
          BuySellFragment parent = getActivity() == null ? null : ((MainActivity) getActivity()).getBuySellFragment();

          if(parent != null) {
            parent.startBuySellTask(type, amount1, agreeBtcAmountVaries);
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

    private static final String BTC_AMOUNT_VARIES_ERROR = "Sorry, the maximum number of purchases on Coinbase has been reached";
    private ProgressDialog mDialog;
    private boolean mAgreeBtcAmountVaries;

    @Override
    protected void onPreExecute() {

      super.onPreExecute();
      mDialog = ProgressDialog.show(mParent, null, getString(R.string.buysell_progress));
    }

    protected Object[] doInBackground(Object... params) {

      mAgreeBtcAmountVaries = params.length > 3 && params[3] != null;
      return doBuySell((BuySellType) params[0], (String) params[1], mAgreeBtcAmountVaries);
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
        mAmount.setText(null);

        // Sync transactions
        mParent.refresh();

        // Hide keyboard (so the user can see their new transaction)
        InputMethodManager inputMethodManager = (InputMethodManager) mParent.getSystemService(Context.INPUT_METHOD_SERVICE);
        inputMethodManager.hideSoftInputFromWindow(mParent.findViewById(android.R.id.content).getWindowToken(), 0);
      } else {

        if(result[1] != null && ((String) result[1]).trim().startsWith(BTC_AMOUNT_VARIES_ERROR)
            && !mAgreeBtcAmountVaries) {

          // Prompt to buy again, but with different message
          BuySellType type = (BuySellType) result[2];
          String amount = (String) result[3];
          ConfirmBuySellDialogFragment dialog = new ConfirmBuySellDialogFragment();
          Bundle b = new Bundle();
          b.putSerializable("type", type);
          b.putString("amount1", amount);
          b.putBoolean("agreeBtcAmountVaries", true);
          dialog.setArguments(b);
          dialog.show(getFragmentManager(), "confirm");
        } else {

          System.out.println("Does not start with '" + mAgreeBtcAmountVaries);
          Utils.showMessageDialog(getFragmentManager(), String.format(getString(R.string.buysell_error_api), (String) result[1]));
        }
      }
    }
  }

  private enum UpdatePriceFailure {
    ERROR_LOADING,
    NO_DATA_ENTERED;
  }

  private class UpdatePriceTask extends AsyncTask<String, Void, Object> {

    @Override
    protected void onPreExecute() {

      super.onPreExecute();
      mTotal.setText(null);
      mSubmitButton.setEnabled(false);
    }

    boolean mIsSingleUpdate;

    protected Object doInBackground(String... params) {

      try {

        String amount = params[0], type = params[1];

        mIsSingleUpdate = false;

        if(amount == null) {
          // Get single
          mIsSingleUpdate = true;
          amount = "1";
        }

        if(amount.isEmpty() || ".".equals(amount) || new BigDecimal(amount).doubleValue() == 0) {
          return UpdatePriceFailure.NO_DATA_ENTERED;
        }

        Collection<BasicNameValuePair> requestParams = new ArrayList<BasicNameValuePair>();
        requestParams.add(new BasicNameValuePair("qty", amount));

        JSONObject result = RpcManager.getInstance().callGet(mParent, "prices/" + type, requestParams);
        return result;

      } catch (IOException e) {

        e.printStackTrace();
      } catch (JSONException e) {

        ACRA.getErrorReporter().handleException(new RuntimeException("UpdatePrice", e));
        e.printStackTrace();
      }

      return UpdatePriceFailure.ERROR_LOADING;
    }

    protected void onPostExecute(Object result) {

      TextView target = mIsSingleUpdate ? mText2 : mTotal;
      int format = mIsSingleUpdate ? R.string.buysell_text2 : R.string.buysell_total;
      int error = mIsSingleUpdate ? R.string.buysell_text2_blank : R.string.buysell_total_error;

      if(target != null) {

        if(result instanceof UpdatePriceFailure) {

          if(result == UpdatePriceFailure.ERROR_LOADING) {
            target.setVisibility(View.VISIBLE);
            target.setText(error);
          } else if(result == UpdatePriceFailure.NO_DATA_ENTERED) {
            target.setText(null);
            target.setVisibility(View.GONE);
          }
        } else {

          target.setVisibility(View.VISIBLE);
          JSONObject json = (JSONObject) result;
          String subtotalAmount = Utils.formatCurrencyAmount(new BigDecimal(json.optJSONObject("subtotal").optString("amount")), false, CurrencyType.TRADITIONAL);
          String subtotalCurrency = json.optJSONObject("subtotal").optString("currency");

          if(mIsSingleUpdate) {

            target.setText(String.format(mParent.getString(format), subtotalAmount, subtotalCurrency));
          } else {

            String totalAmount = Utils.formatCurrencyAmount(new BigDecimal(json.optJSONObject("total").optString("amount")), false, CurrencyType.TRADITIONAL);
            String totalCurrency = json.optJSONObject("total").optString("currency");

            mSubmitButton.setEnabled(true);
            mCurrentPrice = totalAmount;
            mCurrentPriceCurrency = totalCurrency;

            // Create breakdown of transaction
            StringBuffer breakdown = new StringBuffer();
            JSONArray fees = json.optJSONArray("fees");

            breakdown.append("<font color=\"#757575\">");
            breakdown.append("Subtotal: $" + subtotalAmount + " " + subtotalCurrency + "<br>");

            for(int i = 0; i < fees.length(); i++) {
              JSONObject fee = fees.optJSONObject(i);
              String type = (String) fee.keys().next();
              String amount = fee.optJSONObject(type).optString("amount");
              String currency = fee.optJSONObject(type).optString("currency");
              breakdown.append(type.substring(0, 1).toUpperCase(Locale.CANADA)).append(type.substring(1)).append(" fee: $");
              breakdown.append(Utils.formatCurrencyAmount(new BigDecimal(amount), false, CurrencyType.TRADITIONAL));
              breakdown.append(' ').append(currency).append("<br>");
            }

            breakdown.append("</font>");
            breakdown.append("Total: $" + totalAmount + " " + totalCurrency);

            target.setText(Html.fromHtml(breakdown.toString()));
          }

          return;
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
    mTotal.setVisibility(View.GONE);
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

  protected void startBuySellTask(BuySellType type, String amount, boolean agreeBtcAmountVaries) {

    Utils.runAsyncTaskConcurrently(new DoBuySellTask(), type, amount, agreeBtcAmountVaries);
  }

  private Object[] doBuySell(BuySellType type, String amount, boolean agreeBtcAmountVaries) {

    List<BasicNameValuePair> params = new ArrayList<BasicNameValuePair>();
    params.add(new BasicNameValuePair("qty", amount));

    if(type == BuySellType.BUY && agreeBtcAmountVaries) {
      params.add(new BasicNameValuePair("agree_btc_amount_varies", "true"));
    }

    try {
      JSONObject response = RpcManager.getInstance().callPost(mParent, type.getRequestType() + "s", params);

      boolean success = response.getBoolean("success");

      if(success) {

        return new Object[] { true, amount, type };
      } else {

        String errorMessage = response.getJSONArray("errors").optString(0);
        return new Object[] { false, errorMessage, type, amount };
      }
    } catch (IOException e) {
      e.printStackTrace();
    } catch (JSONException e) {
      ACRA.getErrorReporter().handleException(new RuntimeException("doBuySell", e));
      e.printStackTrace();
    }

    // There was an exception
    return new Object[] { false, getString(R.string.buysell_error_exception), type, amount };
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
