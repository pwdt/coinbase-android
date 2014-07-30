package com.coinbase.android.settings;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.DialogFragment;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.coinbase.android.ApiTask;
import com.coinbase.android.CoinbaseFragment;
import com.coinbase.android.Constants;
import com.coinbase.android.R;
import com.coinbase.android.ReceiveAddressesActivity;
import com.coinbase.android.Utils;
import com.coinbase.android.dialog.InputTextDialogFragment;
import com.coinbase.android.dialog.SpinnerDialogFragment;
import com.coinbase.android.event.ReceiveAddressUpdatedEvent;
import com.coinbase.android.event.UserDataUpdatedEvent;
import com.coinbase.android.pin.PINManager;
import com.coinbase.android.pin.PINSettingDialogFragment;
import com.coinbase.api.LoginManager;
import com.google.inject.Inject;
import com.squareup.otto.Subscribe;

import org.joda.money.CurrencyUnit;
import org.json.JSONArray;

import java.io.Serializable;
import java.util.List;

import roboguice.RoboGuice;
import roboguice.fragment.RoboListFragment;
import roboguice.inject.InjectResource;

public class AccountSettingsFragment extends RoboListFragment implements CoinbaseFragment {

  private abstract class PreferenceListItem {
    @Inject
    protected LoginManager mLoginManager;

    protected Context mContext;
    protected SharedPreferences mPrefs;

    public abstract String getDisplayName();
    public abstract String getDisplayValue();
    public void onClick() {}
    public PreferenceListItem(Context context) {
      RoboGuice.getInjector(context).injectMembers(this);
      mContext = context;
      mPrefs = PreferenceManager.getDefaultSharedPreferences(mContext);
    }
    public PreferenceListItem() {
      this(getActivity());
    }
    protected String getCachedValue(String preferenceKey, String def) {
      return mPrefs.getString(
              String.format(preferenceKey, mLoginManager.getActiveAccount()),
              def
      );
    }
    protected String getCachedValue(String preferenceKey) {
      return getCachedValue(preferenceKey, null);
    }
  }

  private class NameItem extends PreferenceListItem {
    @InjectResource(R.string.account_name)
    protected String mName;

    @Override
    public String getDisplayName() {
      return mName;
    }

    @Override
    public String getDisplayValue() {
      return getCachedValue(Constants.KEY_ACCOUNT_FULL_NAME);
    }

    @Override
    public void onClick() {
      DialogFragment dialog = new ChangeNameDialogFragment();
      Bundle args = new Bundle();
      args.putString(InputTextDialogFragment.VALUE, getDisplayValue());
      // TODO add title with i18n
      dialog.setArguments(args);
      dialog.show(getFragmentManager(), "change_name");
    }
  }

  private class EmailItem extends PreferenceListItem {
    @InjectResource(R.string.account_email)
    protected String mName;

    @Override
    public void onClick() {
      DialogFragment dialog = new ChangeEmailDialogFragment();
      Bundle args = new Bundle();
      args.putString(InputTextDialogFragment.VALUE, getDisplayValue());
      // TODO add title with i18n
      dialog.setArguments(args);
      dialog.show(getFragmentManager(), "change_email");
    }

    @Override
    public String getDisplayName() {
      return mName;
    }

    @Override
    public String getDisplayValue() {
      return getCachedValue(Constants.KEY_ACCOUNT_NAME);
    }
  }

  public class TimezoneItem extends PreferenceListItem {
    @InjectResource(R.string.account_time_zone)
    protected String mName;
    protected Timezone[] timezones;

