package com.coinbase.android.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.coinbase.android.ApiTask;
import com.coinbase.android.Constants;
import com.coinbase.api.LoginManager;
import com.coinbase.api.entity.Address;
import com.coinbase.api.entity.AddressesResponse;
import com.google.inject.Inject;

import roboguice.util.RoboAsyncTask;

class LoadReceiveAddressTask extends ApiTask<Address> {
  public LoadReceiveAddressTask(Context context) {
    super(context);
  }

  @Override
  public Address call() throws Exception {
    Address result = null;

    AddressesResponse response = getClient().getAddresses();
    if (response.getTotalCount() > 0) {
      result = response.getAddresses().get(0);
    }

    return result;
  }

  @Override
  public void onSuccess(Address address) {
    if(address != null) {
      // Save balance in preferences
      SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
      int activeAccount = mLoginManager.getActiveAccount();
      SharedPreferences.Editor editor = prefs.edit();
      editor.putString(String.format(Constants.KEY_ACCOUNT_RECEIVE_ADDRESS, activeAccount), address.getAddress());
      editor.commit();
    }
  }
}
