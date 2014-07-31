package com.coinbase.android.transfers;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.coinbase.android.ApiTask;
import com.coinbase.android.CoinbaseFragment;
import com.coinbase.android.Constants;
import com.coinbase.android.FontManager;
import com.coinbase.android.R;
import com.coinbase.android.Utils;
import com.coinbase.android.pin.PINManager;
import com.coinbase.android.util.BitcoinUri;
import com.coinbase.api.entity.Transaction;
import com.google.inject.Inject;

import org.joda.money.BigMoney;
import org.joda.money.BigMoneyProvider;
import org.joda.money.CurrencyUnit;
import org.joda.money.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;

import roboguice.fragment.RoboFragment;
import roboguice.inject.InjectResource;
import roboguice.inject.InjectView;

public class TransferFragment extends RoboFragment implements CoinbaseFragment {
  protected enum TransferType {
    SEND(R.string.transfer_send_money, "send"),
    REQUEST(R.string.transfer_request_money, "request");

    private int mFriendlyName;
    private String mRequestName;

    private TransferType(int friendlyName, String requestName) {

      mFriendlyName = friendlyName;
      mRequestName = requestName;
    }

    public int getName() {

      return mFriendlyName;
    }

    public String getRequestName() {

      return mRequestName;
    }
  }

  private enum TransferButton {
    SEND, EMAIL, QR, NFC;
  }

  private class RefreshExchangeRateTask extends FetchExchangeRatesTask {
    public RefreshExchangeRateTask(Context context) {
      super(context);
    }

    @Override
    public void onSuccess(Map<String, BigDecimal> rates) {
      mNativeExchangeRates = rates;
      mNativeExchangeRateTime = System.currentTimeMillis();
      doNativeCurrencyUpdate();
    }

    @Override
    public void onException(Exception ex) {
      mNativeAmount.setText(R.string.transfer_fxrate_failure);
      super.onException(ex);
    }

    @Override
    public void onFinally() {
      mNativeExchangeTask = null;
    }
  }

  public static final int EXCHANGE_RATE_EXPIRE_TIME = 60000 * 5; // Expires in 5 minutes

  private Activity mParent;

  private TransferButton mLastPressedButton = null;

  private long mNativeExchangeRateTime;
  private Map<String, BigDecimal> mNativeExchangeRates;
  private RefreshExchangeRateTask mNativeExchangeTask;
  private String[] mCurrenciesArray = new String[] { "BTC" };

  @InjectResource(R.string.title_transfer)       String mTitle;
  @InjectResource(R.string.transfer_amt_native)  String mNativeAmountFormatString;

  @InjectView(R.id.transfer_money_native)        TextView mNativeAmount;
  @InjectView(R.id.transfer_divider_1)           View mRecipientDivider;
  @InjectView(R.id.transfer_divider_3)           View mRequestDivider;
  @InjectView(R.id.transfer_money_recipient)     AutoCompleteTextView mRecipientView;
  @InjectView(R.id.transfer_money_amt)           EditText mAmountView;
  @InjectView(R.id.transfer_money_notes)         EditText mNotesView;
  @InjectView(R.id.transfer_money_currency)      Spinner mTransferCurrencyView;
  @InjectView(R.id.transfer_money_type)          Spinner mTransferTypeView;
  @InjectView(R.id.transfer_money_button_send)   Button mSubmitSend;
  @InjectView(R.id.transfer_money_button_email)  Button mSubmitEmail;
  @InjectView(R.id.transfer_money_button_qrcode) Button mSubmitQr;
  @InjectView(R.id.transfer_money_button_nfc)    Button mSubmitNfc;
  @InjectView(R.id.transfer_money_button_clear)  Button mClearButton;

  @Inject
  protected PINManager mPinManager;

