package com.coinbase.android;

import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.preference.PreferenceManager;

import com.coinbase.api.RpcManager;

import org.apache.http.message.BasicNameValuePair;
import org.json.JSONException;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class UpdateWidgetPriceService extends Service {

  public static interface WidgetUpdater {
    public void updateWidget(Context context, AppWidgetManager manager, int appWidgetId, String balance);
  }

  public static String EXTRA_WIDGET_ID = "widget_id";
  public static String EXTRA_UPDATER_CLASS = "updater_class";



  @Override
  public int onStartCommand(Intent intent, int flags, final int startId) {

    final int widgetId = intent.getIntExtra(EXTRA_WIDGET_ID, -1);
    final Class<?> updaterClass = (Class<?>) intent.getSerializableExtra(EXTRA_UPDATER_CLASS);

    new Thread(new Runnable() {
      public void run() {

        try {

          String currency = PreferenceManager.getDefaultSharedPreferences(UpdateWidgetPriceService.this).getString(
                  String.format(Constants.KEY_WIDGET_CURRENCY, widgetId), "USD");

          // Step 1: Update widget without price
          AppWidgetManager manager = AppWidgetManager.getInstance(UpdateWidgetPriceService.this);
          WidgetUpdater updater = (WidgetUpdater) updaterClass.newInstance();
          updater.updateWidget(UpdateWidgetPriceService.this, manager, widgetId, null);

          // Step 2: Fetch price
          String price;
          List<BasicNameValuePair> getParams = new ArrayList<BasicNameValuePair>();
          getParams.add(new BasicNameValuePair("currency", currency));
          price = RpcManager.getInstance().callGet(UpdateWidgetPriceService.this,
                  "prices/spot_rate", getParams).getString("amount");
          price = Utils.formatCurrencyAmount(new BigDecimal(price), false, Utils.CurrencyType.TRADITIONAL);

          // Step 3: Update widget
          updater.updateWidget(UpdateWidgetPriceService.this, manager, widgetId, price);

        } catch(JSONException e) {
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
