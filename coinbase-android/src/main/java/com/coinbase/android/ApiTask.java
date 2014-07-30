package com.coinbase.android;

import android.content.Context;

import com.coinbase.api.Coinbase;
import com.coinbase.api.LoginManager;
import com.google.inject.Inject;

import roboguice.util.RoboAsyncTask;

public abstract class ApiTask<T> extends RoboAsyncTask<T> {

  @Inject
  protected LoginManager mLoginManager;

  public ApiTask(Context context) {
    super(context);
  }

  protected Coinbase getClient() {
    return mLoginManager.getClient();
  }

}
