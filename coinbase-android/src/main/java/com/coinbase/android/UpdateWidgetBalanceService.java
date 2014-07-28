package com.coinbase.android;

import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;

import com.coinbase.api.Coinbase;
import com.coinbase.api.LoginManager;
import com.coinbase.api.entity.Account;
import com.coinbase.api.exception.CoinbaseException;
import com.google.inject.Inject;

import java.io.IOException;
import java.util.List;

import roboguice.service.RoboService;

public class UpdateWidgetBalanceService extends RoboService {

  public static interface WidgetUpdater {
    public void updateWidget(Context context, AppWidgetManager manager, int appWidgetId, String balance);
  }

  public static String EXTRA_WIDGET_ID = "widget_id";
  public static String EXTRA_UPDATER_CLASS = "updater_class";

  @Inject
  private LoginManager mLoginManager;

  @Override
  public int onStartCommand(Intent intent, int flags, final int startId) {

    final int widgetId = intent.getIntExtra(EXTRA_WIDGET_ID, -1);
    final Class<?> updaterClass = (Class<?>) intent.getSerializableExtra(EXTRA_UPDATER_CLASS);

    new Thread(new Runnable() {
      public void run() {

        try {

          int accountId = PreferenceManager.getDefaultSharedPreferences(UpdateWidgetBalanceService.this).getInt(
              String.format(Constants.KEY_WIDGET_ACCOUNT, widgetId), -1);


          // Step 1: Update widget without balance
          AppWidgetManager manager = AppWidgetManager.getInstance(UpdateWidgetBalanceService.this);
          WidgetUpdater updater = (WidgetUpdater) updaterClass.newInstance();
          updater.updateWidget(UpdateWidgetBalanceService.this, manager, widgetId, null);

          // Step 2: Fetch balance for primary account
          String balance;
          if(accountId == -1) {
            balance = "";
          } else {
            balance = "";
            Log.i("Coinbase", "Service fetching balance... [" + updaterClass.getSimpleName() + "]");
            Coinbase client = mLoginManager.getClient();
            List<Account> subAccounts = client.getAccounts().getAccounts();
            for (Account subAccount : subAccounts) {
              if (subAccount.isPrimary()) {
                balance = Utils.formatCurrencyAmount(subAccount.getBalance().getAmount());
              }
            }
          }

          // Step 3: Update widget
          updater.updateWidget(UpdateWidgetBalanceService.this, manager, widgetId, balance);

        } catch(CoinbaseException e) {
          e.printStackTrace();
        } catch (InstantiationException e) {
          e.printStackTrace();
        } catch (IllegalAccessException e) {
          e.printStackTrace();
        } catch (IOException e) {
          e.printStackTrace();
        }

        stopSelf(startId);
      }
    }).start();

    return Service.START_REDELIVER_INTENT;
  }



  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

}
