package com.coinbase.android;

import android.app.ListActivity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.coinbase.api.RpcManager;

import org.json.JSONArray;

public class WidgetChooseCurrencyActivity extends ListActivity {

  @Override
  protected void onCreate(Bundle arg0) {
    super.onCreate(arg0);

    // Load currency list
    new Thread(new Runnable() {
      public void run() {
        try {

          final JSONArray currencies = RpcManager.getInstance().callGet(WidgetChooseCurrencyActivity.this, "currencies").optJSONArray("response");
          runOnUiThread(new Runnable() {
            public void run() {
              loadCurrencies(currencies);
            }
          });
        } catch (Exception e) {

          e.printStackTrace();
          finish();
        }
      }
    }).start();

    setResult(RESULT_CANCELED);
  }

  private void loadCurrencies(final JSONArray currencies) {
    setListAdapter(new BaseAdapter() {
      @Override
      public int getCount() {
        return currencies.length();
      }

      @Override
      public Object getItem(int i) {
        return currencies.optJSONArray(i);
      }

      @Override
      public long getItemId(int i) {
        return i;
      }

      @Override
      public View getView(int i, View view, ViewGroup viewGroup) {

        if (view == null) {
          view = View.inflate(WidgetChooseCurrencyActivity.this, android.R.layout.simple_list_item_1, null);
        }

        TextView text1 = (TextView) view.findViewById(android.R.id.text1);
        text1.setText(currencies.optJSONArray(i).optString(0));

        return view;
      }
    });
  }

  @Override
  protected void onListItemClick(ListView l, View v, int position, long id) {

    onCurrencyChosen(((JSONArray) l.getItemAtPosition(position)).optString(1));
  }

  public void onCurrencyChosen(String currency) {

    int widgetId = getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);

    Editor e = PreferenceManager.getDefaultSharedPreferences(this).edit();
    e.putString(String.format(Constants.KEY_WIDGET_CURRENCY, widgetId), currency);
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
