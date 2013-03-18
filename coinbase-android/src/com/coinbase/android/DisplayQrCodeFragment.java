package com.coinbase.android;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import org.json.JSONArray;
import org.json.JSONObject;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.DialogFragment;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.coinbase.android.Utils.CurrencyType;
import com.coinbase.api.RpcManager;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;

public class DisplayQrCodeFragment extends DialogFragment {

  private class CheckStatusTask extends TimerTask {

    private Context mContext;
    private TextView mStatus = null;
    private ProgressBar mProgress;
    private ImageView mIcon;
    private long mMonitorStartTime = 0;
    private Handler mHandler;
    private JSONObject mExchangeRates = null;
    private BigDecimal mDesiredAmount = null;

    public CheckStatusTask(Context context, View parent, long monitorStartTime, BigDecimal desiredAmount) {

      mContext = context;
      mStatus = (TextView) parent.findViewById(R.id.payment_status);
      mProgress = (ProgressBar) parent.findViewById(R.id.payment_progress);
      mIcon = (ImageView) parent.findViewById(R.id.payment_icon);
      mMonitorStartTime = monitorStartTime;
      mHandler = new Handler();
      mDesiredAmount = desiredAmount;
    }

    public void run() {

      JSONObject object = doCheck();

      boolean success = false;
      String resultText = null;
      if(object == null) {

        resultText = null;
      } else {

        // Done
        mStatusCheckTimer.cancel();

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        int activeAccount = prefs.getInt(Constants.KEY_ACTIVE_ACCOUNT, -1);
        String nativeCurrency = prefs.getString(String.format(Constants.KEY_ACCOUNT_NATIVE_CURRENCY, activeAccount),
            "usd").toLowerCase(Locale.CANADA);

        BigDecimal bigAmount = new BigDecimal(object.optJSONObject("amount").optString("amount"));

        success = mDesiredAmount == null ? true : mDesiredAmount.compareTo(bigAmount) < 1;
        int format = success ? R.string.payment_received : R.string.payment_received_short;

        String amountBtc = Utils.formatCurrencyAmount(bigAmount),
            amountNative = Utils.formatCurrencyAmount(bigAmount.multiply(new BigDecimal(mExchangeRates.optString("btc_to_" + nativeCurrency))),
              false, CurrencyType.TRADITIONAL),
              desiredAmount = mDesiredAmount == null ? "" : Utils.formatCurrencyAmount(mDesiredAmount);
        resultText = String.format(mContext.getString(format), amountBtc, amountNative,
          nativeCurrency.toUpperCase(Locale.CANADA), desiredAmount);
      }

      final String finalResultText = resultText;
      final boolean finalSuccess = success;
      mHandler.post(new Runnable() {

        public void run() {

          if(finalResultText != null) {
            mStatus.setText(finalResultText);
            mIcon.setVisibility(View.VISIBLE);
            mProgress.setVisibility(View.GONE);
            mIcon.setImageResource(finalSuccess ? R.drawable.ic_payment_success : R.drawable.ic_payment_error);
          } else {
            mIcon.setVisibility(View.GONE);
            mProgress.setVisibility(View.VISIBLE);
          }
        }
      });
    }

    private JSONObject doCheck() {

      try {

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        int activeAccount = prefs.getInt(Constants.KEY_ACTIVE_ACCOUNT, -1);

        String currentUserId = prefs.getString(String.format(Constants.KEY_ACCOUNT_ID, activeAccount), null);

        if(mExchangeRates == null) {

          mExchangeRates = RpcManager.getInstance().callGet(mContext, "currencies/exchange_rates");
        }

        JSONObject response = RpcManager.getInstance().callGet(mContext, "transactions");
        if(response.getInt("total_count") > 0) {

          JSONArray array = response.getJSONArray("transactions");

          for(int i = 0; i < array.length(); i++) {

            JSONObject latest = array.getJSONObject(i).getJSONObject("transaction");

            if(latest.optBoolean("request")) {
              continue;
            }

            if(latest.optJSONObject("recipient") == null ||
                !currentUserId.equals(latest.getJSONObject("recipient").optString("id"))) {
              continue; // We are looking for money being sent to us
            }

            boolean recent = ISO8601.toCalendar(latest.getString("created_at")).getTime().getTime() > mMonitorStartTime;

            if(recent) {

              // This is the one we've been waiting for!
              return latest;
            } else {

              continue;
            }
          }

          return null;
        } else {
          return null;
        }

      } catch (Exception e) {
        e.printStackTrace();
        return null;
      }
    }
  }

  private Timer mStatusCheckTimer = new Timer();

  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState) {

    AlertDialog.Builder b = new AlertDialog.Builder(getActivity());

    String contents = getArguments().getString("data");
    boolean checkForNewPayments = getArguments().getBoolean("checkForNewPayments", true);
    String desiredAmount = getArguments().getString("desiredAmount");

    Bitmap bitmap;
    try {
      bitmap = Utils.createBarcode(contents, BarcodeFormat.QR_CODE, 512, 512);
    } catch (WriterException e) {
      e.printStackTrace();
      return null;
    }

    View view = View.inflate(getActivity(), R.layout.dialog_qrcode, null);
    ImageView imageView = (ImageView) view.findViewById(R.id.qrcode);
    imageView.setImageBitmap(bitmap);

    View paymentStatusContainer = view.findViewById(R.id.payment_status_container);
    if(checkForNewPayments) {

      long checkInterval = 1000; // One second
      TimerTask task = new CheckStatusTask(getActivity(), paymentStatusContainer, System.currentTimeMillis(),
        desiredAmount == null ? null : new BigDecimal(desiredAmount));
      mStatusCheckTimer.scheduleAtFixedRate(task, checkInterval, checkInterval);
    } else {

      paymentStatusContainer.setVisibility(View.GONE);
    }

    b.setView(view);

    if(Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
      // Make sure dialog has white background so QR code is legible
      view.setBackgroundColor(Color.WHITE);
    }

    b.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {

      @Override
      public void onClick(DialogInterface dialog, int which) {
        dialog.dismiss();
      }
    });

    return b.create();
  }

  @Override
  public void onStop() {
    super.onStop();

    mStatusCheckTimer.cancel();
  }

}
