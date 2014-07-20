package com.coinbase.android;

import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockListActivity;
import com.actionbarsherlock.view.MenuItem;
import com.coinbase.api.LoginManager;
import com.coinbase.api.RpcManager;
import com.coinbase.api.entity.Address;
import com.coinbase.api.entity.AddressesResponse;
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

import roboguice.inject.InjectView;
import roboguice.util.RoboAsyncTask;

public class ReceiveAddressesActivity extends RoboSherlockListActivity {

  private class AddressesAdapter extends BaseAdapter {
    private LayoutInflater mInflater;
    private List<Address> mAddresses;

    public AddressesAdapter(Context context, List<Address> addresses) {
      mInflater = LayoutInflater.from(context);
      mAddresses = addresses;
    }

    @Override
    public int getCount() {
      return mAddresses.size();
    }

    @Override
    public Address getItem(int position) {
      return mAddresses.get(position);
    }

    @Override
    public long getItemId(int position) {
      return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
      final Address address = getItem(position);
      View view = convertView;
      if (view == null) {
        view = mInflater.inflate(android.R.layout.simple_list_item_2, null);
      }

      TextView addressView = (TextView) view.findViewById(android.R.id.text2);
      TextView labelView   = (TextView) view.findViewById(android.R.id.text1);

      addressView.setText(address.getAddress());

      String label = address.getLabel();
      if(label == null || "null".equals(label) || "".equals(label)) {
        label = getString(R.string.addresses_nolabel);
      }
      labelView.setText(label);

      return view;
    }
  }

  private class FetchReceiveAddressesTask extends RoboAsyncTask<AddressesResponse> {

    @Inject
    private LoginManager mLoginManager;
    private AddressesResponse mResult = null;
    private ListView mListView;

    public FetchReceiveAddressesTask(Context context) {
      super(context);
      mListView = getListView();
    }

    @Override
    public AddressesResponse call() {
      try {
        mResult = mLoginManager.getClient(ReceiveAddressesActivity.this).getAddresses();
      } catch (Exception e) {
        e.printStackTrace();
      }
      return mResult;
    }

    @Override
    protected void onFinally() {
      if(mResult == null) {
        Toast.makeText(ReceiveAddressesActivity.this, getString(R.string.addresses_error), Toast.LENGTH_SHORT).show();
        finish();
      } else {
        findViewById(R.id.addresses_progress).setVisibility(View.GONE);
        mListView.setVisibility(View.VISIBLE);
        mListView.addHeaderView(View.inflate(ReceiveAddressesActivity.this, R.layout.activity_addresses_header, null));

        setListAdapter(new AddressesAdapter(
                ReceiveAddressesActivity.this,
                mResult.getAddresses()
        ));
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

    Address address = (Address) l.getItemAtPosition(position);

    Utils.setClipboard(this, address.getAddress());
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
