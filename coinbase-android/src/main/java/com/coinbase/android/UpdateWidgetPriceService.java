package com.coinbase.android;

import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.preference.PreferenceManager;

import com.coinbase.api.LoginManager;
import com.google.inject.Inject;

import org.joda.money.CurrencyUnit;
import org.joda.money.Money;

import roboguice.service.RoboService;

public class UpdateWidgetPriceService extends RoboService {

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

          String currencyCode = PreferenceManager.getDefaultSharedPreferences(UpdateWidgetPriceService.this).getString(
                  String.format(Constants.KEY_WIDGET_CURRENCY, widgetId), "USD");

          CurrencyUnit currency = CurrencyUnit.getInstance(currencyCode);

          // Step 1: Update widget without price
          AppWidgetManager manager = AppWidgetManager.getInstance(UpdateWidgetPriceService.this);
          WidgetUpdater updater = (WidgetUpdater) updaterClass.newInstance();
          updater.updateWidget(UpdateWidgetPriceService.this, manager, widgetId, null);

          // Step 2: Fetch price
          String priceString;
          Money spotPrice = mLoginManager.getClient().getSpotPrice(currency);
          priceString = Utils.formatCurrencyAmount(spotPrice.getAmount(), false, Utils.CurrencyType.TRADITIONAL);

          // Step 3: Update widget
          updater.updateWidget(UpdateWidgetPriceService.this, manager, widgetId, priceString);

        } catch(Exception e) {
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
