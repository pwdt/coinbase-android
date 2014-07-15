package com.coinbase.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.ListFragment;
import android.text.InputType;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.coinbase.android.event.UserDataUpdatedEvent;
import com.coinbase.android.pin.PINManager;
import com.coinbase.android.pin.PINSettingDialogFragment;
import com.coinbase.api.LoginManager;
import com.coinbase.api.RpcManager;
import com.google.inject.Inject;

import org.acra.ACRA;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import roboguice.fragment.RoboListFragment;
import roboguice.util.RoboAsyncTask;

public class AccountSettingsFragment extends RoboListFragment implements CoinbaseFragment {

  private class RefreshSettingsTask extends RoboAsyncTask<Boolean> {

    @Inject
    private RpcManager mRpcManager;

    public RefreshSettingsTask(Context context) {
      super(context);
    }

    @Override
    public Boolean call() {

      try {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mParent);
        int activeAccount = prefs.getInt(Constants.KEY_ACTIVE_ACCOUNT, -1);
        JSONObject userInfo = mRpcManager.callGet(mParent, "users").getJSONArray("users").getJSONObject(0).getJSONObject("user");

        Editor e = prefs.edit();

        e.putString(String.format(Constants.KEY_ACCOUNT_NAME, activeAccount), userInfo.getString("email"));
        e.putString(String.format(Constants.KEY_ACCOUNT_NATIVE_CURRENCY, activeAccount), userInfo.getString("native_currency"));
        e.putString(String.format(Constants.KEY_ACCOUNT_FULL_NAME, activeAccount), userInfo.getString("name"));
        e.putString(String.format(Constants.KEY_ACCOUNT_TIME_ZONE, activeAccount), userInfo.getString("time_zone"));
        e.putString(String.format(Constants.KEY_ACCOUNT_LIMIT, activeAccount, "buy"), userInfo.getJSONObject("buy_limit").getString("amount"));
        e.putString(String.format(Constants.KEY_ACCOUNT_LIMIT, activeAccount, "sell"), userInfo.getJSONObject("sell_limit").getString("amount"));
        e.putString(String.format(Constants.KEY_ACCOUNT_LIMIT_CURRENCY, activeAccount, "buy"), userInfo.getJSONObject("buy_limit").getString("currency"));
        e.putString(String.format(Constants.KEY_ACCOUNT_LIMIT_CURRENCY, activeAccount, "sell"), userInfo.getJSONObject("sell_limit").getString("currency"));

        e.commit();

        return true;
      } catch (JSONException e) {
        ACRA.getErrorReporter().handleException(new RuntimeException("RefreshSettings", e));
        e.printStackTrace();
      } catch (IOException e) {
        e.printStackTrace();
      }

      return false;
    }

