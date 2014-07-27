package com.coinbase.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.coinbase.android.event.ReceiveAddressUpdatedEvent;
import com.coinbase.android.event.UserDataUpdatedEvent;
import com.coinbase.android.pin.PINManager;
import com.coinbase.android.pin.PINSettingDialogFragment;
import com.coinbase.api.LoginManager;
import com.coinbase.api.entity.User;
import com.coinbase.api.exception.CoinbaseException;
import com.google.inject.Inject;
import com.squareup.otto.Subscribe;

import org.acra.ACRA;
import org.joda.money.CurrencyUnit;
import org.json.JSONArray;

import java.io.IOException;
import java.util.List;

import roboguice.RoboGuice;
import roboguice.fragment.RoboListFragment;
import roboguice.inject.InjectResource;
import roboguice.util.RoboAsyncTask;

public class AccountSettingsFragment extends RoboListFragment implements CoinbaseFragment {

  private class RefreshSettingsTask extends RoboAsyncTask<Boolean> {

    @Inject
    private LoginManager mLoginManager;

    public RefreshSettingsTask(Context context) {
      super(context);
    }

    @Override
    public Boolean call() {

      try {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mParent);
        int activeAccount = mLoginManager.getActiveAccount(mParent);
        User user = mLoginManager.getClient(mParent).getUser();

        Editor e = prefs.edit();

        e.putString(String.format(Constants.KEY_ACCOUNT_NAME, activeAccount), user.getEmail());
        e.putString(String.format(Constants.KEY_ACCOUNT_NATIVE_CURRENCY, activeAccount), user.getNativeCurrency().getCurrencyCode());
        e.putString(String.format(Constants.KEY_ACCOUNT_FULL_NAME, activeAccount), user.getName());
        e.putString(String.format(Constants.KEY_ACCOUNT_TIME_ZONE, activeAccount), user.getTimeZone());
        e.putString(String.format(Constants.KEY_ACCOUNT_LIMIT_BUY, activeAccount), user.getBuyLimit().getAmount().toString());
        e.putString(String.format(Constants.KEY_ACCOUNT_LIMIT_SELL, activeAccount), user.getSellLimit().getAmount().toString());
        e.putString(String.format(Constants.KEY_ACCOUNT_LIMIT_CURRENCY_BUY, activeAccount), user.getBuyLimit().getCurrencyUnit().getCurrencyCode());
        e.putString(String.format(Constants.KEY_ACCOUNT_LIMIT_CURRENCY_SELL, activeAccount), user.getSellLimit().getCurrencyUnit().getCurrencyCode());

        e.commit();

        String name = prefs.getString(String.format(Constants.KEY_ACCOUNT_FULL_NAME, activeAccount), "nothing");

        Log.v(this.getClass().toString(), "User data updated, name is " + name);

        return true;
      } catch (CoinbaseException e) {
        ACRA.getErrorReporter().handleException(new RuntimeException("RefreshSettings", e));
        e.printStackTrace();
      } catch (IOException e) {
        e.printStackTrace();
      }

      return false;
    }

