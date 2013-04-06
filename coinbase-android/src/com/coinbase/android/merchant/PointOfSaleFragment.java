package com.coinbase.android.merchant;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Locale;

import org.acra.ACRA;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import com.coinbase.android.CoinbaseFragment;
import com.coinbase.android.Constants;
import com.coinbase.android.MainActivity;
import com.coinbase.android.R;
import com.coinbase.android.TransferFragment;
import com.coinbase.android.Utils;
import com.coinbase.android.Utils.CurrencyType;
import com.coinbase.api.RpcManager;

public class PointOfSaleFragment extends Fragment implements CoinbaseFragment {

  private class RefreshExchangeRatesTask extends AsyncTask<Void, Void, JSONObject> {

    @Override
    protected JSONObject doInBackground(Void... params) {

      try {

        JSONObject exchangeRates = RpcManager.getInstance().callGet(mParent, "currencies/exchange_rates");
        return exchangeRates;
      } catch (IOException e) {
        e.printStackTrace();
      } catch (JSONException e) {
        ACRA.getErrorReporter().handleException(new RuntimeException("RefreshExchangeRate", e));
        e.printStackTrace();
      }

      return null;
    }

    @Override
    protected void onPostExecute(JSONObject result) {

      mExchangeRates = null;

      if(result != null) {
        mExchangeRates = result;
        mExchangeRatesUpdateTime = System.currentTimeMillis();
        updateBtcDisplay();
      }
    }

  }

  private MainActivity mParent;
  private JSONObject mExchangeRates;
  private long mExchangeRatesUpdateTime = -1;
  private RefreshExchangeRatesTask mExchangeRatesTask = null;

  private TextView mBtcDisplay;
  private EditText mAmount, mNotes;
  private Button mSubmitEmail, mSubmitQr, mSubmitNfc;
  private Spinner mCurrency;
  private String[] mCurrenciesArray;

  @Override
  public void onSwitchedTo() {

  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

    // Inflate the layout for this fragment
    View view = inflater.inflate(R.layout.fragment_point_of_sale, container, false);

    mBtcDisplay = (TextView) view.findViewById(R.id.pos_btc);
    mAmount = (EditText) view.findViewById(R.id.pos_amt);
    mNotes = (EditText) view.findViewById(R.id.pos_notes);
    mSubmitEmail = (Button) view.findViewById(R.id.pos_request_email);
    mSubmitQr = (Button) view.findViewById(R.id.pos_request_qr);
    mSubmitNfc = (Button) view.findViewById(R.id.pos_request_nfc);
    mCurrency = (Spinner) view.findViewById(R.id.pos_currency);

    mAmount.addTextChangedListener(new TextWatcher() {

      @Override
      public void afterTextChanged(Editable arg0) {
      }

      @Override
      public void beforeTextChanged(CharSequence arg0, int arg1, int arg2, int arg3) {
      }

      @Override
      public void onTextChanged(CharSequence arg0, int arg1, int arg2, int arg3) {

        updateBtcDisplay();
      }
    });

    mSubmitEmail.setOnClickListener(new View.OnClickListener() {

      @Override
      public void onClick(View v) {

        mParent.getTransferFragment().startEmailRequest(getBtcAmount(), mNotes.getText().toString());
      }
    });

    mSubmitQr.setOnClickListener(new View.OnClickListener() {

      @Override
      public void onClick(View v) {

        mParent.getTransferFragment().startQrNfcRequest(false, getBtcAmount(), mNotes.getText().toString());
      }
    });

    mSubmitNfc.setOnClickListener(new View.OnClickListener() {

      @Override
      public void onClick(View v) {

        mParent.getTransferFragment().startQrNfcRequest(true, getBtcAmount(), mNotes.getText().toString());
      }
    });
    
    initializeCurrencySpinner();
    mCurrency.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {

      @Override
      public void onItemSelected(AdapterView<?> arg0, View arg1, int arg2, long arg3) {

        updateAmountHint();
        updateBtcDisplay();
      }

      @Override
      public void onNothingSelected(AdapterView<?> arg0) {
        // Ignore
      }});

