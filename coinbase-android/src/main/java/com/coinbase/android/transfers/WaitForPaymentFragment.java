package com.coinbase.android.transfers;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.coinbase.android.PlatformUtils;
import com.coinbase.android.R;
import com.coinbase.android.Utils;
import com.coinbase.android.event.NewTransactionEvent;
import com.coinbase.android.util.BitcoinUri;
import com.coinbase.api.LoginManager;
import com.coinbase.api.entity.Transaction;
import com.google.inject.Inject;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.squareup.otto.Bus;
import com.squareup.otto.Subscribe;

import org.joda.money.Money;

import roboguice.fragment.RoboDialogFragment;
import roboguice.inject.InjectResource;

public class WaitForPaymentFragment extends RoboDialogFragment {
  public static final String MESSAGE = "DisplayQrFragment_Message";
  public static final String LABEL = "DisplayQrFragment_Label";
  public static final String RECIPIENT = "DisplayQrFragment_Recipient";
  public static final String AMOUNT = "DisplayQrFragment_Amount";
  public static final String NATIVE_AMOUNT = "DisplayQrFragment_Native_Amount";
  public static final String TYPE = "WaitForPaymentFragment_Type";

  public enum Type {
    NFC,
    QR
  }

  public static WaitForPaymentFragment newInstance(Type type, String recipient, Money amount, Money nativeAmount, String label, String message) {
    WaitForPaymentFragment result = new WaitForPaymentFragment();
    Bundle args = new Bundle();
    args.putSerializable(TYPE, type);
    args.putString(MESSAGE, message);
    args.putSerializable(AMOUNT, amount);
    args.putString(LABEL, label);
    args.putString(RECIPIENT, recipient);
    args.putSerializable(NATIVE_AMOUNT, nativeAmount);
    result.setArguments(args);

    return result;
  }

  @Inject
  protected Bus mBus;

  protected Type mType;
  protected Money mAmount, mNativeAmount;
  protected String mLabel, mMessage, mRecipient;
  protected boolean mBound;

  @InjectResource(R.string.payment_received_native)
  protected String mNativePaymentReceivedFormatString;

  @InjectResource(R.string.payment_received)
  protected String mPaymentReceivedFormatString;

  @InjectResource(R.string.payment_received_short)
  protected String mPaymentReceivedInvalidFormatString;

  @Inject
  protected LoginManager mLoginManager;

