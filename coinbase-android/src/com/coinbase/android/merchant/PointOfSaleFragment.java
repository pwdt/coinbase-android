package com.coinbase.android.merchant;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.ViewFlipper;

import com.coinbase.android.CoinbaseFragment;
import com.coinbase.android.Constants;
import com.coinbase.android.MainActivity;
import com.coinbase.android.R;
import com.coinbase.android.Utils;
import com.coinbase.api.LoginManager;
import com.coinbase.api.RpcManager;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;

import org.apache.http.message.BasicNameValuePair;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class PointOfSaleFragment extends Fragment implements CoinbaseFragment {

  private class CreateButtonTask extends AsyncTask<String, Void, Object> {

    @Override
    protected Object doInBackground(String... strings) {

      String amount = strings[0];
      String currency = strings[1];
      String title = strings[2];

      if (title == null || title.trim().equals("")) {
        title = "Merchant did not enter a title for this transaction (Android point of sale)";
      }

      List<BasicNameValuePair> params = new ArrayList<BasicNameValuePair>();

      params.add(new BasicNameValuePair("button[name]", title));
      params.add(new BasicNameValuePair("button[description]", title));
      params.add(new BasicNameValuePair("button[price_string]", amount));
      params.add(new BasicNameValuePair("button[price_currency_iso]", currency));
      params.add(new BasicNameValuePair("button[custom]", "coinbase_android_point_of_sale"));

      try {
        JSONObject response = RpcManager.getInstance().callPost(mParent, "buttons", params).optJSONObject("button");
        String button = response.optString("code");

        System.out.println(response.toString(5));

        JSONObject orderResponse = RpcManager.getInstance().callPost(mParent,
                "buttons/" + button + "/create_order", new ArrayList<BasicNameValuePair>());

        return orderResponse;

      } catch (Exception e) {

        e.printStackTrace();
        return e;
      }
    }

    @Override
    protected void onPostExecute(Object o) {

      if (o == null) {

        showResult(false, R.string.pos_result_failure_creation);
      } else if (o instanceof Exception) {

        showResult(false, mParent.getString(R.string.pos_result_failure_creation_exception, ((Exception) o).getMessage()));
      } else {

        JSONObject result = (JSONObject) o;

        if (!result.optBoolean("success")) {

          showResult(false, mParent.getString(R.string.pos_result_failure_creation_exception, result.toString()));
        } else {

          try {
            populateAccept(result.getJSONObject("order"));
          } catch (JSONException e) {

            showResult(false, mParent.getString(R.string.pos_result_failure_creation_exception, e.getMessage()));
          }
        }
      }
    }
  }

  private class LoadMerchantInfoTask extends AsyncTask<Void, Void, Object[]> {

    @Override
    protected Object[] doInBackground(Void... arg0) {

      try {
        // 1. Load merchant info
        JSONObject response = RpcManager.getInstance().callGet(mParent, "users");
        JSONObject userInfo = response.getJSONArray("users").getJSONObject(0).getJSONObject("user");
        JSONObject merchantInfo = userInfo.getJSONObject("merchant");

        // 2. if possible, load logo
        if (merchantInfo.optJSONObject("logo") != null) {
         try {

           String logoUrlString = merchantInfo.getJSONObject("logo").getString("small");
           URL logoUrl = logoUrlString.startsWith("/") ? new URL(new URL(LoginManager.CLIENT_BASEURL), logoUrlString) : new URL(logoUrlString);
           Bitmap logo = BitmapFactory.decodeStream(logoUrl.openStream());
           return new Object[] { merchantInfo, logo };
         } catch (Exception e) {
           // Could not load logo
           e.printStackTrace();
           return new Object[] { merchantInfo, null };
         }
        } else {
          // No logo
          return new Object[] { merchantInfo, null };
        }

      } catch (Exception e) {
        // Could not load merchant info
        e.printStackTrace();
        return null;
      }
    }

    @Override
    protected void onPostExecute(Object[] result) {

      if (result == null) {

        // Data could not be loaded.
        for (View header : mHeaders) {
          header.setVisibility(View.GONE);
        }
      } else {

        for (View header : mHeaders) {
          header.setVisibility(View.VISIBLE);
        }

        String title = ((JSONObject) result[0]).optString("company_name");
        for (TextView titleView : mHeaderTitles) {
          titleView.setText(title);
        }

        if (result[1] != null) {
          for (ImageView logoView : mHeaderLogos) {
            logoView.setImageBitmap((Bitmap) result[1]);
          }
        }
      }
    }
  }

  private static final int INDEX_MAIN = 0;
  private static final int INDEX_LOADING = 1;
  private static final int INDEX_ACCEPT = 2;
  private static final int INDEX_RESULT = 3;

  private MainActivity mParent;

  private EditText mAmount, mNotes;
  private Button mSubmit;
  private Spinner mCurrency;
  private String[] mCurrenciesArray;
  private ViewFlipper mFlipper;

  private ImageView mAcceptQr;
  private TextView mAcceptDesc;
  private Button mAcceptCancel;

  private View[] mHeaders;
  private TextView[] mHeaderTitles;
  private ImageView[] mHeaderLogos;

  @Override
  public void onSwitchedTo() {

  }

  @Override
  public void onPINPromptSuccessfulReturn() {

  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

    // Inflate the layout for this fragment
    View view = inflater.inflate(R.layout.fragment_point_of_sale, container, false);

    mAmount = (EditText) view.findViewById(R.id.pos_amt);
    mNotes = (EditText) view.findViewById(R.id.pos_notes);
    mSubmit = (Button) view.findViewById(R.id.pos_submit);
    mCurrency = (Spinner) view.findViewById(R.id.pos_currency);
    mFlipper = (ViewFlipper) view.findViewById(R.id.pos_flipper);

    mAcceptCancel = (Button) view.findViewById(R.id.pos_accept_cancel);
    mAcceptQr = (ImageView) view.findViewById(R.id.pos_accept_qr);
    mAcceptDesc = (TextView) view.findViewById(R.id.pos_accept_desc);

    mSubmit.setOnClickListener(new View.OnClickListener() {

      @Override
      public void onClick(View v) {

        mFlipper.setDisplayedChild(INDEX_LOADING);
        new CreateButtonTask().execute(mAmount.getText().toString(),
                (String) mCurrency.getSelectedItem(), mNotes.getText().toString());
      }
    });
    
    initializeCurrencySpinner();
    mCurrency.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {

      @Override
      public void onItemSelected(AdapterView<?> arg0, View arg1, int arg2, long arg3) {

        updateAmountHint();
      }

      @Override
      public void onNothingSelected(AdapterView<?> arg0) {
        // Ignore
      }});

    // Headers
    int[] headers = { R.id.pos_accept_header, R.id.pos_main_header };

    mHeaders = new View[headers.length];
    mHeaderTitles = new TextView[headers.length];
    mHeaderLogos = new ImageView[headers.length];
    for (int i = 0; i < headers.length; i++) {

      mHeaders[i] = view.findViewById(headers[i]);
      mHeaderTitles[i] = (TextView) mHeaders[i].findViewById(R.id.pos_header_name);
      mHeaderLogos[i] = (ImageView) mHeaders[i].findViewById(R.id.pos_header_logo);

      mHeaderTitles[i].setText(null);
      mHeaderLogos[i].setImageDrawable(null);
    }

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

  private void populateAccept(JSONObject order) throws JSONException {

    String receiveAddress = order.getString("receive_address");
    String amount = Double.toString(order.getJSONObject("total_btc").getDouble("cents") / 100d);
    String bitcoinUri = String.format("bitcoin:%1$s?amount=%2$s", receiveAddress, amount);

    Bitmap bitmap;
    try {
      bitmap = Utils.createBarcode(bitcoinUri, BarcodeFormat.QR_CODE, 512, 512);
    } catch (WriterException e) {
      e.printStackTrace();
      bitmap = null;
    }
    mAcceptQr.setImageBitmap(bitmap);

    mFlipper.setDisplayedChild(INDEX_ACCEPT);
  }

  private void showResult(boolean success, int message) {
    showResult(success, mParent.getString(message));
  }

  private void showResult(boolean success, String message) {

    // TODO
    mFlipper.setDisplayedChild(INDEX_RESULT);
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

    new LoadMerchantInfoTask().execute();
  }
}
