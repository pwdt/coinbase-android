package com.coinbase.api;

import android.content.Context;
import android.content.SharedPreferences;

public interface LoginManager {
  String getClientBaseUrl();

  boolean isSignedIn(Context context);

  String[] getAccounts(Context context);

  boolean switchActiveAccount(Context context, int index);

  int getAccountId(Context context, int index);

  boolean switchActiveAccount(Context context, int index, SharedPreferences.Editor e);

  int getSelectedAccountIndex(Context context);

  String getAccessToken(Context context, int account);

  boolean needToRefreshAccessToken(Context context, int account);

  void refreshAccessToken(Context context, int account);

  // start three legged oauth handshake
  String generateOAuthUrl(String redirectUrl);

  // end three legged oauth handshake. (code to tokens)
  // Returns error as human-readable string, or null on success.
  String addAccountOAuth(Context context, String code, String originalRedirectUrl);

  String getSelectedAccountName(Context context);

  void setAccountValid(Context context, int accountId, boolean status, String desc);

  String getAccountValid(Context context, int accountId);

  void deleteCurrentAccount(Context context);

  int getActiveAccount(Context context);

  Coinbase getClient(Context context, int account);

  Coinbase getClient(Context context);
}
