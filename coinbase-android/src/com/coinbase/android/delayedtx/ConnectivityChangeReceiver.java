package com.coinbase.android.delayedtx;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.coinbase.android.Utils;

public class ConnectivityChangeReceiver extends BroadcastReceiver {


  @Override
  public void onReceive(Context context, Intent intent) {

    if (Utils.isConnectedOrConnecting(context)) {

      // Time to try and send delayed tx!
      context.startService(new Intent(context, DelayedTxSenderService.class));
    } else {
      // Continue waiting
    }
  }
}
