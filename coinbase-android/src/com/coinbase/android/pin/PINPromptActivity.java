package com.coinbase.android.pin;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.TextView.OnEditorActionListener;

import com.coinbase.android.AccountsFragment;
import com.coinbase.android.CoinbaseActivity;
import com.coinbase.android.CoinbaseActivity.RequiresAuthentication;
import com.coinbase.android.Constants;
import com.coinbase.android.LoginActivity;
import com.coinbase.android.R;
import com.coinbase.api.LoginManager;

@RequiresAuthentication
public class PINPromptActivity extends CoinbaseActivity implements AccountsFragment.ParentActivity {

  public static final String ACTION_PROMPT = "com.coinbase.android.pin.ACTION_PROMPT";
  public static final String ACTION_SET = "com.coinbase.android.pin.ACTION_SET";

  private boolean mIsSetMode = false;
  private EditText mPinNumberField = null;

  @Override
  protected void onCreate(Bundle arg0) {
    super.onCreate(arg0);

    mIsSetMode = ACTION_SET.equals(getIntent().getAction());

    if(mIsSetMode && !PINManager.getInstance().shouldGrantAccess(this)) {
      finish();
      return;
    }

    setContentView(R.layout.activity_pinprompt);

    findViewById(R.id.pin_switch_accounts).setOnClickListener(new View.OnClickListener() {

      @Override
      public void onClick(View v) {

        new AccountsFragment().show(getSupportFragmentManager(), "accounts");
      }
    });
    findViewById(R.id.pin_submit).setOnClickListener(new View.OnClickListener() {

      @Override
      public void onClick(View v) {

        onSubmit();
      }
    });
    findViewById(R.id.pin_switch_accounts).setVisibility(mIsSetMode ? View.GONE : View.VISIBLE);

    ((TextView) findViewById(R.id.pin_account)).setText(LoginManager.getInstance().getSelectedAccountName(this));

    ((EditText) findViewById(R.id.pin_number)).addTextChangedListener(new TextWatcher() {

      @Override
      public void afterTextChanged(Editable arg0) {
      }

      @Override
      public void beforeTextChanged(CharSequence arg0, int arg1, int arg2,
                                    int arg3) {
      }

      @Override
      public void onTextChanged(CharSequence arg0, int arg1, int arg2, int arg3) {

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(PINPromptActivity.this);
        int activeAccount = prefs.getInt(Constants.KEY_ACTIVE_ACCOUNT, -1);
        String pin = prefs.getString(String.format(Constants.KEY_ACCOUNT_PIN, activeAccount), null);

        if(!mIsSetMode && arg0.toString().equals(pin)) {
          // Correct PIN has been entered.
          onPinEntered(pin);
        }
      }

    });

    mPinNumberField = ((EditText) findViewById(R.id.pin_number));
    mPinNumberField.setOnEditorActionListener(new OnEditorActionListener() {

      @Override
      public boolean onEditorAction(TextView arg0, int arg1, KeyEvent arg2) {

        onSubmit();
        return true;
      }
    });
  }

  private void onSubmit() {
    if(mIsSetMode) {
      if(!"".equals(mPinNumberField.getText().toString())) {
        onPinEntered(mPinNumberField.getText().toString());
      }
    } else {
      Toast.makeText(PINPromptActivity.this, R.string.pin_incorrect, Toast.LENGTH_SHORT).show();
    }
  }

  private void onPinEntered(String pin) {

    if(mIsSetMode) {
      PINManager.getInstance().setPin(this, pin);
    }

    PINManager.getInstance().resetPinClock(this);
    finish();
  }

  @Override
  public void finish() {
    super.finish();

    if(!mIsSetMode) {
      overridePendingTransition(R.anim.pin_stop_enter, R.anim.pin_stop_exit);
    }
  }

  public void onAccountChosen(int account) {

    // Change active account
    LoginManager.getInstance().switchActiveAccount(this, account);

    finish();
    startActivity(new Intent(this, getClass()));
    overridePendingTransition(0, 0);
  }

  public void onAddAccount() {

    Intent intent = new Intent(this, LoginActivity.class);
    intent.putExtra(LoginActivity.EXTRA_SHOW_INTRO, false);
    startActivity(intent);
    finish();
  }
}
