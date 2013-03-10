package com.coinbase.android;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceManager;

import com.actionbarsherlock.app.SherlockPreferenceActivity;
import com.actionbarsherlock.view.MenuItem;
import com.coinbase.android.CoinbaseActivity.RequiresPIN;
import com.coinbase.android.pin.PINManager;
import com.coinbase.android.pin.PINPromptActivity;

@RequiresPIN
public class AppSettingsActivity extends SherlockPreferenceActivity {

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    if(!PINManager.getInstance().shouldGrantAccess(this)) {
      finish();
      return;
    }

    addPreferencesFromResource(R.xml.app_settings);

    getSupportActionBar().setDisplayHomeAsUpEnabled(true);
  }

  @Override
  protected void onResume() {
    super.onResume();

    final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
    final int activeAccount = prefs.getInt(Constants.KEY_ACTIVE_ACCOUNT, -1);
    final String pinKey = String.format(Constants.KEY_ACCOUNT_PIN, activeAccount);
    boolean hasPin = prefs.getString(pinKey, null) != null;

    final Preference pinChange = findPreference("pin_change");
    pinChange.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {

      @Override
      public boolean onPreferenceClick(Preference preference) {

        Intent intent = new Intent(AppSettingsActivity.this, PINPromptActivity.class);
        intent.setAction(PINPromptActivity.ACTION_SET);
        startActivity(intent);
        return true;
      }
    });
    pinChange.setEnabled(hasPin);

    final CheckBoxPreference pinViewAllowed = (CheckBoxPreference) findPreference("pin_view_allowed");
    pinViewAllowed.setChecked(prefs.getBoolean(String.format(Constants.KEY_ACCOUNT_PIN_VIEW_ALLOWED, activeAccount), false));
    pinViewAllowed.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {

      @Override
      public boolean onPreferenceChange(Preference preference, Object newValue) {

        prefs.edit().putBoolean(
            String.format(Constants.KEY_ACCOUNT_PIN_VIEW_ALLOWED, activeAccount),
            (Boolean) newValue).commit();
        return true;
      }
    });
    pinViewAllowed.setEnabled(hasPin);

    CheckBoxPreference pinUse = (CheckBoxPreference) findPreference("pin_use");
    pinUse.setChecked(hasPin);
    pinUse.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {

      @Override
      public boolean onPreferenceChange(Preference preference, Object newValue) {

        Boolean usePin = (Boolean) newValue;
        if(usePin == Boolean.TRUE) {
          pinChange.setEnabled(true);
          pinViewAllowed.setEnabled(true);
        } else {
          pinChange.setEnabled(false);
          pinViewAllowed.setEnabled(false);
        }

        prefs.edit().putString(pinKey, null).commit();

        return true;
      }
    });
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {

    if(item.getItemId() == android.R.id.home) {
      // Action bar up button
      finish();
    }

    return false;
  }

}
