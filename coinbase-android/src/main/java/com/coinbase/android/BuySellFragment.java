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

  private abstract class GetQuoteTask extends RoboAsyncTask<Quote> {
    @Inject
    protected LoginManager mLoginManager;
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
      return mLoginManager.getClient().getBuyQuote(mAmount.toBigMoney().toMoney());
    }
  }

  private class GetSellQuoteTask extends GetQuoteTask {
    public GetSellQuoteTask(Context context, BigMoneyProvider amount) {
      super(context, amount);
    }

    @Override
    public Quote call() throws Exception {
      return mLoginManager.getClient().getSellQuote(mAmount.toBigMoney().toMoney());
    }
  }

  private abstract class ConfirmationDialogFragment extends RoboDialogFragment {
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
      final TextView message = new TextView(mParent);
      message.setBackgroundColor(Color.WHITE);
      message.setTextColor(Color.BLACK);
      message.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);

      float scale = getResources().getDisplayMetrics().density;
      int paddingPx = (int) (15 * scale + 0.5f);
      message.setPadding(paddingPx, paddingPx, paddingPx, paddingPx);

      message.setText(getMessage());

      AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
      builder.setView(message)
              .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                  onUserConfirm();
                }
              })
              .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                  onUserCancel();
                }
              });

      return builder.create();
    }

    public abstract String getMessage();
    public abstract void onUserConfirm();
    public void onUserCancel() {}
  }

  private abstract class AmountConfirmationDialogFragment extends ConfirmationDialogFragment {
    protected BigMoneyProvider mAmount;
    protected BigMoneyProvider mTotal;

    AmountConfirmationDialogFragment(BigMoneyProvider amount, BigMoneyProvider total) {
      mAmount = amount;
      mTotal = total;
    }
  }

  private abstract class BuyConfirmationDialog extends AmountConfirmationDialogFragment {
    @InjectResource(R.string.buysell_confirm_message_buy)
    protected String mMessageFormat;

    BuyConfirmationDialog(BigMoneyProvider amount, BigMoneyProvider total) {
      super(amount, total);
    }

    @Override
    public String getMessage() {
      return String.format(mMessageFormat, Utils.formatMoney(mAmount), Utils.formatMoney(mTotal));
    }
  }

  private abstract class SellConfirmationDialog extends AmountConfirmationDialogFragment {
    @InjectResource(R.string.buysell_confirm_message_sell)
    protected String mMessageFormat;

    SellConfirmationDialog(BigMoneyProvider amount, BigMoneyProvider total) {
      super(amount, total);
    }

    @Override
    public String getMessage() {
      return String.format(mMessageFormat, Utils.formatMoney(mAmount), Utils.formatMoney(mTotal));
    }
  }

  private abstract class BuySellTask extends RoboAsyncTask<Transfer> {
    @InjectResource(R.string.buysell_error_api)
    private String mApiErrorMessage;
    private ProgressDialog mDialog;

    @Override
    protected void onPreExecute() throws Exception {
      super.onPreExecute();
      mDialog = ProgressDialog.show(mParent, null, getString(R.string.buysell_progress));
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

  private class BuyTask extends BuySellTask  {
    @InjectResource(R.string.buysell_success_buy)
    protected String mSuccessFormat;
    @Inject
    protected LoginManager mLoginManager;
    protected BigMoneyProvider        mAmount;

    public BuyTask(Context context, BigMoneyProvider amount) {
      super(context);
      mAmount = amount;
    }

    @Override
    public Transfer call() throws Exception {
      return mLoginManager.getClient().buy(mAmount.toBigMoney().toMoney());
    }

    @Override
    public String getSuccessFormatString() {
      return mSuccessFormat;
    }
  }

  private class SellTask extends BuySellTask  {
    @InjectResource(R.string.buysell_success_sell)
    protected String mSuccessFormat;
    @Inject
    protected LoginManager mLoginManager;
    protected BigMoneyProvider        mAmount;

    public SellTask(Context context, BigMoneyProvider amount) {
      super(context);
      mAmount = amount;
    }

    @Override
    public Transfer call() throws Exception {
      return mLoginManager.getClient().sell(mAmount.toBigMoney().toMoney());
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
    final BigMoneyProvider quantity = getQuantityEntered();

    if (null == quantity) {
      return;
    }

    if(!PINManager.getInstance().checkForEditAccess(getActivity())) {
      return;
    }

    ConfirmationDialogFragment dialog;

    switch(mBuySellType) {
      case BUY:
        dialog = new BuyConfirmationDialog(quantity, mCurrentQuote.getTotal()) {
          @Override
          public void onUserConfirm() {
            new BuyTask(mParent, quantity).execute();
          }
        };
        break;
      case SELL:
      default:
        dialog = new SellConfirmationDialog(quantity, mCurrentQuote.getTotal()) {
          @Override
          public void onUserConfirm() {
            new SellTask(mParent, quantity).execute();
          }
        };
        break;
    }

    dialog.show(getFragmentManager(), "confirm");
  }

  // Have to use BigMoneyProvider here to truncate trailing zeros for BTC
  protected BigMoneyProvider getQuantityEntered() {
    BigMoneyProvider quantity = null;
    try {
      quantity = BigMoney.of(
              CurrencyUnit.getInstance("BTC"),
              new BigDecimal(mAmount.getText().toString()).stripTrailingZeros()
      );
      // Only positive quantities are valid
      if (quantity.toBigMoney().getAmount().compareTo(BigDecimal.ZERO) <= 0) {
        quantity = null;
      }
    } catch (Exception ex) {}
    return quantity;
  }
}
