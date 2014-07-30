package com.coinbase.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.coinbase.android.pin.PINManager;
import com.coinbase.api.LoginManager;
import com.coinbase.api.entity.Quote;
import com.coinbase.api.entity.Transfer;
import com.coinbase.api.exception.CoinbaseException;
import com.google.inject.Inject;

import org.joda.money.BigMoney;
import org.joda.money.BigMoneyProvider;
import org.joda.money.CurrencyUnit;
import org.joda.money.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import roboguice.fragment.RoboDialogFragment;
import roboguice.fragment.RoboFragment;
import roboguice.inject.InjectResource;
import roboguice.inject.InjectView;
import roboguice.util.RoboAsyncTask;

public class BuySellFragment extends RoboFragment implements CoinbaseFragment {

  private enum BuySellType {
    BUY(R.string.buysell_type_buy),
    SELL(R.string.buysell_type_sell);

    private int mFriendlyName;

    private BuySellType(int friendlyName) {
      mFriendlyName = friendlyName;
    }

    public int getName() {
      return mFriendlyName;
    }
  }

  private abstract class GetQuoteTask extends ApiTask<Quote> {
    protected BigMoneyProvider mAmount;

    public GetQuoteTask(Context context, BigMoneyProvider amount) {
      super(context);
      mAmount = amount;
    }

    @Override
    public void onException(Exception ex) {
      Toast.makeText(context, R.string.buysell_type_price_error, Toast.LENGTH_SHORT).show();
      super.onException(ex);
    }

    @Override
    public void onInterrupted(Exception ex) {
      updateLabelText(null);
    }
  }

  private class GetBuyQuoteTask extends GetQuoteTask {
    public GetBuyQuoteTask(Context context, BigMoneyProvider amount) {
      super(context, amount);
    }

    @Override
    public Quote call() throws Exception {
      return getClient().getBuyQuote(mAmount.toBigMoney().toMoney());
    }
  }

  private class GetSellQuoteTask extends GetQuoteTask {
    public GetSellQuoteTask(Context context, BigMoneyProvider amount) {
      super(context, amount);
    }

    @Override
    public Quote call() throws Exception {
      return getClient().getSellQuote(mAmount.toBigMoney().toMoney());
    }
  }

  public static abstract class BuySellConfirmationDialogFragment extends ConfirmationDialogFragment {
    public static final String QUANTITY = "BUY_SELL_CONFIRMATION_DIALOG_QUANTITY";
    public static final String TOTAL = "BUY_SELL_CONFIRMATION_DIALOG_TOTAL";

