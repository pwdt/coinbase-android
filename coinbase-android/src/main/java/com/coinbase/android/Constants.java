package com.coinbase.android;

public class Constants {

  private Constants() {}

  public static enum RateNoticeState {
    NOTICE_NOT_YET_SHOWN,
    SHOULD_SHOW_NOTICE,
    NOTICE_DISMISSED;
  }
  
  public static final String KEY_ACTIVE_ACCOUNT = "active_account";
  public static final String KEY_MAX_ACCOUNT = "max_account";
  public static final String KEY_KIOSK_MODE = "kiosk_mode";
  
  public static final String KEY_WIDGET_ACCOUNT = "widget_%d_account";
  public static final String KEY_WIDGET_CURRENCY = "widget_%d_currency";
  
  public static final String KEY_ACCOUNT_ACCESS_TOKEN = "account_%d_access_token";
  public static final String KEY_ACCOUNT_REFRESH_TOKEN = "account_%d_refresh_token";
  public static final String KEY_ACCOUNT_TOKEN_EXPIRES_AT = "account_%d_token_expires_at";
  public static final String KEY_ACCOUNT_VALID = "account_%d_valid";
  public static final String KEY_ACCOUNT_VALID_DESC = "account_%d_valid_desc";
  public static final String KEY_ACCOUNT_NAME = "account_%d_name";
  public static final String KEY_ACCOUNT_ID = "account_%d_id";
  public static final String KEY_ACCOUNT_NATIVE_CURRENCY = "account_%d_native_currency";
  public static final String KEY_ACCOUNT_FULL_NAME = "account_%d_full_name";
  public static final String KEY_ACCOUNT_TIME_ZONE = "account_%d_time_zone";
  public static final String KEY_ACCOUNT_LIMIT = "account_%1$d_limit_%2$s";
  public static final String KEY_ACCOUNT_LIMIT_CURRENCY = "account_%1$d_limit_currency_%2$s";
  public static final String KEY_ACCOUNT_POS_NOTES = "account_%1$d_pos_notes";
  public static final String KEY_ACCOUNT_POS_BTC_AMT = "account_%1$d_pos_btc_amt";
  public static final String KEY_ACCOUNT_SHOW_BALANCE = "account_%1$d_show_balance";
  public static final String KEY_ACCOUNT_FIRST_LAUNCH = "account_%1$d_first_launch";
  public static final String KEY_ACCOUNT_RATE_NOTICE_STATE = "account_%1$d_rate_notice_state";
  public static final String KEY_ACCOUNT_APP_USAGE = "account_%1$d_app_usage";
  public static final String KEY_ACCOUNT_BALANCE_FUZZY = "account_%1$d_balance_fuzzy";
  public static final String KEY_ACCOUNT_TRANSFER_CURRENCY_BTC = "account_%1$d_transfer_currency_btc";
  public static final String KEY_ACCOUNT_ENABLE_TIPPING = "account_%1$d_enable_tipping";

  public static final String KEY_ACCOUNT_PIN = "account_%d_pin";
  public static final String KEY_ACCOUNT_SALT = "account_%d_salt";
  public static final String KEY_ACCOUNT_LAST_PIN_ENTRY_TIME = "account_%d_last_pin_entry_time";
  public static final String KEY_ACCOUNT_PIN_VIEW_ALLOWED = "account_%d_pin_view_allowed";

  public static final String KEY_ACCOUNT_BALANCE = "account_%d_balance_amt";
  public static final String KEY_ACCOUNT_BALANCE_HOME = "account_%d_balance_home_amt";
  public static final String KEY_ACCOUNT_BALANCE_HOME_CURRENCY = "account_%d_balance_home_currency";
  
  public static final String KEY_ACCOUNT_RECEIVE_ADDRESS = "account_%d_receive_address";
  public static final String KEY_ACCOUNT_ENABLE_MERCHANT_TOOLS = "account_%d_enable_merchant_tools";

}