    @Override
    protected void onFinally() {
      setListAdapter(new PreferenceListAdapter());
      Utils.bus().post(new UserDataUpdatedEvent());
    }

  }

  private class PreferenceListAdapter extends BaseAdapter {

    int mActiveAccount = -1;

    @Override
    public int getCount() {
      return mPreferences.length + (BuildConfig.DEBUG ? mDebugPreferences.length : 0);
    }

    @Override
    public Object getItem(int position) {
      if (position >= mPreferences.length) {
        return mDebugPreferences[position - mPreferences.length];
      } else {
        return mPreferences[position];
      }
    }

    @Override
    public long getItemId(int position) {
      return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {

      SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mParent);

      if(mActiveAccount == -1) {
        mActiveAccount = prefs.getInt(Constants.KEY_ACTIVE_ACCOUNT, -1);
      }

      View view = convertView;
      Object[] item = (Object[]) getItem(position);

      if(view == null) {
        view = View.inflate(mParent, R.layout.account_item, null);
      }

      TextView text1 = (TextView) view.findViewById(android.R.id.text1),
          text2 = (TextView) view.findViewById(android.R.id.text2);

      String desc = null;
      if("limits".equals(item[2])) {

        desc = String.format(getString(R.string.account_limits_text),
            Utils.formatCurrencyAmount(prefs.getString(String.format(Constants.KEY_ACCOUNT_LIMIT, mActiveAccount, "buy"), "0")),
            prefs.getString(String.format(Constants.KEY_ACCOUNT_LIMIT_CURRENCY, mActiveAccount, "buy"), "BTC"),
            Utils.formatCurrencyAmount(prefs.getString(String.format(Constants.KEY_ACCOUNT_LIMIT, mActiveAccount, "sell"), "0")),
            prefs.getString(String.format(Constants.KEY_ACCOUNT_LIMIT_CURRENCY, mActiveAccount, "sell"), "BTC"));

      } else if("enable_merchant_tools".equals(item[2])) {
        desc = getString(R.string.account_merchant_tools_notice);
      } else if("pin".equals(item[2])) {
        boolean enabled = prefs.getString(String.format(Constants.KEY_ACCOUNT_PIN, mActiveAccount), null) != null;
        boolean editOnly = prefs.getBoolean(String.format(Constants.KEY_ACCOUNT_PIN_VIEW_ALLOWED, mActiveAccount), false);

        desc = getString(enabled ? (editOnly ? R.string.account_android_pin_edit : R.string.account_android_pin_all) : R.string.account_android_pin_none);

      } else if ("app_usage".equals(item[2])) {
        desc = Integer.toString(prefs.getInt(String.format((String) item[1], mActiveAccount), 0));
      } else {
        desc = prefs.getString(
            String.format((String) item[1], mActiveAccount), null);
      }

      if (item[0] instanceof String) {
        text1.setText((String) item[0]);
      } else {
        text1.setText((Integer) item[0]);
      }
      text2.setText(desc);

      return view;
    }

  }

  public static class NetworkListDialogFragment extends DialogFragment {

    int selected = -1;

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {

      AlertDialog.Builder b = new AlertDialog.Builder(getActivity());

      final String[] display = getArguments().getStringArray("display");
      final String[] data = getArguments().getStringArray("data");
      final String key = getArguments().getString("key");
      final String userUpdateParam = getArguments().getString("userUpdateParam");
      selected = getArguments().getInt("selected");

      b.setSingleChoiceItems(display, selected, new DialogInterface.OnClickListener() {

        @Override
        public void onClick(DialogInterface dialog, int which) {

          selected = which;
        }
      });

      b.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {

        @Override
        public void onClick(DialogInterface dialog, int which) {

          // Update user
          ((MainActivity) getActivity()).getAccountSettingsFragment().updateUser(userUpdateParam, data[selected], key);
        }
      });

      b.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {

        @Override
        public void onClick(DialogInterface dialog, int which) {
          // Do nothing
        }
      });

      return b.create();
    }
  }

  public static class TimeZonesDialogFragment extends DialogFragment {

    int selected = -1;

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {

      AlertDialog.Builder b = new AlertDialog.Builder(getActivity());

      // Load list of time zones
      String[] timeZones;
      String[] displayTimeZones;
      try {
        String jsonString = Utils.convertStreamToString(getResources().openRawResource(R.raw.time_zones));
        JSONArray json = new JSONArray(jsonString);
        timeZones = new String[json.length()];
        displayTimeZones = new String[json.length()];
        for (int i = 0; i < json.length(); i++) {
          JSONArray values = json.getJSONArray(i);
          timeZones[i] = values.getString(0);
          displayTimeZones[i] = values.getString(1);
        }
      } catch (Exception e) {
        throw new RuntimeException(e);
      }

      final String[] data = timeZones;
      selected = Arrays.asList(data).indexOf(getArguments().getString("selected"));

      b.setSingleChoiceItems(displayTimeZones, selected, new DialogInterface.OnClickListener() {

        @Override
        public void onClick(DialogInterface dialog, int which) {

          selected = which;
        }
      });

      b.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {

        @Override
        public void onClick(DialogInterface dialog, int which) {

          // Update user
          ((MainActivity) getActivity()).getAccountSettingsFragment().updateUser("time_zone",
                  data[selected], Constants.KEY_ACCOUNT_TIME_ZONE);
        }
      });

      b.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {

        @Override
        public void onClick(DialogInterface dialog, int which) {
          // Do nothing
        }
      });

      return b.create();
    }
  }

  public static class TextSettingFragment extends DialogFragment {

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {

      AlertDialog.Builder b = new AlertDialog.Builder(getActivity());

      final String key = getArguments().getString("key");
      final String userUpdateParam = getArguments().getString("userUpdateParam");

      String currentValue = PreferenceManager.getDefaultSharedPreferences(getActivity()).getString(key, null);
      DisplayMetrics metrics = getActivity().getResources().getDisplayMetrics();
      int padding = (int)(16 * metrics.density);

      final FrameLayout layout = new FrameLayout(getActivity());
      final EditText text = new EditText(getActivity());
      text.setText(currentValue);
      text.setInputType(
          InputType.TYPE_CLASS_TEXT |
          ("name".equals(key) ? InputType.TYPE_TEXT_VARIATION_PERSON_NAME : InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS));
      layout.addView(text);
      layout.setPadding(padding, padding, padding, padding);
      b.setView(layout);

      b.setTitle("Change " + userUpdateParam);

      b.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {

        @Override
        public void onClick(DialogInterface dialog, int which) {

          // Update user
          ((MainActivity) getActivity()).getAccountSettingsFragment().updateUser(userUpdateParam,
              text.getText().toString(), key);
        }
      });

      b.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {

        @Override
        public void onClick(DialogInterface dialog, int which) {
          // Do nothing
        }
      });

      return b.create();
    }
  }

  private class ShowNetworkListTask extends RoboAsyncTask<String[][]> {

    ProgressDialog mDialog;
    String  mApiEndpoint, mPreferenceKey, mUserUpdateParam;
    int mSelected = -1;
    String[][] mResult = null;

    @Inject
    private RpcManager mRpcManager;

    public ShowNetworkListTask(Context context, String apiEndpoint, String preferenceKey, String userUpdateParam) {
      super(context);
      mApiEndpoint = apiEndpoint;
      mPreferenceKey = preferenceKey;
      mUserUpdateParam = userUpdateParam;
    }

    @Override
    protected void onPreExecute() {
      mDialog = ProgressDialog.show(mParent, null, mParent.getString(R.string.account_progress));
    }

    @Override
    public String[][] call() {
      String currentValue = PreferenceManager.getDefaultSharedPreferences(mParent).getString(mPreferenceKey, null);

      try {

        JSONArray array = mRpcManager.callGet(mParent,mApiEndpoint).getJSONArray("response");

        String[] display = new String[array.length()];
        String[] data = new String[array.length()];

        for(int i = 0; i < array.length(); i++) {
          display[i] = array.getJSONArray(i).getString(0);
          data[i] = array.getJSONArray(i).getString(1);

          if(data[i].equalsIgnoreCase(currentValue)) {
            mSelected = i;
          }
        }

        mResult = new String[][] { display, data };
        return mResult;

      } catch (JSONException e) {
        ACRA.getErrorReporter().handleException(new RuntimeException("ShowNetworkList", e));
        e.printStackTrace();
        return mResult;
      } catch (IOException e) {
        e.printStackTrace();
        return mResult;
      }
    }

    @Override
    protected void onFinally() {

      try {
        mDialog.dismiss();
      } catch (Exception e) {
        // ProgressDialog has been destroyed already
      }

      if(mResult == null) {

        Toast.makeText(mParent, R.string.account_list_error, Toast.LENGTH_SHORT).show();
      } else {

        NetworkListDialogFragment f = new NetworkListDialogFragment();
        Bundle args = new Bundle();
        args.putStringArray("display", mResult[0]);
        args.putStringArray("data", mResult[1]);
        args.putString("key", mPreferenceKey);
        args.putInt("selected", mSelected);
        args.putString("userUpdateParam", mUserUpdateParam);
        f.setArguments(args);
        f.show(mParent.getSupportFragmentManager(), "networklist");
      }
    }

  }

  private class UpdateUserTask extends RoboAsyncTask<Boolean> {

    ProgressDialog mDialog;

    private String mUserUpdateParam, mValue, mPrefsKey;
    private Boolean mResult = false;

    @Inject
    private RpcManager mRpcManager;

    public UpdateUserTask(Context context, String userUpdateParam, String value, String prefsKey) {
      super(context);
      mUserUpdateParam = userUpdateParam;
      mValue = value;
      mPrefsKey = prefsKey;
    }

    @Override
    protected void onPreExecute() {
      mDialog = ProgressDialog.show(mParent, null, mParent.getString(R.string.account_save_progress));
    }

    @Override
    public Boolean call() {
      try {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mParent);
        int activeAccount = prefs.getInt(Constants.KEY_ACTIVE_ACCOUNT, -1);
        String userId = prefs.getString(String.format(Constants.KEY_ACCOUNT_ID, activeAccount), null);

        List<BasicNameValuePair> postParams = new ArrayList<BasicNameValuePair>();
        postParams.add(new BasicNameValuePair("user[" + mUserUpdateParam + "]", mValue));
        JSONObject response = mRpcManager.callPut(mParent, "users/" + userId, postParams);

        boolean success = response.optBoolean("success");

        if(success) {
          Editor e = prefs.edit();
          e.putString(mPrefsKey, mValue);
          e.commit();
        } else {
          Log.e("Coinbase", "Got error when updating user: " + response);
        }

        mResult = success;
        return mResult;

      } catch (JSONException e) {
        ACRA.getErrorReporter().handleException(new RuntimeException("UpdateUser", e));
        e.printStackTrace();
        return mResult;
      } catch (IOException e) {
        e.printStackTrace();
        return mResult;
      }
    }

    @Override
    protected void onFinally() {

      mDialog.dismiss();

      if(mResult) {

        Toast.makeText(mParent, R.string.account_save_success, Toast.LENGTH_SHORT).show();

        mParent.refresh();
      } else {

        Toast.makeText(mParent, R.string.account_save_error, Toast.LENGTH_SHORT).show();
      }
    }

  }

  private class LoadReceiveAddressTask extends RoboAsyncTask<Void> {

    @Inject
    private RpcManager mRpcManager;
    private Boolean mShouldGenerateNew;

    public LoadReceiveAddressTask(Context context, Boolean shouldGenerateNew) {
      super(context);
      mShouldGenerateNew = shouldGenerateNew;
    }

    @Override
    public Void call() {
      SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mParent);
      int activeAccount = prefs.getInt(Constants.KEY_ACTIVE_ACCOUNT, -1);

      try {

        String address;

        if(mShouldGenerateNew) {

          JSONObject response = mRpcManager.callPost(mParent, "account/generate_receive_address", null);

          address = response.optString("address");

        } else {

          JSONObject response = mRpcManager.callGet(mParent, "account/receive_address");

          address = response.optString("address");
        }

        if(address != null) {
          // Save balance in preferences
          Editor editor = prefs.edit();
          editor.putString(String.format(Constants.KEY_ACCOUNT_RECEIVE_ADDRESS, activeAccount), address);
          editor.commit();
        }

      } catch (IOException e) {
        e.printStackTrace();
      } catch (JSONException e) {
        ACRA.getErrorReporter().handleException(new RuntimeException("LoadReceiveAddress " + mShouldGenerateNew, e));
        e.printStackTrace();
      }

      return null;
    }
  }

  private Object[][] mPreferences = new Object[][] {
      { R.string.account_name, Constants.KEY_ACCOUNT_FULL_NAME, "name" },
      { R.string.account_email, Constants.KEY_ACCOUNT_NAME, "email" },
      { R.string.account_time_zone, Constants.KEY_ACCOUNT_TIME_ZONE, "time_zone" },
      { R.string.account_native_currency, Constants.KEY_ACCOUNT_NATIVE_CURRENCY, "native_currency" },
      { R.string.account_limits, Constants.KEY_ACCOUNT_LIMIT, "limits" },
      { R.string.account_receive_address, Constants.KEY_ACCOUNT_RECEIVE_ADDRESS, "receive_address" },
      { R.string.account_enable_merchant_tools, Constants.KEY_ACCOUNT_ENABLE_MERCHANT_TOOLS, "enable_merchant_tools" },
      { R.string.account_android_pin, Constants.KEY_ACCOUNT_PIN_VIEW_ALLOWED, "pin" },
  };

  private Object[][] mDebugPreferences = new Object[][] {
          { "Rate notice state", Constants.KEY_ACCOUNT_RATE_NOTICE_STATE, "rate_notice_state" },
          { "Tokens", Constants.KEY_ACCOUNT_ACCESS_TOKEN, "tokens" },
          { "App usage count", Constants.KEY_ACCOUNT_APP_USAGE, "app_usage" },
  };

  MainActivity mParent;
  int mPinItem = -1;
  SharedPreferences.OnSharedPreferenceChangeListener mChangeListener;
  @Inject LoginManager mLoginManager;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    setListAdapter(new PreferenceListAdapter());

    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mParent);
    mChangeListener = new SharedPreferences.OnSharedPreferenceChangeListener() {

      @Override
      public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
          String key) {

        // Refresh list
        ((BaseAdapter) getListAdapter()).notifyDataSetChanged();
      }
    };
    prefs.registerOnSharedPreferenceChangeListener(mChangeListener);
  }

  @Override
  public void onViewCreated(View view, Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    registerForContextMenu(getListView());
  }

  @Override
  public void onDestroy() {
    super.onDestroy();

    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mParent);
    prefs.unregisterOnSharedPreferenceChangeListener(mChangeListener);
  }

  @Override
  public void onAttach(Activity activity) {
    super.onAttach(activity);

    mParent = (MainActivity) activity;
  }

  public void updateUser(String key, String value, String prefsKey) {
    new UpdateUserTask(mParent, key, value, prefsKey).execute();
  }

  @Override
  public void onListItemClick(ListView l, View v, int position, long id) {
    editItem(position);
  }

  private void editItem(int position) {

    Object[] data = (Object[]) getListView().getItemAtPosition(position);
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mParent);
    final int activeAccount = prefs.getInt(Constants.KEY_ACTIVE_ACCOUNT, -1);

    if(!PINManager.getInstance().checkForEditAccess(getActivity())) {
      mPinItem = position;
      return;
    }

    if("name".equals(data[2]) || "email".equals(data[2])) {
      // Show text prompt
      TextSettingFragment f = new TextSettingFragment();
      Bundle args = new Bundle();
      args.putString("key", String.format((String) data[1], activeAccount));
      args.putString("userUpdateParam", (String) data[2]);
      f.setArguments(args);
      f.show(getFragmentManager(), "prompt");
    } else if("time_zone".equals(data[2])) {
      // Show list of time zones
      TimeZonesDialogFragment d = new TimeZonesDialogFragment();
      Bundle args = new Bundle();
      args.putString("selected", Utils.getPrefsString(mParent, Constants.KEY_ACCOUNT_TIME_ZONE, null));
      d.setArguments(args);
      d.show(getFragmentManager(), "time_zone");
    } else if("native_currency".equals(data[2])) {

      // Show list of currencies
      new ShowNetworkListTask(mParent, "currencies",
              String.format(Constants.KEY_ACCOUNT_NATIVE_CURRENCY, activeAccount),
              "native_currency").execute();
    } else if("tokens".equals(data[2])) {

      // Refresh token
      new Thread(new Runnable() {
        public void run() {
          mLoginManager.refreshAccessToken(mParent, activeAccount);
        }
      }).start();
    } else if("limits".equals(data[2])) {

      // Open browser
      Intent i = new Intent(Intent.ACTION_VIEW);
      i.addCategory(Intent.CATEGORY_BROWSABLE);
      i.setData(Uri.parse("https://coinbase.com/verifications"));
      startActivity(i);
    } else if("receive_address".equals(data[2])) {

      // Open all addresses
      startActivity(new Intent(mParent, ReceiveAddressesActivity.class));
    } else if("enable_merchant_tools".equals(data[2])) {

      Intent intent = new Intent(Intent.ACTION_VIEW);
      intent.setData(Uri.parse("http://play.google.com/store/apps/details?id=com.coinbase.android.merchant"));
      startActivity(intent);
    } else if("pin".equals(data[2])) {

      new PINSettingDialogFragment().show(getFragmentManager(), "pin");
    } else if("rate_notice_state".equals(data[2])) {
      mParent.getTransactionsFragment().setRateNoticeState(Constants.RateNoticeState.SHOULD_SHOW_NOTICE, true);
    } else if("app_usage".equals(data[2])) {
      Utils.incrementPrefsInt(mParent, Constants.KEY_ACCOUNT_APP_USAGE);
    }
  }

  @Override
  public void onCreateContextMenu(ContextMenu menu, View v,
      ContextMenuInfo _menuInfo) {

    AdapterView.AdapterContextMenuInfo menuInfo = (AdapterView.AdapterContextMenuInfo) _menuInfo;
    Object[] data = (Object[]) getListView().getItemAtPosition(menuInfo.position);

    if("receive_address".equals(data[2])) {

      menu.add(Menu.NONE, R.id.account_receive_address_generate, Menu.NONE, R.string.account_receive_address_generate);
    }
  }

  @Override
  public boolean onContextItemSelected(MenuItem item) {

    if(item.getItemId() == R.id.account_receive_address_generate) {

      if(!PINManager.getInstance().checkForEditAccess(getActivity())) {
        return true;
      }

      regenerateReceiveAddress();
      return true;
    }

    return super.onContextItemSelected(item);
  }

  public void regenerateReceiveAddress() {

    new LoadReceiveAddressTask(mParent, true).execute();
  }

  public void refresh() {

    setListAdapter(new PreferenceListAdapter());

    new RefreshSettingsTask(mParent).execute();
    new LoadReceiveAddressTask(mParent, false).execute();
  }

  @Override
  public void onSwitchedTo() {
    // Not used
  }

  @Override
  public void onPINPromptSuccessfulReturn() {
    if (mPinItem != -1) {
      editItem(mPinItem);
      mPinItem = -1;
    }
  }

  @Override
  public String getTitle() {
    return getString(R.string.title_account);
  }
}
