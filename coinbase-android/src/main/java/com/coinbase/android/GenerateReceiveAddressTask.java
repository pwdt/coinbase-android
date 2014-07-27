package com.coinbase.android;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.coinbase.android.event.ReceiveAddressUpdatedEvent;
import com.coinbase.android.event.UserDataUpdatedEvent;
import com.coinbase.api.LoginManager;
import com.google.inject.Inject;

import roboguice.util.RoboAsyncTask;

public class GenerateReceiveAddressTask extends RoboAsyncTask<String> {
  @Inject
  LoginManager mLoginManager;
  String mResult;

  public GenerateReceiveAddressTask(Context context) {
    super(context);
  }

  @Override
  public String call() throws Exception {
    mResult = mLoginManager.getClient(context).generateReceiveAddress().getAddress();

    int activeAccount = mLoginManager.getActiveAccount(context);
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
    SharedPreferences.Editor e = prefs.edit();
    e.putString(String.format(Constants.KEY_ACCOUNT_RECEIVE_ADDRESS, activeAccount), mResult);
    e.commit();

    Utils.bus().post(new ReceiveAddressUpdatedEvent());

    return mResult;
  }
}
