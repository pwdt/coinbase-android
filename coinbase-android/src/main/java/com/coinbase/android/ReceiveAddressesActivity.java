package com.coinbase.android;

import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockListActivity;
import com.actionbarsherlock.view.MenuItem;
import com.coinbase.api.RpcManager;
import com.github.rtyley.android.sherlock.roboguice.activity.RoboSherlockListActivity;
import com.google.inject.Inject;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import roboguice.util.RoboAsyncTask;

public class ReceiveAddressesActivity extends RoboSherlockListActivity {

  private class FetchReceiveAddressesTask extends RoboAsyncTask<JSONObject> {

    @Inject
    private RpcManager mRpcManager;
    private JSONObject mResult = null;

    public FetchReceiveAddressesTask(Context context) {
      super(context);
    }

    @Override
    public JSONObject call() {
      try {
        mResult = mRpcManager.callGet(ReceiveAddressesActivity.this, "addresses");
      } catch (IOException e) {
        e.printStackTrace();
      } catch (JSONException e) {
        e.printStackTrace();
      }

      return mResult;
    }

    @Override
    protected void onFinally() {

      try {
        if(mResult == null) {
          Toast.makeText(ReceiveAddressesActivity.this, getString(R.string.addresses_error), Toast.LENGTH_SHORT).show();
          finish();
        } else {

          findViewById(R.id.addresses_progress).setVisibility(View.GONE);
          getListView().setVisibility(View.VISIBLE);
          getListView().addHeaderView(View.inflate(ReceiveAddressesActivity.this, R.layout.activity_addresses_header, null));

          // Create adapter
          List<Map<String, Object>> data = new ArrayList<Map<String, Object>>();
          JSONArray addresses = mResult.getJSONArray("addresses");
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
    new FetchReceiveAddressesTask(this).execute();
    getSupportActionBar().setDisplayHomeAsUpEnabled(true);
  }

  @Override
  protected void onListItemClick(ListView l, View v, int position, long id) {

    if (position == 0) {
      return; // Header
    }

    String address = (String)((Map<?, ?>) l.getItemAtPosition(position)).get("address");
    Utils.setClipboard(this, address);
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
