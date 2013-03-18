package com.coinbase.android.merchant;

import java.io.IOException;
import java.math.BigDecimal;
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
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.coinbase.android.CoinbaseFragment;
import com.coinbase.android.Constants;
import com.coinbase.android.MainActivity;
import com.coinbase.android.R;
import com.coinbase.android.TransferFragment;
import com.coinbase.android.Utils;
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

    return view;
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

    String btcAmount = getBtcAmount();

    if(btcAmount == null) {

      mBtcDisplay.setText(null);
      setButtonsEnabled(false);
      return;
    }

    setButtonsEnabled(true);
    mBtcDisplay.setText(String.format(getString(R.string.pos_btc), Utils.formatCurrencyAmount(btcAmount)));
  }

  private void setButtonsEnabled(boolean enabled) {

    mSubmitEmail.setEnabled(enabled);
    mSubmitQr.setEnabled(enabled);
    mSubmitNfc.setEnabled(enabled);
  }

  private String getBtcAmount() {

    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mParent);
    int activeAccount = prefs.getInt(Constants.KEY_ACTIVE_ACCOUNT, -1);
    String nativeCurrency = prefs.getString(String.format(Constants.KEY_ACCOUNT_NATIVE_CURRENCY, activeAccount),
        "usd").toLowerCase(Locale.CANADA);
    String nativeCurrencyAmount = mAmount.getText().toString();
    String nativeToBtc = mExchangeRates.optString(nativeCurrency + "_to_btc");

    if(nativeToBtc == null || "".equals(nativeCurrencyAmount) || ".".equals(nativeCurrencyAmount)) {
      return null;
    }

    String btcAmount = new BigDecimal(nativeCurrencyAmount).multiply(new BigDecimal(nativeToBtc)).toString();
    return btcAmount;
  }

  @Override
  public void onStart() {
    super.onStart();

    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mParent);
    int activeAccount = prefs.getInt(Constants.KEY_ACTIVE_ACCOUNT, -1);
    String notes = prefs.getString(String.format(Constants.KEY_ACCOUNT_POS_NOTES, activeAccount), "");
    mNotes.setText(notes);
  }

  @Override
  public void onStop() {
    super.onStop();

    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mParent);
    int activeAccount = prefs.getInt(Constants.KEY_ACTIVE_ACCOUNT, -1);
    Editor e = prefs.edit();
    e.putString(String.format(Constants.KEY_ACCOUNT_POS_NOTES, activeAccount), mNotes.getText().toString());
    e.commit();
  }

  public void refresh() {

    // Update text hint
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mParent);
    int activeAccount = prefs.getInt(Constants.KEY_ACTIVE_ACCOUNT, -1);
    String nativeCurrency = prefs.getString(String.format(Constants.KEY_ACCOUNT_NATIVE_CURRENCY, activeAccount),
        "usd").toUpperCase(Locale.CANADA);
    mAmount.setHint(String.format(getString(R.string.pos_amt), nativeCurrency));

    updateBtcDisplay();
  }
}
