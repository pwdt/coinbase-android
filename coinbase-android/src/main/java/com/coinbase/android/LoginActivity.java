package com.coinbase.android;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.Toast;

import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;
import com.coinbase.api.LoginManager;

import java.util.List;

public class LoginActivity extends CoinbaseActivity {

  private static final String REDIRECT_URL = "urn:ietf:wg:oauth:2.0:oob";
  public static final String EXTRA_SHOW_INTRO = "show_intro";

  WebView mLoginWebView;
  MenuItem mRefreshItem;
  boolean mRefreshItemState = false;

  private class IntroDialog extends Dialog {

    protected Activity mParent;

    public IntroDialog(Context c) {
      super(c, android.R.style.Theme_Translucent_NoTitleBar);

      requestWindowFeature(Window.FEATURE_NO_TITLE);
      getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
      getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
      setCancelable(false);
      getWindow().getAttributes().windowAnimations = R.style.LoginIntroDialogAnimation;

      setContentView(R.layout.activity_login_intro);

      findViewById(R.id.login_pos_warning).setVisibility(BuildConfig.type == BuildType.MERCHANT ? View.VISIBLE : View.GONE);

      Button dismiss = (Button) findViewById(R.id.login_intro_submit);
      dismiss.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View view) {
          dismiss();
        }
      });
    }

    @Override
    public void onBackPressed() {
      mParent.finish();
    }
  }

  private class OAuthCodeTask extends AsyncTask<String, Void, String> {

    ProgressDialog mDialog;

    @Override
    protected void onPreExecute() {
      super.onPreExecute();
      mDialog = ProgressDialog.show(LoginActivity.this, null, getString(R.string.login_progress));
    }

    @Override
    protected String doInBackground(String... params) {
      return LoginManager.getInstance().addAccountOAuth(LoginActivity.this, params[0], REDIRECT_URL);
    }

    protected void onPostExecute(String result) {

      try {
        mDialog.dismiss();
      } catch (Exception e) {
        // ProgressDialog has been destroyed already
      }

      if(result == null) {
        // Success!
        startActivity(new Intent(LoginActivity.this, MainActivity.class));
        finish();
      } else {
        // Failure.
        Toast.makeText(LoginActivity.this, result, Toast.LENGTH_LONG).show();
        loadLoginUrl();
      }
    }
  }

  @SuppressLint("SetJavaScriptEnabled")
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);  
    setContentView(R.layout.activity_login);
    setProgressBarIndeterminateVisibility(false); 
    getSupportActionBar().setTitle(R.string.login_title);
    findViewById(R.id.login_pos_notice).setVisibility(BuildConfig.type == BuildType.MERCHANT ? View.VISIBLE : View.GONE);

    mLoginWebView = (WebView) findViewById(R.id.login_webview);

    // Load authorization URL before user clicks on the sign in button so that it loads quicker
    mLoginWebView.getSettings().setJavaScriptEnabled(true);
    mLoginWebView.getSettings().setSavePassword(false);

    // Clear cookies so that user is not already logged in if they want to add a new account
    CookieSyncManager.createInstance(this); 
    CookieManager cookieManager = CookieManager.getInstance();
    cookieManager.removeAllCookie();

    mLoginWebView.setWebViewClient(new WebViewClient() {

      @Override
      public void onPageStarted(WebView view, String url, Bitmap favicon) {

        if(!PlatformUtils.hasHoneycomb()) {
          // There is a bug where shouldOverrideUrlLoading is not called
          // On versions of Android lower then Honeycomb
          // When the URL change is a result of a redirect
          // Emulate it here
          boolean shouldOverride = _shouldOverrideUrlLoading(view, url);
          if(shouldOverride) {
            view.stopLoading();
          }
        }
      }

      @Override
      public boolean shouldOverrideUrlLoading(WebView view, String url) {

        if(PlatformUtils.hasHoneycomb()) {
          return _shouldOverrideUrlLoading(view, url);
        } else {
          return false;
        }
      }

      public boolean _shouldOverrideUrlLoading(WebView view, String url) {

        Uri uri = Uri.parse(url);
        List<String> pathSegments = uri.getPathSegments();
        if(uri.getPath().startsWith("/transactions") || uri.getPath().isEmpty()) {
          // The coinbase site is trying to redirect us to the transactions page or the home page
          // Since we are not logged in go to the login page
          loadLoginUrl();
          return true;
        } else if(!url.contains("oauth") && !url.contains("signin") && !url.contains("signup") &&
            !url.contains("users") &&
            !url.contains("sessions")) {

          // Do not allow leaving the login page.
          Intent intent = new Intent(Intent.ACTION_VIEW);
          intent.setData(Uri.parse(url));
          intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
          startActivity(intent);
          return true;
        }

        Log.i("Coinbase", "Login activity allowed to browse to " + url);
        return false;
      }
    });
    mLoginWebView.setWebChromeClient(new WebChromeClient() {

      @Override
      public void onProgressChanged(WebView view, int newProgress) {

        setProgressBarVisible(newProgress != 100); 
      }

      @Override
      public void onReceivedTitle(WebView view, String title) {

        // Check if we have received the OAuth token
        if(!title.contains(" ") && title.length() > 25) {
          // Title is long and does not contain spaces;
          // must be the OAuth token!
          Log.i("Coinbase", "Starting login with title " + title.substring(0, 15) + "...");
          new OAuthCodeTask().execute(title);
        }
      }
    });

    onNewIntent(getIntent());
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);

    setIntent(intent);

    if(getIntent().getData() != null) {
      // Load this URL in the web view
      mLoginWebView.loadUrl(getIntent().getDataString());
    } else {
      loadLoginUrl();
      if(getIntent().getBooleanExtra(EXTRA_SHOW_INTRO, true)) {
        showIntro();
      }
    }
  }

  public void setProgressBarVisible(boolean animated) {

    mRefreshItemState = animated;

    if(mRefreshItem == null) {
      return;
    }

    if(animated) {
      mRefreshItem.setVisible(true);
      mRefreshItem.setActionView(R.layout.actionbar_indeterminate_progress);
    } else {
      mRefreshItem.setVisible(false);
      mRefreshItem.setActionView(null);
    }
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getSupportMenuInflater().inflate(R.menu.activity_login, menu);
    mRefreshItem = menu.findItem(R.id.menu_refresh);
    setProgressBarVisible(mRefreshItemState);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {

    return false;
  }

  private void loadLoginUrl() {
    mLoginWebView.loadUrl(LoginManager.getInstance().generateOAuthUrl(REDIRECT_URL));
  }

  private void showIntro() {

    IntroDialog d = new IntroDialog(this);
    d.mParent = this;
    d.show();
  }
}
