package com.coinbase.android;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.coinbase.android.event.ReceiveAddressUpdatedEvent;
import com.google.inject.Inject;
import com.squareup.otto.Bus;

public class GenerateReceiveAddressTask extends ApiTask<String> {
  @Inject protected Bus mBus;

  public GenerateReceiveAddressTask(Context context) {
    super(context);
  }

  @Override
  public String call() throws Exception {
    String newAddress = getClient().generateReceiveAddress().getAddress();

    int activeAccount = mLoginManager.getActiveAccount();
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
    SharedPreferences.Editor e = prefs.edit();
    e.putString(String.format(Constants.KEY_ACCOUNT_RECEIVE_ADDRESS, activeAccount), newAddress);
    e.commit();

    return newAddress;
  }

  @Override
  public void onFinally() {
    mBus.post(new ReceiveAddressUpdatedEvent());
  }
}
