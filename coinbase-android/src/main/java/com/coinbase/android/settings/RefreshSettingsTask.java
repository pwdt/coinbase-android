package com.coinbase.android.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import com.coinbase.android.ApiTask;
import com.coinbase.android.Constants;
import com.coinbase.android.Utils;
import com.coinbase.android.event.UserDataUpdatedEvent;
import com.coinbase.api.LoginManager;
import com.coinbase.api.entity.User;
import com.coinbase.api.exception.CoinbaseException;
import com.google.inject.Inject;

import org.acra.ACRA;

import java.io.IOException;

import roboguice.util.RoboAsyncTask;

class RefreshSettingsTask extends ApiTask<User> {

  public RefreshSettingsTask(Context context) {
    super(context);
  }

  @Override
  public User call() throws Exception {
    return getClient().getUser();
  }

  @Override
  public void onSuccess(User user) {
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
    int activeAccount = mLoginManager.getActiveAccount();

    SharedPreferences.Editor e = prefs.edit();

    e.putString(String.format(Constants.KEY_ACCOUNT_NAME, activeAccount), user.getEmail());
    e.putString(String.format(Constants.KEY_ACCOUNT_NATIVE_CURRENCY, activeAccount), user.getNativeCurrency().getCurrencyCode());
    e.putString(String.format(Constants.KEY_ACCOUNT_FULL_NAME, activeAccount), user.getName());
    e.putString(String.format(Constants.KEY_ACCOUNT_TIME_ZONE, activeAccount), user.getTimeZone());
    e.putString(String.format(Constants.KEY_ACCOUNT_LIMIT_BUY, activeAccount), user.getBuyLimit().getAmount().toString());
    e.putString(String.format(Constants.KEY_ACCOUNT_LIMIT_SELL, activeAccount), user.getSellLimit().getAmount().toString());
    e.putString(String.format(Constants.KEY_ACCOUNT_LIMIT_CURRENCY_BUY, activeAccount), user.getBuyLimit().getCurrencyUnit().getCurrencyCode());
    e.putString(String.format(Constants.KEY_ACCOUNT_LIMIT_CURRENCY_SELL, activeAccount), user.getSellLimit().getCurrencyUnit().getCurrencyCode());

    e.commit();
  }

  @Override
  protected void onFinally() {
    Utils.bus().post(new UserDataUpdatedEvent());
  }

}