    public TimezoneItem() {
      // Load list from resource
      try {
        String jsonString = Utils.convertStreamToString(getResources().openRawResource(R.raw.time_zones));
        JSONArray json = new JSONArray(jsonString);
        timezones = new Timezone[json.length()];
        for (int i = 0; i < json.length(); i++) {
          JSONArray values = json.getJSONArray(i);
          timezones[i] = new Timezone(values.getString(0), values.getString(1));
        }
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public void onClick() {
      DialogFragment dialog = new ChangeTimezoneDialogFragment();
      Bundle args = new Bundle();
      args.putSerializable(ChangeTimezoneDialogFragment.TIMEZONES, timezones);
      args.putInt(ChangeTimezoneDialogFragment.SELECTED_INDEX, getSelectedIndex());
      dialog.setArguments(args);
      dialog.show(getFragmentManager(), "change_timezone");
    }

    @Override
    public String getDisplayName() {
      return mName;
    }

    @Override
    public String getDisplayValue() {
      return getCachedValue(Constants.KEY_ACCOUNT_TIME_ZONE);
    }

    private int getSelectedIndex() {
      String currentTimezone = getDisplayValue();
      for (int i = 0; i < timezones.length; ++i) {
        if (currentTimezone == timezones[i].getTimezone()) {
          return i;
        }
      }
      return -1;
    }
  }

  private class NativeCurrencyItem extends PreferenceListItem {
    @InjectResource(R.string.account_native_currency)
    protected String mName;
    protected List<CurrencyUnit> mCurrencyOptions;

    private class DisplayCurrenciesTask extends ApiTask<List<CurrencyUnit>> {
      protected DisplayCurrenciesTask(Context context) {
        super(context);
      }

      @Override
      public List<CurrencyUnit> call() throws Exception {
          return getClient().getSupportedCurrencies();
      }

      @Override
      public void onSuccess(List<CurrencyUnit> result) {
        mCurrencyOptions = result;

        DialogFragment dialog = new ChangeNativeCurrencyDialogFragment();
        Bundle args = new Bundle();
        args.putSerializable(ChangeNativeCurrencyDialogFragment.CURRENCIES, (Serializable) result);
        args.putInt(SpinnerDialogFragment.SELECTED_INDEX, getDefaultOptionIndex());
        dialog.setArguments(args);
        dialog.show(getFragmentManager(), "change_currency");
      }

      @Override
      public void onException(Exception ex) {
        Toast.makeText(context, R.string.account_list_error, Toast.LENGTH_SHORT).show();
      }
    }

    @Override
    public String getDisplayName() {
      return mName;
    }

    @Override
    public String getDisplayValue() {
      return getCachedValue(Constants.KEY_ACCOUNT_NATIVE_CURRENCY);
    }

    private int getDefaultOptionIndex() {
      String currentCurrencyCode = getDisplayValue();
      for (int i = 0; i < mCurrencyOptions.size(); ++i) {
        if (currentCurrencyCode == mCurrencyOptions.get(i).getCurrencyCode()) {
          return i;
        }
      }
      return -1;
    }

    @Override
    public void onClick() {
      new DisplayCurrenciesTask(mParent).execute();
    }
  }

  private class LimitsItem extends PreferenceListItem {

    @InjectResource(R.string.account_limits)
    protected String mName;

    @Override
    public String getDisplayName() {
      return mName;
    }

    @Override
    public String getDisplayValue() {
      return String.format(
              getString(R.string.account_limits_text),
              Utils.formatCurrencyAmount(getCachedValue(Constants.KEY_ACCOUNT_LIMIT_BUY, "0")),
              getCachedValue(Constants.KEY_ACCOUNT_LIMIT_CURRENCY_BUY, "BTC"),
              Utils.formatCurrencyAmount(getCachedValue(Constants.KEY_ACCOUNT_LIMIT_SELL, "0")),
              getCachedValue(Constants.KEY_ACCOUNT_LIMIT_CURRENCY_SELL, "BTC")
      );
    }
  }

  private class ReceiveAddressItem extends PreferenceListItem {
    @InjectResource(R.string.account_receive_address)
    protected String mName;

    @Override
    public String getDisplayName() {
      return mName;
    }

    @Override
    public String getDisplayValue() {
      return getCachedValue(Constants.KEY_ACCOUNT_RECEIVE_ADDRESS);
    }

    @Override
    public void onClick() {
      startActivity(new Intent(mParent, ReceiveAddressesActivity.class));
    }
  }

  private class MerchantToolsItem extends PreferenceListItem {
    @InjectResource(R.string.account_enable_merchant_tools)
    protected String mName;

    @InjectResource(R.string.account_merchant_tools_notice)
    protected String mDesc;

    @Override
    public String getDisplayName() {
      return mName;
    }

    @Override
    public String getDisplayValue() {
      return mDesc;
    }

    @Override
    public void onClick() {
      Intent intent = new Intent(Intent.ACTION_VIEW);
      intent.setData(Uri.parse("http://play.google.com/store/apps/details?id=com.coinbase.android.merchant"));
      startActivity(intent);
    }
  }

  private class PinItem extends PreferenceListItem {
    @InjectResource(R.string.account_android_pin)      String mName;
    @InjectResource(R.string.account_android_pin_edit) String mPinEdit;
    @InjectResource(R.string.account_android_pin_all)  String mPinAll;
    @InjectResource(R.string.account_android_pin_none) String mPinNone;

    @Override
    public String getDisplayName() {
      return mName;
    }

    @Override
    public String getDisplayValue() {
      int activeAccount = mLoginManager.getActiveAccount();
      boolean enabled = getCachedValue(Constants.KEY_ACCOUNT_PIN) != null;
      boolean editOnly = mPrefs.getBoolean(String.format(Constants.KEY_ACCOUNT_PIN_VIEW_ALLOWED, activeAccount), false);

      return enabled ? (editOnly ? mPinEdit : mPinAll) : mPinNone;
    }

    @Override
    public void onClick() {
      new PINSettingDialogFragment().show(getFragmentManager(), "pin");
    }
  }

  private class PreferenceListAdapter extends ArrayAdapter<PreferenceListItem> {
    public PreferenceListAdapter(PreferenceListItem[] preferences) {
      super(mParent, R.layout.account_item, preferences);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
      View view = convertView;
      PreferenceListItem item = (PreferenceListItem) getItem(position);

      if(view == null) {
        view = View.inflate(mParent, R.layout.account_item, null);
      }

      TextView text1 = (TextView) view.findViewById(android.R.id.text1),
               text2 = (TextView) view.findViewById(android.R.id.text2);

      text1.setText(item.getDisplayName());
      text2.setText(item.getDisplayValue());

      return view;
    }
  }

  Activity mParent;
  int mPinItem = -1;

  @InjectResource(R.string.title_account)
  String mTitle;

  @Override
  public void onViewCreated(View view, Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    registerForContextMenu(getListView());
  }

  @Override
  public void onAttach(Activity activity) {
    super.onAttach(activity);
    mParent = activity;
    PreferenceListItem[] preferenceListItems = new PreferenceListItem[] {
            new NameItem(),
            new EmailItem(),
            new TimezoneItem(),
            new NativeCurrencyItem(),
            new LimitsItem(),
            new ReceiveAddressItem(),
            new MerchantToolsItem(),
            new PinItem()
    };
    setListAdapter(new PreferenceListAdapter(preferenceListItems));
    refresh();
  }

  @Override
  public void onListItemClick(ListView l, View v, int position, long id) {
    editItem(position);
  }

  private void editItem(int position) {
    if(!PINManager.getInstance().checkForEditAccess(getActivity())) {
      mPinItem = position;
      return;
    }

    PreferenceListItem item = (PreferenceListItem) getListView().getItemAtPosition(position);
    item.onClick();
  }

  public void refresh() {
    new RefreshSettingsTask(mParent).execute();
    new LoadReceiveAddressTask(mParent).execute();
  }

  @Subscribe
  public void userDataUpdated(UserDataUpdatedEvent event) {
    Log.v(this.getClass().toString(), "User data updated, refreshing list adapter");
    ((BaseAdapter) getListAdapter()).notifyDataSetChanged();
  }

  @Subscribe
  public void receiveAddressUpdated(ReceiveAddressUpdatedEvent event) {
    Log.v(this.getClass().toString(), "Receive address updated, refreshing list adapter");
    ((BaseAdapter) getListAdapter()).notifyDataSetChanged();
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
    return mTitle;
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    Utils.bus().register(this);
  }
}