  protected TextView mStatus;
  protected ProgressBar mProgress;
  protected ImageView mIcon;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    mAmount = (Money) getArguments().getSerializable(AMOUNT);
    mRecipient = getArguments().getString(RECIPIENT);
    mMessage = getArguments().getString(MESSAGE);
    mRecipient = getArguments().getString(RECIPIENT);
    mLabel = getArguments().getString(LABEL);
    mNativeAmount = (Money) getArguments().getSerializable(NATIVE_AMOUNT);
    mType = (Type) getArguments().getSerializable(TYPE);
  }

  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState) {
    AlertDialog.Builder b = new AlertDialog.Builder(getActivity());

    DisplayMetrics metrics = getResources().getDisplayMetrics();
    int smallestWidth = Math.min(metrics.widthPixels, metrics.heightPixels);
    int qrSize = smallestWidth - (int) (100 * metrics.density);

    View view = View.inflate(getActivity(), R.layout.dialog_qrcode, null);
    ImageView imageView = (ImageView) view.findViewById(R.id.qrcode);
    TextView nfcStatus = (TextView) view.findViewById(R.id.nfc_status);

    View paymentStatusContainer = view.findViewById(R.id.payment_status_container);
    RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) paymentStatusContainer.getLayoutParams();

    if (Type.QR.equals(mType)) {
      Bitmap bitmap;
      try {
        bitmap = Utils.createBarcode(getBitcoinUri().toString(), BarcodeFormat.QR_CODE, 512, 512);
      } catch (WriterException e) {
        e.printStackTrace();
        return null;
      }
      imageView.setImageBitmap(bitmap);
      imageView.getLayoutParams().width = qrSize;
      imageView.getLayoutParams().height = qrSize;

      nfcStatus.setVisibility(View.GONE);
      params.addRule(RelativeLayout.BELOW, R.id.qrcode);
    } else if (Type.NFC.equals(mType)) {
      boolean nfcReady = NfcAdapter.getDefaultAdapter(getActivity()) != null;
      nfcStatus.setText(nfcReady ? R.string.transfer_nfc_ready : R.string.transfer_nfc_failure);
      nfcStatus.setVisibility(View.VISIBLE);
      params.addRule(RelativeLayout.BELOW, R.id.nfc_status);
    }

    mStatus = (TextView) paymentStatusContainer.findViewById(R.id.payment_status);
    mProgress = (ProgressBar) paymentStatusContainer.findViewById(R.id.payment_progress);
    mIcon = (ImageView) paymentStatusContainer.findViewById(R.id.payment_icon);

    b.setView(view);

    if(!PlatformUtils.hasHoneycomb()) {
      // Make sure dialog has white background so QR code is legible
      view.setBackgroundColor(Color.WHITE);
    }

    b.setPositiveButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int which) {
        dismiss();
      }
    });

    return b.create();
  }

  @Subscribe
  public void onNewTransaction(NewTransactionEvent newTransactionEvent) {
    Transaction newTransaction = newTransactionEvent.transaction;

    // Ignore requests
    if (newTransaction.isRequest()) {
      return;
    }

    // Ignore transactions with recipients other than us
    if (newTransaction.getRecipient() == null ||
            mLoginManager.getActiveUserId() != newTransaction.getRecipient().getId()) {
      return;
    }

    // We've received btc at this point
    Money transactionAmount = newTransaction.getAmount();

    if (transactionAmount.compareTo(mAmount) != 0) {
      mStatus.setText(String.format(
              mPaymentReceivedInvalidFormatString,
              Utils.formatMoney(transactionAmount),
              Utils.formatMoney(mAmount)
      ));
      mIcon.setImageResource(R.drawable.ic_payment_error);
    } else {
      if (mNativeAmount.getCurrencyUnit().equals(mAmount.getCurrencyUnit())) {
        mStatus.setText(String.format(
                mPaymentReceivedFormatString,
                Utils.formatMoney(transactionAmount)
        ));
      } else {
        mStatus.setText(String.format(
                mNativePaymentReceivedFormatString,
                Utils.formatMoney(transactionAmount),
                Utils.formatMoney(mNativeAmount)
        ));
      }
      mIcon.setImageResource(R.drawable.ic_payment_success);
    }

    mIcon.setVisibility(View.VISIBLE);
    mProgress.setVisibility(View.GONE);

    Button button = ((AlertDialog) getDialog()).getButton(ProgressDialog.BUTTON_POSITIVE);
    button.setText(android.R.string.ok);
    button.invalidate();
  }

  protected BitcoinUri getBitcoinUri() {
    BitcoinUri uri = new BitcoinUri();
    uri.setAddress(mRecipient);
    uri.setAmount(mAmount.getAmount());
    uri.setLabel(mLabel);
    uri.setMessage(mMessage);

    return uri;
  }

  @Override
  public void onStart() {
    super.onStart();
    mBus.register(this);

    Intent intent = new Intent(getActivity(), TransactionPollingService.class);
    getActivity().bindService(intent, mPollingServiceConnection, Context.BIND_AUTO_CREATE);

    if (Type.NFC.equals(mType) && PlatformUtils.hasIceCreamSandwich()) {
      startNfc(getBitcoinUri().toString());
    }
  }

  @Override
  public void onStop() {
    mBus.unregister(this);
    if (mBound) {
      getActivity().unbindService(mPollingServiceConnection);
    }
    mBound = false;

    if (Type.NFC.equals(mType) && PlatformUtils.hasIceCreamSandwich()) {
      stopNfc();
    }

    super.onStop();
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

  private ServiceConnection mPollingServiceConnection = new ServiceConnection() {
    @Override
    public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
      mBound = true;
    }

    @Override
    public void onServiceDisconnected(ComponentName componentName) {
      mBound = false;
    }
  };
}