    return view;
  }

  private void initializeCurrencySpinner() {

    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mParent);
    int activeAccount = prefs.getInt(Constants.KEY_ACTIVE_ACCOUNT, -1);
    String nativeCurrency = prefs.getString(String.format(Constants.KEY_ACCOUNT_NATIVE_CURRENCY, activeAccount),
        "usd").toUpperCase(Locale.CANADA);

    mCurrenciesArray = new String[] {
                                     "BTC",
                                     nativeCurrency,
    };

    ArrayAdapter<String> arrayAdapter = new ArrayAdapter<String>(
        mParent, R.layout.fragment_transfer_currency, Arrays.asList(mCurrenciesArray)) {

      @Override
      public View getView(int position, View convertView, ViewGroup parent) {

        TextView view = (TextView) super.getView(position, convertView, parent);
        view.setText(mCurrenciesArray[position]);
        return view;
      }

      @Override
      public View getDropDownView(int position, View convertView, ViewGroup parent) {

        TextView view = (TextView) super.getDropDownView(position, convertView, parent);
        view.setText(mCurrenciesArray[position]);
        return view;
      }
    };
    arrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    mCurrency.setAdapter(arrayAdapter);
  }

  @Override
  public void onAttach(Activity activity) {
    super.onAttach(activity);

    mParent = (MainActivity) activity;
  }

  private void updateBtcDisplay() {

    if(mBtcDisplay == null) {
      return;
    }

    if((System.currentTimeMillis() - mExchangeRatesUpdateTime) > TransferFragment.EXCHANGE_RATE_EXPIRE_TIME) {

      mBtcDisplay.setText(null);
      setButtonsEnabled(false);

      // Need to fetch exchange rates again
      if(mExchangeRatesTask != null) {
        return;
      }

      Utils.runAsyncTaskConcurrently(new RefreshExchangeRatesTask());
      return;
    }

    String displayAmount;
    CurrencyType currencyType;
    int string;
    if(mCurrency.getSelectedItemPosition() == 0) {
      displayAmount = getNativeAmount();
      currencyType = CurrencyType.TRADITIONAL;
      string = R.string.pos_traditional;
    } else {
      displayAmount = getBtcAmount();
      currencyType = CurrencyType.BTC;
      string = R.string.pos_btc;
    }

    if(displayAmount == null) {

      mBtcDisplay.setText(null);
      setButtonsEnabled(false);
      return;
    }

    setButtonsEnabled(true);
    mBtcDisplay.setText(String.format(getString(string), Utils.formatCurrencyAmount(
      new BigDecimal(displayAmount),
      false,
      currencyType), mCurrenciesArray[1]));
  }

  private void setButtonsEnabled(boolean enabled) {

    mSubmitEmail.setEnabled(enabled);
    mSubmitQr.setEnabled(enabled);
    mSubmitNfc.setEnabled(enabled);
  }

  private String getBtcAmount() {

    if(mExchangeRates == null) {
      return null;
    }
 
    String enteredAmount = mAmount.getText().toString();

    if("".equals(enteredAmount) || ".".equals(enteredAmount)) {
      return null;
    }

    if(mCurrency.getSelectedItemPosition() == 0) {
      // Prices are entered in BTC
      return mAmount.getText().toString();
    }

    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mParent);
    int activeAccount = prefs.getInt(Constants.KEY_ACTIVE_ACCOUNT, -1);
    String nativeCurrency = prefs.getString(String.format(Constants.KEY_ACCOUNT_NATIVE_CURRENCY, activeAccount),
        "usd").toLowerCase(Locale.CANADA);
    String nativeToBtc = mExchangeRates.optString(nativeCurrency + "_to_btc");

    if(nativeToBtc == null) {
      return null;
    }

    String btcAmount = new BigDecimal(enteredAmount).multiply(new BigDecimal(nativeToBtc)).toString();
    return btcAmount;
  }

  private String getNativeAmount() {

    if(mExchangeRates == null) {
      return null;
    }
 
    String enteredAmount = mAmount.getText().toString();

    if("".equals(enteredAmount) || ".".equals(enteredAmount)) {
      return null;
    }

    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mParent);
    int activeAccount = prefs.getInt(Constants.KEY_ACTIVE_ACCOUNT, -1);
    String nativeCurrency = prefs.getString(String.format(Constants.KEY_ACCOUNT_NATIVE_CURRENCY, activeAccount),
        "usd").toLowerCase(Locale.CANADA);
    String rate = mExchangeRates.optString("btc_to_" + nativeCurrency);

    if(rate == null) {
      return null;
    }

    String amount = new BigDecimal(enteredAmount).multiply(new BigDecimal(rate)).toString();
    return amount;
  }

  @Override
  public void onStart() {
    super.onStart();

    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mParent);
    int activeAccount = prefs.getInt(Constants.KEY_ACTIVE_ACCOUNT, -1);
    String notes = prefs.getString(String.format(Constants.KEY_ACCOUNT_POS_NOTES, activeAccount), "");
    boolean btcPrices = prefs.getBoolean(String.format(Constants.KEY_ACCOUNT_POS_BTC_AMT, activeAccount), false);
    mNotes.setText(notes);
    mCurrency.setSelection(btcPrices ? 0 : 1);
  }

  @Override
  public void onStop() {
    super.onStop();

    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mParent);
    int activeAccount = prefs.getInt(Constants.KEY_ACTIVE_ACCOUNT, -1);
    Editor e = prefs.edit();
    e.putString(String.format(Constants.KEY_ACCOUNT_POS_NOTES, activeAccount), mNotes.getText().toString());
    e.putBoolean(String.format(Constants.KEY_ACCOUNT_POS_BTC_AMT, activeAccount), mCurrency.getSelectedItemPosition() == 0);
    e.commit();
  }
  
  private void updateAmountHint() {

    // Update text hint
    String currency = mCurrenciesArray[mCurrency.getSelectedItemPosition()];
    mAmount.setHint(String.format(getString(R.string.pos_amt), currency.toUpperCase(Locale.CANADA)));
  }

  public void refresh() {

    updateAmountHint();
    updateBtcDisplay();
  }
}
