package com.coinbase.android;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.DialogFragment;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.coinbase.android.Utils.CurrencyType;
import com.coinbase.api.RpcManager;
import com.google.inject.Inject;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;

import org.json.JSONArray;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import roboguice.fragment.RoboDialogFragment;

public class DisplayQrOrNfcFragment extends RoboDialogFragment {

  private static final boolean IS_NFC_SUPPORTED = PlatformUtils.hasIceCreamSandwich();

  private class CheckStatusTask extends TimerTask {

    private Context mContext;
    private TextView mStatus = null;
    private ProgressBar mProgress;
    private ImageView mIcon;
    private long mMonitorStartTime = 0;
    private Handler mHandler;
    private JSONObject mExchangeRates = null;
    private BigDecimal mDesiredAmount = null;

    @Inject
    private RpcManager mRpcManager;

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
            
            Button button = ((AlertDialog) getDialog()).getButton(ProgressDialog.BUTTON_POSITIVE);
            button.setText(android.R.string.ok);
            button.invalidate();
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

          mExchangeRates = mRpcManager.callGet(mContext, "currencies/exchange_rates");
        }

        JSONObject response = mRpcManager.callGet(mContext, "transactions");
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
    boolean isNfc = getArguments().getBoolean("isNfc", false);
    String desiredAmount = getArguments().getString("desiredAmount");

    Bitmap bitmap;
    try {
      bitmap = Utils.createBarcode(contents, BarcodeFormat.QR_CODE, 512, 512);
    } catch (WriterException e) {
      e.printStackTrace();
      return null;
    }

    DisplayMetrics metrics = getResources().getDisplayMetrics();
    int smallestWidth = Math.min(metrics.widthPixels, metrics.heightPixels);
    int qrSize = smallestWidth - (int) (100 * metrics.density);

    View view = View.inflate(getActivity(), R.layout.dialog_qrcode, null);
    ImageView imageView = (ImageView) view.findViewById(R.id.qrcode);
    imageView.setImageBitmap(bitmap);
    imageView.setVisibility(getArguments().getBoolean("isNfc") ? View.GONE : View.VISIBLE);
    imageView.getLayoutParams().width = qrSize;
    imageView.getLayoutParams().height = qrSize;

    TextView nfcStatus = (TextView) view.findViewById(R.id.nfc_status);
    boolean nfcSupported = IS_NFC_SUPPORTED && NfcAdapter.getDefaultAdapter(getActivity()) != null;
    nfcStatus.setText(nfcSupported ? R.string.transfer_nfc_ready : R.string.transfer_nfc_failure);
    nfcStatus.setVisibility(isNfc ? View.VISIBLE : View.GONE);

    View paymentStatusContainer = view.findViewById(R.id.payment_status_container);
    if(checkForNewPayments) {

      long checkInterval = 1000; // One second
      TimerTask task = new CheckStatusTask(getActivity(), paymentStatusContainer, System.currentTimeMillis(),
        desiredAmount == null ? null : new BigDecimal(desiredAmount));
      mStatusCheckTimer.scheduleAtFixedRate(task, checkInterval, checkInterval);
      RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) paymentStatusContainer.getLayoutParams();
      params.addRule(RelativeLayout.BELOW, isNfc ? R.id.nfc_status : R.id.qrcode);
    } else {

      paymentStatusContainer.setVisibility(View.GONE);
    }

    b.setView(view);

    if(!PlatformUtils.hasHoneycomb()) {
      // Make sure dialog has white background so QR code is legible
      view.setBackgroundColor(Color.WHITE);
    }

    b.setPositiveButton(android.R.string.cancel, new DialogInterface.OnClickListener() {

      @Override
      public void onClick(DialogInterface dialog, int which) {
        dialog.dismiss();
      }
    });

    return b.create();
  }

  @Override
  public void onStart() {
    super.onStart();

    if(IS_NFC_SUPPORTED && getArguments().getBoolean("isNfc")) {
      startNfc(getArguments().getString("data"));
    }
  }

  @Override
  public void onStop() {
    super.onStop();

    if(IS_NFC_SUPPORTED) {
      stopNfc();
    }

    mStatusCheckTimer.cancel();
  }

  @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
  private void startNfc(String uri) {

    if(getActivity() != null && NfcAdapter.getDefaultAdapter(getActivity()) != null) {

      NdefMessage message = new NdefMessage(new NdefRecord[] { NdefRecord.createUri(uri) });
      NfcAdapter.getDefaultAdapter(getActivity()).setNdefPushMessage(message, getActivity());
    }
  }

  @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
  private void stopNfc() {

    if(getActivity() != null && NfcAdapter.getDefaultAdapter(getActivity()) != null) {
      NfcAdapter.getDefaultAdapter(getActivity()).setNdefPushMessage(null, getActivity());
    }
  }

}
