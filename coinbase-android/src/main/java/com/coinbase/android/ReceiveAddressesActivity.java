package com.coinbase.android;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.coinbase.api.LoginManager;
import com.coinbase.api.entity.Address;
import com.coinbase.api.entity.AddressesResponse;
import com.github.rtyley.android.sherlock.roboguice.activity.RoboSherlockListActivity;
import com.google.inject.Inject;

import java.util.List;

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

    public FetchReceiveAddressesTask(Context context) {
      super(context);
    }

    @Override
    protected void onPreExecute() throws Exception {
      mProgressDialog = new ProgressDialog(ReceiveAddressesActivity.this);
      mProgressDialog.setCancelable(false);
      mProgressDialog.setIndeterminate(true);
      mProgressDialog.show();
    }

    @Override
    public AddressesResponse call() throws Exception {
      return mLoginManager.getClient().getAddresses();
    }

    @Override
    protected void onSuccess(AddressesResponse addressesResponse) {
      setListAdapter(new AddressesAdapter(
              ReceiveAddressesActivity.this,
              addressesResponse.getAddresses()
      ));
    }

    @Override
    protected void onException(Exception ex) {
      Toast.makeText(ReceiveAddressesActivity.this, getString(R.string.addresses_error), Toast.LENGTH_SHORT).show();
      super.onException(ex);
      finish();
    }

    @Override
    protected void onFinally() {
      mProgressDialog.dismiss();
    }
  }

  protected ProgressDialog mProgressDialog;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_addresses);
    new FetchReceiveAddressesTask(this).execute();
    getSupportActionBar().setDisplayHomeAsUpEnabled(true);
  }

  @Override
  protected void onListItemClick(ListView l, View v, int position, long id) {
    Address address = (Address) l.getItemAtPosition(position);

    Utils.setClipboard(this, address.getAddress());
    Toast.makeText(ReceiveAddressesActivity.this, getString(R.string.addresses_copied), Toast.LENGTH_SHORT).show();
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    MenuInflater inflater = getSupportMenuInflater();
    inflater.inflate(R.menu.activity_receive_addresses, menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    if(item.getItemId() == android.R.id.home) {
      // Action bar up button
      Intent intent = new Intent(this, MainActivity.class);
      intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
      startActivity(intent);
      finish();
    } else if (item.getItemId() == R.id.menu_generate_receive_address) {
      new GenerateReceiveAddressTask(this) {
        @Override
        protected void onSuccess(String result) {
          new FetchReceiveAddressesTask(ReceiveAddressesActivity.this).execute();
        }

        @Override
        protected void onException(Exception e) {
          Log.e("ReceiveAddressesActivity", "Error generating receive address", e);
          Toast.makeText(ReceiveAddressesActivity.this, R.string.account_save_error, Toast.LENGTH_SHORT).show();
        }
      }.execute();
    }

    return false;
  }
}