    protected BigMoney mQuantity;
    protected BigMoney mTotal;

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
      mQuantity = (BigMoney) getArguments().getSerializable(QUANTITY);
      mTotal = (BigMoney) getArguments().getSerializable(TOTAL);
      return super.onCreateDialog(savedInstanceState);
    }
  }

  public static class BuyConfirmationDialogFragment extends BuySellConfirmationDialogFragment {
    @InjectResource(R.string.buysell_confirm_message_buy)
    protected String mMessageFormat;

    public BuyConfirmationDialogFragment() {}

    @Override
    public String getMessage() {
      return String.format(mMessageFormat, Utils.formatMoney(mQuantity), Utils.formatMoney(mTotal));
    }

    @Override
    public void onUserConfirm() {
      new BuyTask(getActivity(), mQuantity).execute();
    }
  }

  public static class SellConfirmationDialogFragment extends BuySellConfirmationDialogFragment {
    @InjectResource(R.string.buysell_confirm_message_sell)
    protected String mMessageFormat;

    public SellConfirmationDialogFragment() {}

    @Override
    public String getMessage() {
      return String.format(mMessageFormat, Utils.formatMoney(mQuantity), Utils.formatMoney(mTotal));
    }

    @Override
    public void onUserConfirm() {
      new SellTask(getActivity(), mQuantity).execute();
    }

    public static SellConfirmationDialogFragment newInstance(BigMoney quantity, BigMoney total) {
      SellConfirmationDialogFragment frag = new SellConfirmationDialogFragment();

      return frag;
    }
  }

  public static abstract class BuySellTask extends ApiTask<Transfer> {
    @InjectResource(R.string.buysell_error_api)
    private String mApiErrorMessage;

    @InjectResource(R.string.buysell_progress)
    private String mBuySellProgressMessage;

    private ProgressDialog mDialog;

    @Override
    protected void onPreExecute() throws Exception {
      super.onPreExecute();
      mDialog = ProgressDialog.show(context, null, mBuySellProgressMessage);
    }

    protected BuySellTask(Context context) {
      super(context);
    }

    @Override
    public void onSuccess(Transfer transfer) throws Exception {
      super.onSuccess(transfer);
      String text = String.format(getSuccessFormatString(), transfer.getBtc().getAmount().toPlainString());
      Toast.makeText(context, text, Toast.LENGTH_SHORT).show();
      // TODO update transactions
    }

    @Override
    protected void onFinally() {
      super.onFinally();
      mDialog.dismiss();
    }

    @Override
    protected void onException(Exception e) {
      super.onException(e);
      if (e instanceof CoinbaseException) {
        Toast.makeText(context, e.getMessage(), Toast.LENGTH_LONG).show();
      } else {
        Toast.makeText(context, mApiErrorMessage, Toast.LENGTH_LONG).show();
      }
    }

    public abstract String getSuccessFormatString();
  }

  public static class BuyTask extends BuySellTask  {
    @InjectResource(R.string.buysell_success_buy)
    protected String mSuccessFormat;
    protected BigMoneyProvider        mAmount;

    public BuyTask(Context context, BigMoneyProvider amount) {
      super(context);
      mAmount = amount;
    }

    @Override
    public Transfer call() throws Exception {
      return getClient().buy(mAmount.toBigMoney().toMoney(RoundingMode.HALF_EVEN));
    }

    @Override
    public String getSuccessFormatString() {
      return mSuccessFormat;
    }
  }

  public static class SellTask extends BuySellTask  {
    @InjectResource(R.string.buysell_success_sell)
    protected String mSuccessFormat;
    protected BigMoneyProvider        mAmount;

    public SellTask(Context context, BigMoneyProvider amount) {
      super(context);
      mAmount = amount;
    }

    @Override
    public Transfer call() throws Exception {
      return getClient().sell(mAmount.toBigMoney().toMoney(RoundingMode.HALF_EVEN));
    }

    @Override
    public String getSuccessFormatString() {
      return mSuccessFormat;
    }
  }

  private Activity mParent;

  private GetQuoteTask mGetQuoteTask;
  private Quote mCurrentQuote;
  private BuySellType mBuySellType;

  @InjectView(R.id.buysell_total)         private TextView mTotal;
  @InjectView(R.id.buysell_type_buy)      private TextView mTypeBuy;
  @InjectView(R.id.buysell_type_sell)     private TextView mTypeSell;
  @InjectView(R.id.buysell_submit)        private Button mSubmitButton;
  @InjectView(R.id.buysell_amount)        private EditText mAmount;
  @InjectResource(R.string.title_buysell) private String mTitle;

  @Override
  public void onAttach(Activity activity) {
    super.onAttach(activity);
    mParent = activity;
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container,
      Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_buysell, container, false);
  }

  @Override
  public void onViewCreated(View view, Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    mTypeBuy.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        switchType(BuySellType.BUY);
      }
    });

    mTypeSell.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        switchType(BuySellType.SELL);
      }
    });

    mSubmitButton.setTypeface(FontManager.getFont(mParent, "Roboto-Light"));
    mSubmitButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        submit();
      }
    });

    mAmount.addTextChangedListener(new TextWatcher() {
      private Timer timer = new Timer();
      private final long DELAY = 1000;

      @Override
      public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) {
      }

      @Override
      public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) {
      }

      @Override
      public void afterTextChanged(Editable s) {
        timer.cancel();
        timer = new Timer();
        timer.schedule(new TimerTask() {
          @Override
          public void run() {
            mParent.runOnUiThread(new Runnable() {
              @Override
              public void run() {
                getQuote();
              }
            });
          }
        }, DELAY);
      }
    });

    switchType(BuySellType.BUY);
  }

  private void updateLabelText(Quote quote) {
    final TextView target = mBuySellType == BuySellType.BUY ? mTypeBuy : mTypeSell;
    final TextView disableTarget = mBuySellType == BuySellType.BUY ? mTypeSell : mTypeBuy;

    final String base = mParent.getString(mBuySellType == BuySellType.BUY ? R.string.buysell_type_buy : R.string.buysell_type_sell);
    final String disableBase = mParent.getString(mBuySellType == BuySellType.BUY ? R.string.buysell_type_sell : R.string.buysell_type_buy);

    final Typeface light = FontManager.getFont(mParent, "Roboto-Light");

    // Target text
    final SpannableStringBuilder targetText = new SpannableStringBuilder(base);
    if (quote != null) {
      String formatString = getString(R.string.buysell_type_price);
      String price = Utils.formatMoney(quote.getSubtotal());
      targetText.append(' ').append(String.format(formatString, price));
      targetText.setSpan(new CustomTypefaceSpan("sans-serift", light), base.length(), base.length() + price.length() + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    target.setText(targetText);
    // Disable text
    disableTarget.setText(disableBase);
  }

  private void displayTotal(Quote quote) {
    String totalAmountString = Utils.formatMoney(quote.getTotal());

    // Create breakdown of transaction
    final StringBuffer breakdown = new StringBuffer();

    breakdown.append("<font color=\"#757575\">");
    breakdown.append("Subtotal: " + Utils.formatMoney(quote.getSubtotal()) + "<br>");

    for(Map.Entry<String, Money> fee : quote.getFees().entrySet()) {
      String type = fee.getKey();
      String amount = Utils.formatMoney(fee.getValue());
      breakdown.append(type.substring(0, 1).toUpperCase(Locale.CANADA)).append(type.substring(1)).append(" fee: ");
      breakdown.append(amount);
      breakdown.append("<br>");
    }

    breakdown.append("</font>");
    breakdown.append("Total: " + totalAmountString);

    mSubmitButton.setEnabled(true);
    mTotal.setVisibility(View.VISIBLE);
    mTotal.setText(Html.fromHtml(breakdown.toString()));
  }

  private void getQuote() {
    if (mGetQuoteTask != null) {
      mGetQuoteTask.cancel(true);
    }
    mSubmitButton.setEnabled(false);
    mTotal.setText(null);
    mTotal.setVisibility(View.GONE);

    BigMoneyProvider quantity = getQuantityEntered();

    // If no quantity is entered, get generic 1 BTC quote
    if (null == quantity) {
      BigMoneyProvider ONE_BTC = BigMoney.of(CurrencyUnit.getInstance("BTC"), BigDecimal.ONE);

      switch(mBuySellType) {
        case BUY:
          mGetQuoteTask = new GetBuyQuoteTask(mParent, ONE_BTC) {
            @Override
            public void onSuccess(Quote quote) {
              updateLabelText(quote);
            }
          };
          break;
        case SELL:
          mGetQuoteTask = new GetSellQuoteTask(mParent, ONE_BTC) {
            @Override
            public void onSuccess(Quote quote) {
              updateLabelText(quote);
            }
          };
          break;
      }
    } else {
      switch(mBuySellType) {
        case BUY:
          mGetQuoteTask = new GetBuyQuoteTask(mParent, quantity) {
            @Override
            public void onSuccess(Quote quote) {
              mCurrentQuote = quote;
              displayTotal(quote);
            }
          };
          break;
        case SELL:
          mGetQuoteTask = new GetSellQuoteTask(mParent, quantity) {
            @Override
            public void onSuccess(Quote quote) {
              mCurrentQuote = quote;
              displayTotal(quote);
            }
          };
          break;
      }
    }

    mGetQuoteTask.execute();
  }

  private void switchType(BuySellType newType) {
    mBuySellType = newType;

    float buyWeight = mBuySellType == BuySellType.BUY ? 1 : 0;
    float sellWeight = mBuySellType == BuySellType.SELL ? 1 : 0;
    ((LinearLayout.LayoutParams) mTypeBuy.getLayoutParams()).weight = buyWeight;
    ((LinearLayout.LayoutParams) mTypeSell.getLayoutParams()).weight = sellWeight;

    // Remove prices from labels
    updateLabelText(null);

    // Swap views
    LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) mTypeBuy.getLayoutParams();
    LinearLayout parent = (LinearLayout) mTypeBuy.getParent();
    View divider = parent.findViewById(R.id.buysell_divider_2);
    parent.removeView(mTypeBuy);
    parent.removeView(divider);
    parent.addView(mTypeBuy, mBuySellType == BuySellType.BUY ? 0 : 1, params);
    parent.addView(divider, 1);

    // Text color
    TextView active = mBuySellType == BuySellType.BUY ? mTypeBuy : mTypeSell;
    TextView inactive = mBuySellType == BuySellType.BUY ? mTypeSell : mTypeBuy;
    active.setTextColor(getResources().getColor(R.color.buysell_type_active));
    inactive.setTextColor(getResources().getColor(R.color.buysell_type_inactive));

    int submitLabel = mBuySellType == BuySellType.BUY ? R.string.buysell_submit_buy : R.string.buysell_submit_sell;
    mSubmitButton.setText(submitLabel);

    getQuote();
  }

  @Override
  public void onSwitchedTo() {
    // Focus text field
    mAmount.requestFocus();
    getQuote();
  }

  @Override
  public void onPINPromptSuccessfulReturn() {
    submit();
  }

  @Override
  public String getTitle() { return mTitle; }

  private void submit() {
    final BigMoney quantity = getQuantityEntered();

    if (null == quantity) {
      return;
    }

    if(!PINManager.getInstance().checkForEditAccess(getActivity())) {
      return;
    }

    ConfirmationDialogFragment dialog;

    switch(mBuySellType) {
      case BUY:
        dialog = new BuyConfirmationDialogFragment();
        break;
      case SELL:
      default:
        dialog = new SellConfirmationDialogFragment();
        break;
    }

    Bundle args = new Bundle();
    args.putSerializable(BuySellConfirmationDialogFragment.QUANTITY, quantity);
    args.putSerializable(BuySellConfirmationDialogFragment.TOTAL, mCurrentQuote.getTotal().toBigMoney());
    dialog.setArguments(args);
    dialog.show(getFragmentManager(), "confirm");
  }

  // Have to use BigMoney here to truncate trailing zeros for BTC
  protected BigMoney getQuantityEntered() {
    BigMoney quantity = null;
    try {
      quantity = BigMoney.of(
              CurrencyUnit.getInstance("BTC"),
              new BigDecimal(mAmount.getText().toString()).stripTrailingZeros()
      );
      // Only positive quantities are valid
      if (quantity.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
        quantity = null;
      }
    } catch (Exception ex) {}
    return quantity;
  }
}
