package com.coinbase.api;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.net.ParseException;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.Log;

import com.coinbase.android.BuildConfig;
import com.coinbase.android.BuildType;
import com.coinbase.android.Constants;
import com.coinbase.android.R;

import org.apache.http.HttpResponse;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class LoginManager {

  private static LoginManager INSTANCE = null;

  public static LoginManager getInstance() {

    if(INSTANCE == null) {
      INSTANCE = new LoginManager();
    }

    return INSTANCE;
  }

  // production
  protected static final String CLIENT_ID = BuildConfig.type == BuildType.CONSUMER ? "34183b03a3e1f0b74ee6aa8a6150e90125de2d6c1ee4ff7880c2b7e6e98b11f5" : "82f3e52bb25da3688066a45ec740a1efa686646bcdb89a054b2264bc362d9332";
  protected static final String CLIENT_SECRET = BuildConfig.type == BuildType.CONSUMER ? "2c481f46f9dc046b4b9a67e630041b9906c023d139fbc77a47053328b9d3122d" : "f8d57dceb5a4e36b30318e6f035ad3c846cb4dea18ff4f353a35608f1acb12cf";
  public static final String CLIENT_BASEURL = "https://coinbase.com:443";

  // development (adjust to your setup)
  //protected static final String CLIENT_ID = "b6753e48f7eff4ca287dd081a251c3801037fcda51bb52181d06947d1fb4cb08";
  //protected static final String CLIENT_SECRET = "da853dce0fcc753501e6fe7972ad64c8525f552e708b585c46e65c12e0a5ef44";
  //public static final String CLIENT_BASEURL = "http://192.168.1.10:3001";

  private LoginManager() {

  }

  public boolean isSignedIn(Context context) {

    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

    return prefs.getInt(Constants.KEY_ACTIVE_ACCOUNT, -1) > -1;
  }

  public String[] getAccounts(Context context) {

    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
    int numAccounts = prefs.getInt(Constants.KEY_MAX_ACCOUNT, -1) + 1;

    List<String> accounts = new ArrayList<String>();
    for(int i = 0; i < numAccounts; i++) {

      String account = prefs.getString(String.format(Constants.KEY_ACCOUNT_NAME, i), null);
      if(account != null) {
        accounts.add(account);
      }
    }

    return accounts.toArray(new String[0]);
  }

  public boolean switchActiveAccount(Context context, int index) {
    return switchActiveAccount(context, index, null);
  }

  public int getAccountId(Context context, int index) {

    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
    int numAccounts = prefs.getInt(Constants.KEY_MAX_ACCOUNT, -1) + 1;

    int currentIndex = 0;
    for(int i = 0; i < numAccounts; i++) {

      String account = prefs.getString(String.format(Constants.KEY_ACCOUNT_NAME, i), null);
      if(account != null) {

        if(currentIndex == index) {

          return i;
        }

        currentIndex++;
      }
    }

    return -1;
  }

  public boolean switchActiveAccount(Context context, int index, Editor e) {

    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
    int numAccounts = prefs.getInt(Constants.KEY_MAX_ACCOUNT, -1) + 1;

    if(e == null) {
      e = prefs.edit();
    }

    int currentIndex = 0;
    for(int i = 0; i < numAccounts; i++) {

      String account = prefs.getString(String.format(Constants.KEY_ACCOUNT_NAME, i), null);
      if(account != null) {

        if(currentIndex == index) {

          e.putInt(Constants.KEY_ACTIVE_ACCOUNT, i);
          e.commit();
          Log.i("Coinbase", "Switching to account " + i);
          return true;
        }

        currentIndex++;
      }
    }

    e.commit();

    return false;
  }

  public int getSelectedAccountIndex(Context context) {

    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
    int numAccounts = prefs.getInt(Constants.KEY_MAX_ACCOUNT, -1) + 1;
    int activeAccount = prefs.getInt(Constants.KEY_ACTIVE_ACCOUNT, -1);

    int id = 0;
    for(int i = 0; i < numAccounts; i++) {

      if(i == activeAccount) {
        return id;
      }

      String account = prefs.getString(String.format(Constants.KEY_ACCOUNT_NAME, i), null);
      if(account != null) {
        id++;
      }
    }

    return -1;
  }

  public String getAccessToken(Context context, int account) {

    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
    return prefs.getString(String.format(Constants.KEY_ACCOUNT_ACCESS_TOKEN, account), null);
  }

  public String getAccessToken(Context context) {

    if(!isSignedIn(context)) {
      return null;
    }

    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
    int activeAccount = prefs.getInt(Constants.KEY_ACTIVE_ACCOUNT, -1);

    return prefs.getString(String.format(Constants.KEY_ACCOUNT_ACCESS_TOKEN, activeAccount), null);
  }

  public boolean needToRefreshAccessToken(Context context, int account) {

    synchronized (this) {
      SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
      long tokenExpiresAt = prefs.getLong(String.format(Constants.KEY_ACCOUNT_TOKEN_EXPIRES_AT, account), -1);
      return System.currentTimeMillis() >= tokenExpiresAt;
    }
  }

  public void refreshAccessToken(Context context, int account) {

    synchronized (this) {
      Log.i("Coinbase", "Refreshing access token...");

      SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
      String refreshToken = prefs.getString(String.format(Constants.KEY_ACCOUNT_REFRESH_TOKEN, account), null);

      List<BasicNameValuePair> parametersBody = new ArrayList<BasicNameValuePair>();
      parametersBody.add(new BasicNameValuePair("grant_type", "refresh_token"));
      parametersBody.add(new BasicNameValuePair("refresh_token", refreshToken));

      Object[] newTokens;

      try {
        newTokens = doTokenRequest(context, parametersBody, account);
      } catch(Exception e) {

        e.printStackTrace();
        Log.e("Coinbase", "Could not fetch new access token!");
        return;
      }

      if(newTokens == null) {

        // Authentication error.
        Log.e("Coinbase", "Authentication error when fetching new access token.");
        return;
      }

      Editor e = prefs.edit();

      e.putString(String.format(Constants.KEY_ACCOUNT_ACCESS_TOKEN, account), (String)newTokens[0]);
      e.putString(String.format(Constants.KEY_ACCOUNT_REFRESH_TOKEN, account), (String)newTokens[1]);
      e.putLong(String.format(Constants.KEY_ACCOUNT_TOKEN_EXPIRES_AT, account), System.currentTimeMillis() + 7200000);

      e.commit();
    }
  }

  // start three legged oauth handshake
  public String generateOAuthUrl(String redirectUrl){

    String baseUrl = CLIENT_BASEURL + "/oauth/authorize";
    String device = Build.MODEL.startsWith(Build.MANUFACTURER) ? Build.MODEL : Build.MANUFACTURER + " " + Build.MODEL;

    try{
      redirectUrl = URLEncoder.encode(redirectUrl, "utf-8");
      device = URLEncoder.encode(device, "utf-8");
    } catch(Exception e) {
      throw new RuntimeException(e);
    }

    String scope = BuildConfig.type == BuildType.CONSUMER ? "all" : "merchant";

    String authorizeUrl = baseUrl + "?response_type=code&client_id=" + CLIENT_ID + "&redirect_uri=" + redirectUrl + "&scope=" + scope + "&meta[name]=" + device;
    return authorizeUrl;
  }

  // end three legged oauth handshake. (code to tokens)
  // Returns error as human-readable string, or null on success.
  public String addAccountOAuth(Context context, String code, String originalRedirectUrl) {

    List<BasicNameValuePair> parametersBody = new ArrayList<BasicNameValuePair>();
    parametersBody.add(new BasicNameValuePair("grant_type", "authorization_code"));
    parametersBody.add(new BasicNameValuePair("redirect_uri", originalRedirectUrl));
    parametersBody.add(new BasicNameValuePair("code", code));

    try {
      Object[] tokens = doTokenRequest(context, parametersBody, -1);

      if(tokens == null) {
        return context.getString(R.string.login_error_auth);
      }

      SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
      Editor e = prefs.edit();

      int accountId = prefs.getInt(Constants.KEY_MAX_ACCOUNT, -1) + 1;
      e.putInt(Constants.KEY_MAX_ACCOUNT, accountId);
      e.putInt(Constants.KEY_ACTIVE_ACCOUNT, accountId);

      e.putString(String.format(Constants.KEY_ACCOUNT_ACCESS_TOKEN, accountId), (String)tokens[0]);
      e.putString(String.format(Constants.KEY_ACCOUNT_REFRESH_TOKEN, accountId), (String)tokens[1]);
      e.putLong(String.format(Constants.KEY_ACCOUNT_TOKEN_EXPIRES_AT, accountId), System.currentTimeMillis() + 7200000);

      e.commit();

      JSONObject userInfo = RpcManager.getInstance().callGet(context, "users").getJSONArray("users").getJSONObject(0).getJSONObject("user");
      e.putString(String.format(Constants.KEY_ACCOUNT_NAME, accountId), userInfo.getString("email"));
      e.putString(String.format(Constants.KEY_ACCOUNT_NATIVE_CURRENCY, accountId), userInfo.getString("native_currency"));
      e.putString(String.format(Constants.KEY_ACCOUNT_FULL_NAME, accountId), userInfo.getString("name"));
      e.putString(String.format(Constants.KEY_ACCOUNT_TIME_ZONE, accountId), userInfo.getString("time_zone"));
      e.putString(String.format(Constants.KEY_ACCOUNT_LIMIT, accountId, "buy"), userInfo.getJSONObject("buy_limit").getString("amount"));
      e.putString(String.format(Constants.KEY_ACCOUNT_LIMIT, accountId, "sell"), userInfo.getJSONObject("sell_limit").getString("amount"));
      e.putString(String.format(Constants.KEY_ACCOUNT_LIMIT_CURRENCY, accountId, "buy"), userInfo.getJSONObject("buy_limit").getString("currency"));
      e.putString(String.format(Constants.KEY_ACCOUNT_LIMIT_CURRENCY, accountId, "sell"), userInfo.getJSONObject("sell_limit").getString("currency"));
      e.commit();

      return null;

    } catch (IOException e) {
      e.printStackTrace();
      return context.getString(R.string.login_error_io);
    } catch (ParseException e) {
      e.printStackTrace();
      return context.getString(R.string.login_error_io);
    } catch (JSONException e) {
      e.printStackTrace();
      return context.getString(R.string.login_error_io);
    }
  }

  private Object[] doTokenRequest(Context context, Collection<BasicNameValuePair> params, int account) throws IOException, JSONException {

    DefaultHttpClient client = new DefaultHttpClient();

    String baseUrl = CLIENT_BASEURL + "/oauth/token";

    HttpPost oauthPost = new HttpPost(baseUrl);
    List<BasicNameValuePair> parametersBody = new ArrayList<BasicNameValuePair>();
    parametersBody.add(new BasicNameValuePair("client_id", CLIENT_ID));
    parametersBody.add(new BasicNameValuePair("client_secret", CLIENT_SECRET));
    parametersBody.addAll(params);
    oauthPost.setEntity(new UrlEncodedFormEntity(parametersBody, HTTP.UTF_8));

    HttpResponse response = client.execute(oauthPost);
    int code = response.getStatusLine().getStatusCode();

    if(code == 401) {

      Log.e("Coinbase", "Authentication error getting token for account " + account);
      return null;
    } else if(code != 200) {

      throw new IOException("Got HTTP response code " + code);
    }

    JSONObject content = new JSONObject(new JSONTokener(EntityUtils.toString(response.getEntity())));

    String accessToken = content.getString("access_token");
    String refreshToken = content.getString("refresh_token");

    return new Object[] { accessToken, refreshToken };
  }

  public String getSelectedAccountName(Context context) {

    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
    int activeAccount = prefs.getInt(Constants.KEY_ACTIVE_ACCOUNT, -1);
    return prefs.getString(String.format(Constants.KEY_ACCOUNT_NAME, activeAccount), null);
  }

  public void setAccountValid(Context context, int accountId, boolean status, String desc) {

    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
    Editor e = prefs.edit();
    e.putBoolean(String.format(Constants.KEY_ACCOUNT_VALID, accountId), status);
    e.putString(String.format(Constants.KEY_ACCOUNT_VALID_DESC, accountId), desc);
    e.commit();
  }

  public String getAccountValid(Context context, int accountId) {

    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
    if (prefs.getBoolean(String.format(Constants.KEY_ACCOUNT_VALID, accountId), true)) {
      return null; // Account valid
    } else {
      return prefs.getString(String.format(Constants.KEY_ACCOUNT_VALID_DESC, accountId), "No msg");
    }
  }

  public void deleteCurrentAccount(Context context) {

    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
    Editor e = prefs.edit();

    int accountId = prefs.getInt(Constants.KEY_ACTIVE_ACCOUNT, -1);

    Set<String> toRemove = new HashSet<String>();
    for(String key : prefs.getAll().keySet()) {
      if(key.startsWith("account_" + accountId)) {
        toRemove.add(key);
      }
    }

    for(String key : toRemove) {
      e.remove(key);
    }

    e.commit();

    // If there are any other accounts, switch to them
    // Otherwise log out completely
    boolean success = switchActiveAccount(context, 0, e);

    if(!success) {
      e.putInt(Constants.KEY_ACTIVE_ACCOUNT, -1);
      Log.i("Coinbase", "Logged out of all accounts; active account is -1");
    }

    e.commit();
  }
}