  @Override
  public void onAttach(Activity activity) {
    super.onAttach(activity);
    mParent = activity;
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container,
                           Bundle savedInstanceState) {

    // Inflate the layout for this fragment
    return inflater.inflate(R.layout.fragment_transfer, container, false);

  }

  @Override
  public void onViewCreated(View view, Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    mTransferTypeView.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
      @Override
      public void onItemSelected(AdapterView<?> arg0, View arg1, int arg2,
                                 long arg3) {
        onTypeChanged();
      }

      @Override
      public void onNothingSelected(AdapterView<?> arg0) {
        // Will never happen.
        throw new RuntimeException("onNothingSelected triggered on transfer type spinner");
      }
    });
    initializeTypeSpinner();

    mTransferCurrencyView.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
      @Override
      public void onItemSelected(AdapterView<?> arg0, View arg1, int arg2,
                                 long arg3) {

        onCurrencyChanged();
      }

      @Override
      public void onNothingSelected(AdapterView<?> arg0) {
        // Will never happen.
        throw new RuntimeException("onNothingSelected triggered on transfer currency spinner");
      }
    });
    initializeCurrencySpinner();

    for(Button b : new Button[] { mSubmitSend, mSubmitEmail, mSubmitNfc, mSubmitQr }) {
      b.setTypeface(FontManager.getFont(mParent, "Roboto-Light"));
    }

    mRecipientView.setAdapter(Utils.getEmailAutocompleteAdapter(mParent));
    mRecipientView.setThreshold(0);

    mNativeAmount.setText(null);

    mAmountView.addTextChangedListener(new TextWatcher() {
      @Override
      public void afterTextChanged(Editable s) {
        // Update native currency
        updateNativeCurrency();
      }

      @Override
      public void beforeTextChanged(CharSequence s, int start, int count,
                                    int after) {
      }

      @Override
      public void onTextChanged(CharSequence s, int start, int before, int count) {
      }
    });

    if (Utils.getPrefsBool(mParent, Constants.KEY_ACCOUNT_TRANSFER_CURRENCY_BTC, false)) {
      mTransferCurrencyView.setSelection(1); // BTC is always second
    } else {
      mTransferCurrencyView.setSelection(0);
    }

    mSubmitSend.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        mLastPressedButton = TransferButton.SEND;
        submitSend();
      }
    });

    mSubmitEmail.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        mLastPressedButton = TransferButton.EMAIL;
        submitEmail();
      }
    });

    mSubmitQr.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        mLastPressedButton = TransferButton.QR;
        submitQr();
      }
    });

    mSubmitNfc.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        mLastPressedButton = TransferButton.NFC;
        submitNfc();
      }
    });

    mClearButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        clearForm();
      }
    });

    onTypeChanged();
    // TODO listen to account data change event to refresh currency spinner
  }

  private void submitSend() {
    BigMoney amount = getEnteredAmount();
    if (null == amount) {
      // No amount entered
      Toast.makeText(mParent, R.string.transfer_amt_empty, Toast.LENGTH_SHORT).show();
      return;
    }

    Money roundedAmount = amount.toMoney(RoundingMode.HALF_EVEN);

    String recipient = getEnteredRecipient();
    if (recipient == null || "".equals(recipient)) {
      // No recipient entered
      Toast.makeText(mParent, R.string.transfer_recipient_empty, Toast.LENGTH_SHORT).show();
      return;
    }

    if(!mPinManager.checkForEditAccess(getActivity())) {
      return;
    }
    mLastPressedButton = null;

    // TODO delayed
    /*
    if(!Utils.isConnectedOrConnecting(mParent)) {
      // Internet is not available
      // Show error message and display option to do a delayed transaction
      new DelayedTransactionDialogFragment(
              new DelayedSendTransaction(recipient, roundedAmount, null, getEnteredNotes()))
              .show(getFragmentManager(), "delayed_send");
      return;
    }
    */

    ConfirmSendTransferFragment dialog = ConfirmSendTransferFragment.newInstance(recipient, roundedAmount, null, getEnteredNotes());
    dialog.show(getFragmentManager(), "confirm");
  }

  private void submitEmail() {
    BigMoney amount = getEnteredAmount();
    if (null == amount) {
      // No amount entered
      Toast.makeText(mParent, R.string.transfer_amt_empty, Toast.LENGTH_SHORT).show();
      return;
    }

    Money roundedAmount = amount.toMoney(RoundingMode.HALF_EVEN);

    if(!mPinManager.checkForEditAccess(getActivity())) {
      return;
    }
    mLastPressedButton = null;

    EmailPromptFragment dialog = EmailPromptFragment.newInstance(roundedAmount, getEnteredNotes());
    dialog.show(getFragmentManager(), "requestEmail");
  }

  private void submitQr() {
    if(!mPinManager.checkForEditAccess(getActivity())) {
      return;
    }
    mLastPressedButton = null;


    /* TODO
    DisplayQrFragment f = new DisplayQrFragment(generateRequestUri());
    f.show(getFragmentManager(), "qrrequest");
    */

    // After using a receive address, generate a new one for next time.
    // TODO regenerate address here
    // mParent.getAccountSettingsFragment().regenerateReceiveAddress();
  }

  private void submitNfc() {
    if(!mPinManager.checkForEditAccess(getActivity())) {
      return;
    }
    mLastPressedButton = null;

    String requestUri = generateRequestUri().toString();

    /* TODO
    DisplayNfcFragment f = new DisplayNfcFragment(requestUri);
    f.show(getFragmentManager(), "nfcrequest");
    */

    // After using a receive address, generate a new one for next time.
    // TODO regenerate address here
    // mParent.getAccountSettingsFragment().regenerateReceiveAddress();
  }

  public void clearForm() {
    mAmountView.setText("");
    mNotesView.setText("");
    mRecipientView.setText("");
    mNativeAmount.setText("");
  }

  private void updateNativeCurrency() {
    if(mNativeExchangeRates == null ||
        (System.currentTimeMillis() - mNativeExchangeRateTime) > EXCHANGE_RATE_EXPIRE_TIME) {

      // Need to fetch exchange rate again
      if(mNativeExchangeTask != null) {
        return;
      }

      if (Utils.isConnectedOrConnecting(mParent)) {
        mNativeAmount.setText(R.string.transfer_fxrate_loading);
        refreshExchangeRate();
      } else {
        // No point in trying to load FX rate
        mNativeAmount.setText(R.string.transfer_fxrate_failure);
      }
    } else {
      doNativeCurrencyUpdate();
    }
  }

  private void doNativeCurrencyUpdate() {
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mParent);
    int activeAccount = prefs.getInt(Constants.KEY_ACTIVE_ACCOUNT, -1);
    String nativeCurrencyCode = prefs.getString(String.format(Constants.KEY_ACCOUNT_NATIVE_CURRENCY, activeAccount),
        "USD").toUpperCase();

    BigMoney enteredAmount = getEnteredAmount();
    if (enteredAmount == null) {
      mNativeAmount.setText("");
      return;
    }

    CurrencyUnit BTC              = CurrencyUnit.getInstance("BTC");
    CurrencyUnit nativeCurrency   = CurrencyUnit.getInstance(nativeCurrencyCode);
    CurrencyUnit selectedCurrency = enteredAmount.getCurrencyUnit();
    CurrencyUnit otherCurrency    = selectedCurrency == nativeCurrency ? BTC : nativeCurrency;

    String exchangeRateKey = String.format(
            "%s_to_%s",
            selectedCurrency.getCurrencyCode().toLowerCase(),
            otherCurrency.getCurrencyCode().toLowerCase()
    );

    Money converted = enteredAmount
                        .convertedTo(otherCurrency, mNativeExchangeRates.get(exchangeRateKey))
                        .toMoney(RoundingMode.HALF_EVEN);

    mNativeAmount.setText(
            String.format(mNativeAmountFormatString, Utils.formatMoneyRounded(converted))
    );
  }

  @TargetApi(Build.VERSION_CODES.HONEYCOMB)
  private void refreshExchangeRate() {
    mNativeExchangeTask = new RefreshExchangeRateTask(this.getActivity());
    mNativeExchangeTask.execute();
  }

  private BigMoney getEnteredAmount() {
    BigMoney quantity = null;
    try {
      quantity = BigMoney.of(
              getSelectedCurrency(),
              new BigDecimal(mAmountView.getText().toString()).stripTrailingZeros()
      );
      // Only positive quantities are valid
      if (quantity.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
        quantity = null;
      }
    } catch (Exception ex) {}
    return quantity;
  }

  private Money getEnteredAmountBtc() {
    BigMoneyProvider amountProvider = getEnteredAmount();

    if (amountProvider == null) {
      return null;
    }

    BigMoney amount = amountProvider.toBigMoney();

    CurrencyUnit BTC = CurrencyUnit.getInstance("BTC");

    if (BTC != amount.getCurrencyUnit()) {
      String key = String.format("%s_to_btc", amount.getCurrencyUnit().getCurrencyCode());
      if (mNativeExchangeRates == null) {
        Toast.makeText(mParent, R.string.exchange_rate_error, Toast.LENGTH_SHORT).show();
        return null;
      }
      amount = amount.convertedTo(BTC, mNativeExchangeRates.get(key));
    }

    return amount.toMoney(RoundingMode.HALF_EVEN);
  }

  private String generateRequestUri() {
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mParent);
    int activeAccount = prefs.getInt(Constants.KEY_ACTIVE_ACCOUNT, -1);
    String receiveAddress = prefs.getString(String.format(Constants.KEY_ACCOUNT_RECEIVE_ADDRESS, activeAccount), null);

    BitcoinUri result = new BitcoinUri();
    result.setAmount(getEnteredAmountBtc().getAmount());
    result.setMessage(getEnteredNotes());
    result.setAddress(receiveAddress);

    return result.toString();
  }

  private void initializeTypeSpinner() {
    ArrayAdapter<TransferType> arrayAdapter = new ArrayAdapter<TransferType>(
        mParent, R.layout.fragment_transfer_type, Arrays.asList(TransferType.values())) {

      @Override
      public View getView(int position, View convertView, ViewGroup parent) {

        TextView view = (TextView) super.getView(position, convertView, parent);
        view.setText(mParent.getString(TransferType.values()[position].getName()));
        return view;
      }

      @Override
      public View getDropDownView(int position, View convertView, ViewGroup parent) {

        TextView view = (TextView) super.getDropDownView(position, convertView, parent);
        view.setText(mParent.getString(TransferType.values()[position].getName()));
        return view;
      }
    };
    arrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    mTransferTypeView.setAdapter(arrayAdapter);
  }

  private void initializeCurrencySpinner() {
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mParent);
    int activeAccount = prefs.getInt(Constants.KEY_ACTIVE_ACCOUNT, -1);
    String nativeCurrency = prefs.getString(String.format(Constants.KEY_ACCOUNT_NATIVE_CURRENCY, activeAccount),
        "usd").toUpperCase(Locale.CANADA);

    mCurrenciesArray = new String[] {
            nativeCurrency,
            "BTC",
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
    mTransferCurrencyView.setAdapter(arrayAdapter);
  }

  private void onTypeChanged() {
    boolean isSend = getSelectedTransferType() == TransferType.SEND;

    mSubmitSend.setVisibility(isSend ? View.VISIBLE : View.GONE);
    mSubmitEmail.setVisibility(isSend ? View.GONE : View.VISIBLE);
    mSubmitQr.setVisibility(isSend ? View.GONE : View.VISIBLE);
    mSubmitNfc.setVisibility(isSend ? View.GONE : View.VISIBLE);
    mRecipientView.setVisibility(isSend ? View.VISIBLE : View.GONE);
    mRequestDivider.setVisibility(isSend ? View.GONE : View.VISIBLE);
    mRecipientDivider.setVisibility(isSend ? View.VISIBLE : View.GONE);

    RelativeLayout.LayoutParams clearParams = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
    clearParams.addRule(RelativeLayout.BELOW, isSend ? R.id.transfer_money_recipient : R.id.transfer_money_notes);
    clearParams.addRule(RelativeLayout.ALIGN_LEFT, isSend ? R.id.transfer_money_recipient : R.id.transfer_money_notes);
    clearParams.topMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10, getResources().getDisplayMetrics());
    mClearButton.setLayoutParams(clearParams);

    doFocus();
  }

  private void onCurrencyChanged() {
    boolean isBTC = getSelectedCurrency().getCurrencyCode().equalsIgnoreCase("BTC");
    Utils.putPrefsBool(mParent, Constants.KEY_ACCOUNT_TRANSFER_CURRENCY_BTC, isBTC);
    updateNativeCurrency();
  }

  private Transaction buildTransaction() {
    Transaction result = new Transaction();
    result.setAmount(getEnteredAmountBtc());
    result.setNotes(getEnteredNotes());
    // TODO result.setUserFee(feeAmount);

    switch(getSelectedTransferType()) {
      case SEND:
        result.setTo(getEnteredRecipient());
        break;
      case REQUEST:
        result.setFrom(getEnteredRecipient());
        break;
    }

    return result;
  }

  public void fillFormForBitcoinUri(String content) {
    BigDecimal amount = null;
    String message = null, address = null;

    try {
      BitcoinUri bitcoinUri = BitcoinUri.parse(content);
      amount = bitcoinUri.getAmount();
      message = bitcoinUri.getMessage();
      address = bitcoinUri.getAddress();
    } catch (BitcoinUri.InvalidBitcoinUriException ex) {
      // Assume barcode consisted of a bitcoin address only (not a URI)
      address = content;
    }

    if(address == null) {
      Log.e("Coinbase", "Could not parse URI! (" + content + ")");
      return;
    }

    if(mTransferTypeView != null) {
      switchType(TransferType.SEND);
      mTransferCurrencyView.setSelection(1); // BTC is always second
      mAmountView.setText(amount.stripTrailingZeros().toPlainString());
      mNotesView.setText(message);
      mRecipientView.setText(address);
    }
  }

  public void switchType(TransferType type) {
    if(mTransferTypeView != null) {
      mTransferTypeView.setSelection(type.ordinal());
      onTypeChanged();
    }
  }

  public CurrencyUnit getSelectedCurrency() {
    String currencyCode = (String) mTransferCurrencyView.getSelectedItem();
    return CurrencyUnit.getInstance(currencyCode.toUpperCase());
  }

  public TransferType getSelectedTransferType() {
    return (TransferType) mTransferTypeView.getSelectedItem();
  }

  public String getEnteredRecipient() {
    return mRecipientView.getText().toString();
  }

  public String getEnteredNotes() {
    return mNotesView.getText().toString();
  }

  @Override
  public void onSwitchedTo() {
    doFocus();
  }

  private void doFocus() {
    if (getSelectedTransferType() == TransferType.REQUEST) {
      mAmountView.requestFocus();
    } else {
      mRecipientView.requestFocus();
    }
  }

  @Override
  public void onPINPromptSuccessfulReturn() {
    if (mLastPressedButton != null) {
      switch (mLastPressedButton) {
        case QR:
          submitQr();
          break;
        case NFC:
          submitNfc();
          break;
        case EMAIL:
          submitEmail();
          break;
        case SEND:
          submitSend();
          break;
      }
    }
  }

  @Override
  public String getTitle() {
    return mTitle;
  }
}
