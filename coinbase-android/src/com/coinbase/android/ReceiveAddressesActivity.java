package com.coinbase.android;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.annotation.TargetApi;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockListActivity;
import com.actionbarsherlock.view.MenuItem;
import com.coinbase.api.RpcManager;

public class ReceiveAddressesActivity extends SherlockListActivity {

  private class FetchReceiveAddressesTask extends AsyncTask<Void, Void, JSONObject> {

    @Override
    protected JSONObject doInBackground(Void... arg0) {

      try {
        return RpcManager.getInstance().callGet(ReceiveAddressesActivity.this, "addresses");
      } catch (IOException e) {
        e.printStackTrace();
        return null;
      } catch (JSONException e) {
        e.printStackTrace();
        return null;
      }
    }

    @Override
    protected void onPostExecute(JSONObject result) {

      try {
        if(result == null) {
          Toast.makeText(ReceiveAddressesActivity.this, getString(R.string.addresses_error), Toast.LENGTH_SHORT).show();
          finish();
        } else {

          findViewById(R.id.addresses_progress).setVisibility(View.GONE);
          getListView().setVisibility(View.VISIBLE);
          getListView().addHeaderView(View.inflate(ReceiveAddressesActivity.this, R.layout.activity_addresses_header, null));

          // Create adapter
          List<Map<String, Object>> data = new ArrayList<Map<String, Object>>();
          JSONArray addresses = result.getJSONArray("addresses");
          for(int i = 0; i < addresses.length(); i++) {

            JSONObject address = addresses.getJSONObject(i).getJSONObject("address");
            Map<String, Object> item = new HashMap<String, Object>();
            item.put("address", address.getString("address"));
            item.put("callback_url", address.optString("callback_url"));
            item.put("created_at", address.getString("created_at"));

            String label = address.optString("label");
            if(label == null || "null".equals(label) || "".equals(label)) {
              label = getString(R.string.addresses_nolabel);
            }
            item.put("label", label);
            data.add(item);
          }

          SimpleAdapter adapter = new SimpleAdapter(ReceiveAddressesActivity.this, data, android.R.layout.simple_list_item_2,
            new String[] { "address", "label" },
            new int[] { android.R.id.text2, android.R.id.text1 });
          setListAdapter(adapter);
        }
      } catch (JSONException e) {
        e.printStackTrace();
        Toast.makeText(ReceiveAddressesActivity.this, getString(R.string.addresses_error), Toast.LENGTH_SHORT).show();
        finish();
      }
    }
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {

    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_addresses);
    new FetchReceiveAddressesTask().execute();
    getSupportActionBar().setDisplayHomeAsUpEnabled(true);
  }

  @TargetApi(Build.VERSION_CODES.HONEYCOMB)
  private void setClipboard(String text) {

    int currentapiVersion = android.os.Build.VERSION.SDK_INT;
    if (currentapiVersion >= android.os.Build.VERSION_CODES.HONEYCOMB) {

      android.content.ClipboardManager clipboard =
          (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
      ClipData clip = ClipData.newPlainText("Coinbase", text);
      clipboard.setPrimaryClip(clip);
    } else {

      android.text.ClipboardManager clipboard =
          (android.text.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
      clipboard.setText(text);
    }
  }

  @Override
  protected void onListItemClick(ListView l, View v, int position, long id) {

    String address = (String)((Map<?, ?>) l.getItemAtPosition(position)).get("address");
    setClipboard(address);
    Toast.makeText(ReceiveAddressesActivity.this, getString(R.string.addresses_copied), Toast.LENGTH_SHORT).show();
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {

    if(item.getItemId() == android.R.id.home) {
      // Action bar up button
      Intent intent = new Intent(this, MainActivity.class);
      intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
      startActivity(intent);
      finish();
    }

    return false;
  }
}
