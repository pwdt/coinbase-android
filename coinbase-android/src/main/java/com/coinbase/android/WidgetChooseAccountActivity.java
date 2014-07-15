package com.coinbase.android;

import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.content.SharedPreferences.Editor;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentActivity;
import android.widget.Toast;

import com.coinbase.api.LoginManager;
import com.google.inject.Inject;

import roboguice.activity.RoboFragmentActivity;

public class WidgetChooseAccountActivity extends RoboFragmentActivity implements AccountsFragment.ParentActivity {

  @Inject
  protected LoginManager mLoginManager;

  @Override
  protected void onCreate(Bundle arg0) {
    super.onCreate(arg0);

    int widgetId = getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
    AppWidgetManager manager = AppWidgetManager.getInstance(this);
    if(!PlatformUtils.hasHoneycomb() &&
        manager.getAppWidgetInfo(widgetId).provider.getClassName().equals(TransactionsAppWidgetProvider.class.getName())) {
      Toast.makeText(this, R.string.widget_transactions_compat, Toast.LENGTH_LONG).show();
      return; // Transactions widget does not work (adapter widgets were added in Honeycomb)
    }

    AccountsFragment f = new AccountsFragment();
    Bundle args = new Bundle();
    args.putBoolean("widgetMode", true);
    f.setArguments(args);
    f.show(getSupportFragmentManager(), "accounts");

    setResult(RESULT_CANCELED);
  }

  public void onAddAccount() {

    startActivity(new Intent(this, LoginActivity.class));
  }

  public void onAccountChosen(int accountId) {

    int widgetId = getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
    int realAccountId = mLoginManager.getAccountId(this, accountId);

    Editor e = PreferenceManager.getDefaultSharedPreferences(this).edit();
    e.putInt(String.format(Constants.KEY_WIDGET_ACCOUNT, widgetId), realAccountId);
    e.commit();

    Intent refresh = new Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
    refresh.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, new int[] { widgetId });
    refresh.setPackage(this.getPackageName());
    sendBroadcast(refresh);

    Intent resultValue = new Intent();
    resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
    setResult(RESULT_OK, resultValue);
    finish();
  }
}
