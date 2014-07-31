package com.coinbase.android.settings;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.widget.Toast;

import com.coinbase.android.ApiTask;
import com.coinbase.android.R;
import com.coinbase.android.Utils;
import com.coinbase.android.event.UserDataUpdatedEvent;
import com.coinbase.api.entity.User;
import com.google.inject.Inject;
import com.squareup.otto.Bus;

import roboguice.inject.InjectResource;

public class UpdateUserTask extends ApiTask<Void> {
  ProgressDialog mDialog;

  private User mUser;
  private String mPrefsKey, mPrefsValue;

  @InjectResource(R.string.account_save_progress) private String mProgressDialogMessage;
  @Inject protected Bus mBus;

  public UpdateUserTask(Context context, User user, String prefsKey, String prefsValue) {
    super(context);
    mUser = user;
    mPrefsKey = prefsKey;
    mPrefsValue = prefsValue;
  }

  @Override
  protected void onPreExecute() {
    mDialog = ProgressDialog.show(context, null, mProgressDialogMessage);
  }

  @Override
  public Void call() throws Exception {
    getClient().updateUser(mLoginManager.getActiveUserId(), mUser);

    int activeAccount = mLoginManager.getActiveAccount();
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
    SharedPreferences.Editor e = prefs.edit();
    e.putString(String.format(mPrefsKey, activeAccount), mPrefsValue);
    e.commit();

    return null;
  }

  @Override
  protected void onSuccess(Void v) {
    Toast.makeText(context, R.string.account_save_success, Toast.LENGTH_SHORT).show();
  }

  @Override
  protected void onException(Exception ex) {
    Toast.makeText(context, R.string.account_save_error, Toast.LENGTH_SHORT).show();
  }

  @Override
  protected void onFinally() {
    mBus.post(new UserDataUpdatedEvent());
    if (mDialog != null) {
      mDialog.dismiss();
    }
  }
}