    @Override
    protected void onFinally() {
      Utils.bus().post(new UserDataUpdatedEvent());
    }

  }

  private class LoadReceiveAddressTask extends RoboAsyncTask {
    @Inject
    LoginManager mLoginManager;

    public LoadReceiveAddressTask(Context context) {
      super(context);
    }

    @Override
    public Void call() throws Exception {
        String address = mLoginManager.getClient(context).getAddresses().getAddresses().get(0).getAddress();
        if(address != null) {
          // Save balance in preferences
          SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
          int activeAccount = mLoginManager.getActiveAccount(context);
          Editor editor = prefs.edit();
          editor.putString(String.format(Constants.KEY_ACCOUNT_RECEIVE_ADDRESS, activeAccount), address);
          editor.commit();
        }
        return null;
    }
  }

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
              String.format(preferenceKey, mLoginManager.getActiveAccount(mContext)),
              def
      );
    }
    protected String getCachedValue(String preferenceKey) {
      return getCachedValue(preferenceKey, null);
    }
  }

  private abstract class EditableTextPreferenceListItem extends PreferenceListItem {
    @Override
    public void onClick() {
      AlertDialog.Builder alertBuilder = new AlertDialog.Builder(mContext);

      LayoutInflater li = LayoutInflater.from(mContext);
      View textDialogView = li.inflate(R.layout.fragment_text_dialog, null);

      alertBuilder.setView(textDialogView);

      final EditText userInput = (EditText) textDialogView
              .findViewById(R.id.text_dialog_input);

      alertBuilder.setTitle("Change " + getDisplayName());
      configureEditText(userInput);
      userInput.setText(getDisplayValue());

      alertBuilder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
          onValueUpdated(userInput.getText().toString());
        }
      });

      alertBuilder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
          // Do nothing
        }
      });

      alertBuilder.show();
    }

    protected void configureEditText(EditText editText) {}

    public abstract void onValueUpdated(String newValue);
  }

  private abstract class ChoosablePreferenceListItem extends PreferenceListItem {
    protected int mSelected;

    @Override
    public void onClick() {
      AlertDialog.Builder alertBuilder = new AlertDialog.Builder(mContext);

      final Object[] options      = getOptions();
      String[] displayTexts       = new String[options.length];
      for (int i = 0; i < options.length; ++i) {
        displayTexts[i] = getOptionDisplayText(options[i]);
      }

      mSelected = getDefaultOptionIndex();
      alertBuilder.setSingleChoiceItems(displayTexts, mSelected, new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
          mSelected = which;
        }
      });

      alertBuilder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
          onChosenValue(options[mSelected]);
        }
      });

      alertBuilder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
          // Do nothing
        }
      });

      alertBuilder.show();
    }

    public abstract Object[] getOptions();
    public abstract void onChosenValue(Object newValue);
    public String getOptionDisplayText(Object option) { return option.toString(); }
    public int getDefaultOptionIndex() { return -1; }
  }

  private class NameItem extends EditableTextPreferenceListItem {
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
    protected void configureEditText(EditText editText) {
      super.configureEditText(editText);
      editText.setInputType(
              InputType.TYPE_CLASS_TEXT |
              InputType.TYPE_TEXT_VARIATION_PERSON_NAME
      );
    }

    @Override
    public void onValueUpdated(String newName) {
      User user = new User();
      user.setName(newName);
      UpdateUserTask task = new UpdateUserTask(mContext, user, Constants.KEY_ACCOUNT_FULL_NAME, newName);
      task.execute();
    }
  }

  private class EmailItem extends EditableTextPreferenceListItem {
    @InjectResource(R.string.account_email)
    protected String mName;

    @Override
    public String getDisplayName() {
      return mName;
    }

    @Override
    protected void configureEditText(EditText editText) {
      super.configureEditText(editText);
      editText.setInputType(
              InputType.TYPE_CLASS_TEXT |
              InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
      );
    }

    @Override
    public String getDisplayValue() {
      return getCachedValue(Constants.KEY_ACCOUNT_NAME);
    }

    @Override
    public void onValueUpdated(String newEmail) {
      User user = new User();
      user.setEmail(newEmail);
      UpdateUserTask task = new UpdateUserTask(mContext, user, Constants.KEY_ACCOUNT_NAME, newEmail);
      task.execute();
    }
  }

  private class TimezoneItem extends ChoosablePreferenceListItem {
    public class Timezone {
      private String displayText;
      private String timezone;

      public Timezone(String timezone, String displayText) {
        this.setDisplayText(displayText);
        this.setTimezone(timezone);
      }

      public String getDisplayText() {
        return displayText;
      }

      public void setDisplayText(String displayText) {
        this.displayText = displayText;
      }

      public String getTimezone() {
        return timezone;
      }

      public void setTimezone(String timezone) {
        this.timezone = timezone;
      }
    }

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
    public Object[] getOptions() {
      return timezones;
    }

    @Override
    public void onChosenValue(Object newValue) {
      String timezone = ((Timezone) newValue).getTimezone();

      User user = new User();
      user.setTimeZone(timezone);
      UpdateUserTask task = new UpdateUserTask(mContext, user, Constants.KEY_ACCOUNT_TIME_ZONE, timezone);
      task.execute();
    }

    @Override
    public String getDisplayName() {
      return mName;
    }

    @Override
    public String getDisplayValue() {
      return getCachedValue(Constants.KEY_ACCOUNT_TIME_ZONE);
    }

    @Override
    public int getDefaultOptionIndex() {
      String currentTimezone = getDisplayValue();
      for (int i = 0; i < timezones.length; ++i) {
        if (currentTimezone == timezones[i].getTimezone()) {
          return i;
        }
      }
      return -1;
    }

    @Override
    public String getOptionDisplayText(Object option) {
      return ((Timezone) option).getDisplayText();
    }
  }

  private class NativeCurrencyItem extends ChoosablePreferenceListItem {
    @InjectResource(R.string.account_native_currency)
    protected String mName;
    protected List<CurrencyUnit> mCurrencyOptions;

    private class DisplayCurrenciesTask extends RoboAsyncTask<List<CurrencyUnit>> {
      @Inject
      private LoginManager mLoginManager;

      protected DisplayCurrenciesTask(Context context) {
        super(context);
      }

      @Override
      public List<CurrencyUnit> call() throws Exception {
        try {
          mCurrencyOptions = mLoginManager.getClient(mContext).getSupportedCurrencies();
        } catch (Exception ex) {}
        return mCurrencyOptions;
      }

      @Override
      public void onFinally() {
        if (mCurrencyOptions != null) {
          launchDialog();
        } else {
          Toast.makeText(mParent, R.string.account_list_error, Toast.LENGTH_SHORT).show();
        }
      }
    }

    @Override
    public Object[] getOptions() {
      return mCurrencyOptions.toArray();
    }

    @Override
    public void onChosenValue(Object newValue) {
      String currencyCode = ((CurrencyUnit) newValue).getCurrencyCode();

      User user = new User();
      user.setNativeCurrency((CurrencyUnit) newValue);
      UpdateUserTask task = new UpdateUserTask(mContext, user, Constants.KEY_ACCOUNT_NATIVE_CURRENCY, currencyCode);
      task.execute();
    }

    @Override
    public String getDisplayName() {
      return mName;
    }

    @Override
    public String getDisplayValue() {
      return getCachedValue(Constants.KEY_ACCOUNT_NATIVE_CURRENCY);
    }

    @Override
    public int getDefaultOptionIndex() {
      String currentCurrencyCode = getDisplayValue();
      for (int i = 0; i < mCurrencyOptions.size(); ++i) {
        if (currentCurrencyCode == mCurrencyOptions.get(i).getCurrencyCode()) {
          return i;
        }
      }
      return -1;
    }

    @Override
    public String getOptionDisplayText(Object option) {
      return ((CurrencyUnit) option).getCurrencyCode();
    }

    @Override
    public void onClick() {
      new DisplayCurrenciesTask(mContext).execute();
    }

    public void launchDialog() { super.onClick(); }
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
      int activeAccount = mLoginManager.getActiveAccount(mContext);
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

  private class UpdateUserTask extends RoboAsyncTask<Boolean> {

    ProgressDialog mDialog;

    private User mUser;
    private Boolean mResult = false;
    private String mPrefsKey, mPrefsValue;
    private Context mContext;

    @Inject
    private LoginManager mLoginManager;

    public UpdateUserTask(Context context, User user, String prefsKey, String prefsValue) {
      super(context);
      mUser = user;
      mPrefsKey = prefsKey;
      mPrefsValue = prefsValue;
      mContext = context;
    }

    @Override
    protected void onPreExecute() {
      mDialog = ProgressDialog.show(mParent, null, mParent.getString(R.string.account_save_progress));
    }

    @Override
    public Boolean call() {
      try {
        mLoginManager.getClient(mContext).updateUser(mLoginManager.getActiveUserId(mContext), mUser);

        int activeAccount = mLoginManager.getActiveAccount(mContext);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        Editor e = prefs.edit();
        e.putString(String.format(mPrefsKey, activeAccount), mPrefsValue);
        e.commit();

        mResult = true;
      } catch (CoinbaseException e) {
        e.printStackTrace();
      } catch (IOException e) {
        e.printStackTrace();
      }

      return mResult;
    }

    @Override
    protected void onFinally() {
      Utils.bus().post(new UserDataUpdatedEvent());
      mDialog.dismiss();

      if(mResult) {
        Toast.makeText(mParent, R.string.account_save_success, Toast.LENGTH_SHORT).show();
      } else {
        Toast.makeText(mContext, R.string.account_save_error, Toast.LENGTH_SHORT).show();
      }
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
