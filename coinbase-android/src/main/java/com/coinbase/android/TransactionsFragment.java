package com.coinbase.android;

import android.animation.LayoutTransition;
import android.annotation.TargetApi;
import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.ListFragment;
import android.support.v4.widget.CursorAdapter;
import android.support.v4.widget.SimpleCursorAdapter;
import android.text.Html;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.Adapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.WrapperListAdapter;

import com.coinbase.android.Utils.CurrencyType;
import com.coinbase.android.db.DatabaseObject;
import com.coinbase.android.db.TransactionsDatabase;
import com.coinbase.android.db.TransactionsDatabase.TransactionEntry;
import com.coinbase.android.delayedtx.DelayedTxSenderService;
import com.coinbase.android.util.InsertedItemListAdapter;
import com.coinbase.api.LoginManager;
import com.coinbase.api.RpcManager;
import com.nineoldandroids.animation.Animator;
import com.nineoldandroids.animation.AnimatorSet;
import com.nineoldandroids.animation.ObjectAnimator;

import org.acra.ACRA;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.IOException;
import java.math.BigDecimal;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import uk.co.senab.actionbarpulltorefresh.extras.actionbarsherlock.AbsDefaultHeaderTransformer;
import uk.co.senab.actionbarpulltorefresh.extras.actionbarsherlock.PullToRefreshLayout;
import uk.co.senab.actionbarpulltorefresh.library.ActionBarPullToRefresh;
import uk.co.senab.actionbarpulltorefresh.library.Options;
import uk.co.senab.actionbarpulltorefresh.library.listeners.OnRefreshListener;

public class TransactionsFragment extends ListFragment implements CoinbaseFragment {

  private class LoadJustBalanceTask extends AsyncTask<Object, Void, JSONObject> {

    @Override
    protected JSONObject doInBackground(Object... params) {

      try {
        JSONObject exchangeRates = RpcManager.getInstance().callGet(mParent, "currencies/exchange_rates");
        exchangeRates.put("balance", RpcManager.getInstance().callGet(mParent, "account/balance"));
        return exchangeRates;
      } catch (IOException e) {

        e.printStackTrace();
      } catch (JSONException e) {

        e.printStackTrace();
      }

      return null;
    }

    @Override
    protected void onPostExecute(JSONObject result) {

      if (result != null) {
        mBalanceBtc = result.optJSONObject("balance").optString("amount");

        // Calculate home currency amount
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mParent);
        int activeAccount = prefs.getInt(Constants.KEY_ACTIVE_ACCOUNT, -1);
        mCurrencyNative = prefs.getString(String.format(Constants.KEY_ACCOUNT_NATIVE_CURRENCY, activeAccount),
                "usd").toUpperCase(Locale.CANADA);
        BigDecimal homeAmount = new BigDecimal(mBalanceBtc).multiply(
                new BigDecimal(result.optString("btc_to_" + mCurrencyNative.toLowerCase(Locale.CANADA))));

        mBalanceNative = Utils.formatCurrencyAmount(homeAmount, false, CurrencyType.TRADITIONAL);
        updateBalance();
      }
    }

  }

  private class SyncTransactionsTask extends AsyncTask<Integer, Void, Boolean> {

    public static final int MAX_PAGES = 1;
    public static final int MAX_ENDLESS_PAGES = 10;

    /**
     * Number of pages of transactions to sync extra transfer-related data.
     */
    public static final int MAX_TRANSFER_SYNC_PAGES = 3;

    @Override
    protected Boolean doInBackground(Integer... params) {

      Profiler p = new Profiler();

      List<JSONObject> transactions = new ArrayList<JSONObject>();
      String currentUserId = null;
      SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mParent);
      int activeAccount = prefs.getInt(Constants.KEY_ACTIVE_ACCOUNT, -1);
      Map<String, JSONObject> extraInfo = new HashMap<String, JSONObject>();

      int startPage = (params.length == 0 || params[0] == null) ? 0 : params[0];
      int loadedPage;

      // Make API call to download list of account changes
      try {

        int numPages = 1; // Real value will be set after first list iteration
        loadedPage = startPage;

        List<BasicNameValuePair> getParams = new ArrayList<BasicNameValuePair>();
        getParams.add(new BasicNameValuePair("page", Integer.toString(startPage + 1)));
        JSONObject response = RpcManager.getInstance().callGet(mParent, "account_changes", getParams);

        // Update balance
        // (we do it here to update the balance ASAP.)
        final String balanceBtc = response.getJSONObject("balance").getString("amount");
        final String balanceNative = response.getJSONObject("native_balance").getString("amount");
        final String currencyNative = response.getJSONObject("native_balance").getString("currency");
        mParent.runOnUiThread(new Runnable() {
          public void run() {
            mBalanceBtc = balanceBtc;
            mBalanceNative = Utils.formatCurrencyAmount(balanceNative, false, CurrencyType.TRADITIONAL);
            mCurrencyNative = currencyNative;
            updateBalance();
          }
        });

        currentUserId = response.getJSONObject("current_user").getString("id");

        JSONArray transactionsArray = response.optJSONArray("account_changes");
        numPages = response.getInt("num_pages");

        if(transactionsArray != null) {

          for(int j = 0; j < transactionsArray.length(); j++) {

            JSONObject transaction = transactionsArray.getJSONObject(j);

            if(transaction == null) {
              continue; // This transaction is empty
            }

            transactions.add(transaction);
          }
        }

        loadedPage++;

        p.segmentDone("Download account changes");

        mMaxPage = numPages;

        // Also fetch extra info from /transactions call
        // for first ~30 transactions
        if (startPage == 0) {
          JSONObject extraJson = RpcManager.getInstance().callGet(mParent, "transactions");
          JSONArray extras = extraJson.getJSONArray("transactions");
          for(int i = 0; i < extras.length(); i++) {
            JSONObject extra = extras.getJSONObject(i).getJSONObject("transaction");
            extraInfo.put(extra.getString("id"), extra);
          }
        } else {
          // Don't reload transactions on an infinite scroll
        }

        p.segmentDone("Download transactions");

      } catch (IOException e) {
        Log.e("Coinbase", "I/O error refreshing transactions.");
        e.printStackTrace();

        return false;
      } catch (JSONException e) {
        // Malformed response from Coinbase.
        Log.e("Coinbase", "Could not parse JSON response from Coinbase, aborting refresh of transactions.");
        ACRA.getErrorReporter().handleException(new RuntimeException("SyncTransactions", e));
        e.printStackTrace();

        return false;
      }

      DatabaseObject db = DatabaseObject.getInstance();

      synchronized(db.databaseLock) {
        db.beginTransaction(mParent);
        try {


          if(startPage == 0) {
            // Remove all old transactions
            int deleted = db.delete(mParent, TransactionEntry.TABLE_NAME,
                    TransactionEntry.COLUMN_NAME_ACCOUNT + " = ? AND " + TransactionEntry.COLUMN_NAME_STATUS + " != ?",
                    new String[] { Integer.toString(activeAccount), "delayed" });
            Log.d("Coinbase", deleted + " rows deleted.");
          }

          // Update user ID
          Editor editor = prefs.edit();
          editor.putString(String.format(Constants.KEY_ACCOUNT_ID, activeAccount), currentUserId);
          editor.commit();

          p.segmentDone("Set up database");

          int i = startPage * 100;
          for(JSONObject transaction : transactions) {

            ContentValues values = new ContentValues();

            String createdAtStr = transaction.optString("created_at", null);
            long createdAt;
            try {
              if(createdAtStr != null) {
                createdAt = ISO8601.toCalendar(createdAtStr).getTimeInMillis();
              } else {
                createdAt = -1;
              }
            } catch (ParseException e) {
              // Error parsing createdAt
              e.printStackTrace();
              createdAt = -1;
            }

            String id = transaction.getString("transaction_id");
            if(extraInfo.containsKey(id)) {
              values.put(TransactionEntry.COLUMN_NAME_TRANSACTION_JSON, extraInfo.get(id).toString());
            }

            values.put(TransactionEntry._ID, id);
            values.put(TransactionEntry.COLUMN_NAME_JSON, transaction.toString());
            values.put(TransactionEntry.COLUMN_NAME_TIME, createdAt);
            values.put(TransactionEntry.COLUMN_NAME_ACCOUNT, activeAccount);
            values.put(TransactionEntry.COLUMN_NAME_ORDER, i);
            values.put(TransactionEntry.COLUMN_NAME_STATUS, transaction.optBoolean("confirmed") ? "complete" : "pending");

            db.insert(mParent, TransactionEntry.TABLE_NAME, null, values);
            i++;
          }

          db.setTransactionSuccessful(mParent);
          mLastLoadedPage = loadedPage;
          p.segmentDone("Insert transactions to database");

        } catch (JSONException e) {
          // Malformed response from Coinbase.
          Log.e("Coinbase", "Could not parse JSON response from Coinbase, aborting refresh of transactions.");
          e.printStackTrace();

          return false;
        } finally {

          db.endTransaction(mParent);
        }
      }

      // Update list
      loadTransactionsList();

      // Update transaction widgets
      updateWidgets();

      // Update the buy / sell history list
      mParent.getBuySellFragment().onTransactionsSynced();

      p.segmentDone("Done");

      return true;
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private void updateWidgets() {
      if(PlatformUtils.hasHoneycomb()) {
        AppWidgetManager widgetManager = AppWidgetManager.getInstance(mParent);
        widgetManager.notifyAppWidgetViewDataChanged(
          widgetManager.getAppWidgetIds(new ComponentName(mParent, TransactionsAppWidgetProvider.class)),
          R.id.widget_list);
      }
    }

    @Override
    protected void onPreExecute() {

      ((MainActivity) mParent).setRefreshButtonAnimated(true);
      mBalanceBtc = mBalanceNative = mCurrencyNative = null;

      if(mSyncErrorView != null) {
        mSyncErrorView.setVisibility(View.GONE);
      }
    }

    @Override
    protected void onPostExecute(Boolean result) {

      ((MainActivity) mParent).setRefreshButtonAnimated(false);

      if(result != null && !result && mSyncErrorView != null) {
        mSyncErrorView.setVisibility(View.VISIBLE);

        // If we're disconnected from the internet, a sync error is expected, so
        // don't show an alarming red error message
        if (Utils.isConnectedOrConnecting(mParent)) {
          // Problem
          mSyncErrorView.setText(R.string.transactions_refresh_error);
          mSyncErrorView.setBackgroundColor(mParent.getResources().getColor(R.color.transactions_sync_error_critical));
        } else {
          // Internet is just disconnected
          mSyncErrorView.setText(R.string.transactions_internet_error);
          mSyncErrorView.setBackgroundColor(mParent.getResources().getColor(R.color.transactions_sync_error_calm));
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mParent);
        int activeAccount = prefs.getInt(Constants.KEY_ACTIVE_ACCOUNT, -1);
        if(LoginManager.getInstance().getAccountValid(mParent, activeAccount) != null) {
          // Request failed because account is no longer valid
          if(getFragmentManager() != null) {
            new AccountInvalidDialogFragment().show(getFragmentManager(), "accountinvalid");
          }
        }
      } else {
        // Successful sync. This is a good time to check for any left over delayed TX.
        mParent.startService(new Intent(mParent, DelayedTxSenderService.class));
      }

      mSyncTask = null;
    }

  }

  private class TransactionViewBinder implements SimpleCursorAdapter.ViewBinder {

    @Override
    public boolean setViewValue(View arg0, Cursor arg1, int arg2) {

      try {
        return setViewValue(arg0, arg1.getString(arg2));
      } catch (JSONException e) {
        // Malformed transaction JSON.
        Log.e("Coinbase", "Corrupted database entry! " + arg1.getInt(arg1.getColumnIndex(TransactionEntry._ID)));
        e.printStackTrace();

        return true;
      }
    }

    public boolean setViewValue(View arg0, String item) throws JSONException {

      JSONObject json = null;
      if (arg0.getId() != R.id.transaction_status) {
        json = new JSONObject(new JSONTokener(item));
      }

        switch(arg0.getId()) {

          case R.id.transaction_status:
            String status = item.toLowerCase(Locale.CANADA);
            String readable;
            int scolor;
            if (status.equals("complete")) {
              readable = getString(R.string.transaction_status_complete);
              scolor = R.color.transaction_inlinestatus_complete;
            } else if (status.equals("pending")) {
              readable = getString(R.string.transaction_status_pending);
              scolor = R.color.transaction_inlinestatus_pending;
            } else if (status.equals("delayed")) {
              readable = getString(R.string.transaction_status_delayed);
              scolor = R.color.transaction_inlinestatus_delayed;
            } else {
              readable = status;
              scolor = R.color.transaction_inlinestatus_complete;
            }

            ((TextView) arg0).setText(readable);
            ((TextView) arg0).setTextColor(getResources().getColor(scolor));
            ((TextView) arg0).setTypeface(FontManager.getFont(mParent, "RobotoCondensed-Regular"));
            return true;

          case R.id.transaction_title:

            ((TextView) arg0).setText(Utils.generateTransactionSummary(mParent, json));
            ((TextView) arg0).setTypeface(FontManager.getFont(mParent, "Roboto-Light"));
            return true;

          case R.id.transaction_amount:

            String amount = json.getJSONObject("amount").getString("amount");
            String currency = json.getJSONObject("amount").getString("currency");
            String balanceString = Utils.formatCurrencyAmount(amount);
            if(balanceString.startsWith("-")) {
              balanceString = balanceString.substring(1);
            }
            if ("BTC".equals(currency)) {
              balanceString = "\u0E3F" + balanceString;
            } else {
              balanceString = balanceString + " " + currency;
            }

            int sign = new BigDecimal(amount).compareTo(BigDecimal.ZERO);
            int color = sign == -1 ? R.color.transaction_negative : (sign == 0 ? R.color.transaction_neutral : R.color.transaction_positive);

            ((TextView) arg0).setText(balanceString);
            ((TextView) arg0).setTextColor(getResources().getColor(color));
            return true;
        }

        return false;
    }
  }

  private class LoadTransactionsTask extends AsyncTask<Void, Void, Cursor> {

    @Override
    protected Cursor doInBackground(Void... params) {

      SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mParent);
      int activeAccount = prefs.getInt(Constants.KEY_ACTIVE_ACCOUNT, -1);

      Cursor c = DatabaseObject.getInstance().query(mParent, TransactionsDatabase.TransactionEntry.TABLE_NAME,
        null, TransactionEntry.COLUMN_NAME_ACCOUNT + " = ?", new String[] { Integer.toString(activeAccount) }, null, null, TransactionEntry.COLUMN_NAME_ORDER + " ASC");
      Log.i("Coinbase", "Loaded " + c.getCount() + " rows.");
      return c;
    }

    @Override
    protected void onPostExecute(Cursor result) {

      if(mListView != null && result != null) {

        setHeaderPinned(!result.moveToFirst());
        mListFooter.setVisibility(canLoadMorePages() ? View.VISIBLE : View.GONE);

        // "rate me" notice
        Constants.RateNoticeState rateNoticeState = Constants.RateNoticeState.valueOf(Utils.getPrefsString(mParent, Constants.KEY_ACCOUNT_RATE_NOTICE_STATE, Constants.RateNoticeState.NOTICE_NOT_YET_SHOWN.name()));
        boolean showRateNotice = rateNoticeState == Constants.RateNoticeState.SHOULD_SHOW_NOTICE;

        if(mListView.getAdapter() != null) {

          // Just update existing adapter
          getAdapter().changeCursor(result);
          getAdapter(InsertedItemListAdapter.class).setInsertedViewVisible(showRateNotice);
          return;
        }

        String[] from = { TransactionEntry.COLUMN_NAME_JSON, TransactionEntry.COLUMN_NAME_JSON,
                          TransactionEntry.COLUMN_NAME_STATUS, TransactionEntry.COLUMN_NAME_JSON };
        int[] to = { R.id.transaction_title, R.id.transaction_amount,
                     R.id.transaction_status, R.id.transaction_currency };
        SimpleCursorAdapter adapter = new SimpleCursorAdapter(mParent, R.layout.fragment_transactions_item, result,
          from, to, 0);
        adapter.setViewBinder(new TransactionViewBinder());

        View rateNotice = getRateNotice();
        InsertedItemListAdapter wrappedAdapter = new InsertedItemListAdapter(adapter, rateNotice, 2);
        wrappedAdapter.setInsertedViewVisible(showRateNotice);

        mListView.setAdapter(wrappedAdapter);
      }
    }
  }

  private class TransactionsInfiniteScrollListener implements OnScrollListener {

    @Override
    public void onScroll(AbsListView view, int firstVisibleItem,
                         int visibleItemCount, int totalItemCount) {

      int padding = 2;
      boolean shouldLoadMore = firstVisibleItem + visibleItemCount + padding >= totalItemCount;

      if(shouldLoadMore && canLoadMorePages()) {

        // Load more transactions
        if(mSyncTask == null) {
          Log.i("Coinbase", "Infinite scroll is loading more pages (last loaded page " + mLastLoadedPage + ", max " + mMaxPage + ")");
          mSyncTask = new SyncTransactionsTask();
          mSyncTask.execute(mLastLoadedPage);
        }
      }
    }

    @Override
    public void onScrollStateChanged(AbsListView view, int scrollState) {
      // Unused
    }
  }

  MainActivity mParent;

  boolean mBalanceLoading, mAnimationPlaying;
  FrameLayout mListHeaderContainer;
  ListView mListView;
  ViewGroup mListHeader, mMainView;
  View mListFooter;
  View mRateNotice;
  TextView mBalanceText, mBalanceHome;
  TextView mSyncErrorView;
  PullToRefreshLayout mPullToRefreshLayout;
  boolean mDetailsShowing = false;
  String mBalanceBtc = null, mBalanceNative = null, mCurrencyNative = null;

  SyncTransactionsTask mSyncTask;
  int mLastLoadedPage = -1, mMaxPage = -1;

  @Override
  public void onCreate(Bundle savedInstanceState) {

    super.onCreate(savedInstanceState);
  }

  @Override
  public void onAttach(Activity activity) {

    super.onAttach(activity);
    mParent = (MainActivity) activity;
  }

  @Override
  public void onSaveInstanceState(Bundle outState) {

    super.onSaveInstanceState(outState);

    if(mBalanceText != null) {
      outState.putString("balance_text", mBalanceText.getText().toString());
    }
    outState.putBoolean("details_showing", mDetailsShowing);
  }

  private boolean canLoadMorePages() {
    return mLastLoadedPage != -1 && mLastLoadedPage < SyncTransactionsTask.MAX_ENDLESS_PAGES &&
            mLastLoadedPage < mMaxPage;
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container,
                           Bundle savedInstanceState) {

    // Inflate base layout
    ViewGroup view = (ViewGroup) inflater.inflate(R.layout.fragment_transactions, container, false);
    mMainView = (ViewGroup) view.findViewById(R.id.inner_view);

    mListView = (ListView) view.findViewById(android.R.id.list);

    // Inflate header (which contains account balance)
    mListHeader = (ViewGroup) inflater.inflate(R.layout.fragment_transactions_header, null, false);
    mListHeaderContainer = new FrameLayout(mParent);
    setHeaderPinned(true);
    mListView.addHeaderView(mListHeaderContainer);

    // Footer
    ViewGroup listFooterParent = (ViewGroup) inflater.inflate(R.layout.fragment_transactions_footer, null, false);
    mListFooter = listFooterParent.findViewById(R.id.transactions_footer_text);
    mListView.addFooterView(listFooterParent);

    // Header card swipe
    boolean showBalance = Utils.getPrefsBool(mParent, Constants.KEY_ACCOUNT_SHOW_BALANCE, true);
    mListHeader.findViewById(R.id.wallet_layout).setVisibility(showBalance ? View.VISIBLE : View.GONE);
    mListHeader.findViewById(R.id.wallet_hidden_notice).setVisibility(showBalance ? View.GONE : View.VISIBLE);
    final BalanceTouchListener balanceTouchListener = new BalanceTouchListener(mListHeader.findViewById(R.id.wallet_layout),
            null, new BalanceTouchListener.OnDismissCallback() {
      @Override
      public void onDismiss(View view, Object token) {

        // Hide balance
        mListHeader.findViewById(R.id.wallet_layout).setVisibility(View.GONE);
        mListHeader.findViewById(R.id.wallet_hidden_notice).setVisibility(View.VISIBLE);

        // Save in preferences
        PreferenceManager.getDefaultSharedPreferences(mParent).edit()
                .putBoolean(String.format(Constants.KEY_ACCOUNT_SHOW_BALANCE, Utils.getActiveAccount(mParent)), false)
                .commit();
      }
    });
    mListHeader.setOnTouchListener(balanceTouchListener);

    if (Build.VERSION.SDK_INT >= 11) {
      LayoutTransition transition = new LayoutTransition();
      //transition.disableTransitionType(LayoutTransition.CHANGE_DISAPPEARING);
      mListHeader.setLayoutTransition(transition);
    }

    mListHeader.findViewById(R.id.wallet_hidden_show).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {

        // Show balance
        mListHeader.findViewById(R.id.wallet_layout).setVisibility(View.VISIBLE);
        mListHeader.findViewById(R.id.wallet_hidden_notice).setVisibility(View.GONE);
        balanceTouchListener.reset();

        // Save in preferences
        PreferenceManager.getDefaultSharedPreferences(mParent).edit()
                .putBoolean(String.format(Constants.KEY_ACCOUNT_SHOW_BALANCE, Utils.getActiveAccount(mParent)), true)
                .commit();
      }
    });

    mListView.setOnScrollListener(new TransactionsInfiniteScrollListener());

    mBalanceText = (TextView) mListHeader.findViewById(R.id.wallet_balance);
    mBalanceHome = (TextView) mListHeader.findViewById(R.id.wallet_balance_home);
    mSyncErrorView = (TextView) mListHeader.findViewById(R.id.wallet_error);

    ((TextView) view.findViewById(R.id.wallet_balance_label)).setTypeface(
            FontManager.getFont(mParent, "RobotoCondensed-Regular"));
    ((TextView) view.findViewById(R.id.wallet_send_label)).setTypeface(
           FontManager.getFont(mParent, "RobotoCondensed-Regular"));

    mBalanceText.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        Utils.togglePrefsBool(mParent, Constants.KEY_ACCOUNT_BALANCE_FUZZY, true);
        setBalance((String) v.getTag());
      }
    });

    // Load old balance
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mParent);
    int activeAccount = prefs.getInt(Constants.KEY_ACTIVE_ACCOUNT, -1);
    String oldBalance = prefs.getString(String.format(Constants.KEY_ACCOUNT_BALANCE, activeAccount), null);
    String oldHomeBalance = prefs.getString(String.format(Constants.KEY_ACCOUNT_BALANCE_HOME, activeAccount), null);
    String oldHomeCurrency = prefs.getString(String.format(Constants.KEY_ACCOUNT_BALANCE_HOME_CURRENCY, activeAccount), null);

    if(oldBalance != null) {
      try {
        setBalance(oldBalance);
      } catch (NumberFormatException e) {
        // Old versions of the app would store the balance in a localized format
        // ex. in some countries with the decimal separator "," instead of "."
        // Restoring balance will fail on upgrade, so just ignore it
        // and reload the balance from the network
      }
      mBalanceText.setTextColor(mParent.getResources().getColor(R.color.wallet_balance_color));
      mBalanceHome.setText(String.format(mParent.getString(R.string.wallet_balance_home), oldHomeBalance, oldHomeCurrency));
    }

    if(mBalanceLoading) {

      mBalanceText.setTextColor(mParent.getResources().getColor(R.color.wallet_balance_color_invalid));
    }

    view.findViewById(R.id.wallet_send).setOnClickListener(new View.OnClickListener() {

      @Override
      public void onClick(View v) {

        mParent.openTransferMenu(false);
      }
    });

    // Configure pull to refresh
    mPullToRefreshLayout = new PullToRefreshLayout(mParent);
    AbsDefaultHeaderTransformer ht =
            (AbsDefaultHeaderTransformer) new AbsDefaultHeaderTransformer();
    ActionBarPullToRefresh.from(mParent)
            .insertLayoutInto(view)
            .theseChildrenArePullable(android.R.id.list)
            .listener(new OnRefreshListener() {
              @Override
              public void onRefreshStarted(View view) {
                mParent.refresh();
              }
            })
            .options(Options.create().headerTransformer(ht).build())
            .setup(mPullToRefreshLayout);
    ht.setPullText("Swipe down to refresh");
    ht.setRefreshingText("Refreshing...");

    // Load transaction list
    loadTransactionsList();

    if (savedInstanceState != null && savedInstanceState.getBoolean("details_showing", false)) {

      mDetailsShowing = true;
      view.findViewById(R.id.transaction_details_background).setVisibility(View.VISIBLE);
    }

    return view;
  }

  @Override
  public void onConfigurationChanged(Configuration newConfig) {
    super.onConfigurationChanged(newConfig);
    //mPullToRefreshAttacher.onConfigurationChanged(newConfig);
  }

  private void setBalance(String balance) {

    boolean fuzzy = Utils.getPrefsBool(mParent, Constants.KEY_ACCOUNT_BALANCE_FUZZY, true);
    String formatted = Utils.formatCurrencyAmount(balance, false, fuzzy ? CurrencyType.BTC_FUZZY : CurrencyType.BTC);
    mBalanceText.setText(String.format("%1$s BTC", formatted));
    mBalanceText.setTag(balance);
  }

  private View getRateNotice() {

    if (mRateNotice != null) {
      return mRateNotice;
    }

    View rateNotice = View.inflate(mParent, R.layout.fragment_transactions_rate_notice, null);

    ((TextView) rateNotice.findViewById(R.id.rate_notice_title)).setTypeface(FontManager.getFont(mParent, "Roboto-Light"));

    TextView btnPositive = (TextView) rateNotice.findViewById(R.id.rate_notice_btn_positive),
            btnNegative = (TextView) rateNotice.findViewById(R.id.rate_notice_btn_negative);
    btnPositive.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        // Permanently hide notice
        setRateNoticeState(Constants.RateNoticeState.NOTICE_DISMISSED, true);
        // Open Play Store
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.coinbase.android")));
      }
    });
    btnNegative.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        // Permanently hide notice
        setRateNoticeState(Constants.RateNoticeState.NOTICE_DISMISSED, true);
      }
    });

    mRateNotice = rateNotice;
    return rateNotice;
  }

  public void setRateNoticeState(Constants.RateNoticeState state, boolean force) {

    Constants.RateNoticeState rateNoticeState = Constants.RateNoticeState.valueOf(Utils.getPrefsString(mParent, Constants.KEY_ACCOUNT_RATE_NOTICE_STATE, Constants.RateNoticeState.NOTICE_NOT_YET_SHOWN.name()));
    if (rateNoticeState == Constants.RateNoticeState.NOTICE_DISMISSED && !force) {
      return;
    }

    Utils.putPrefsString(mParent, Constants.KEY_ACCOUNT_RATE_NOTICE_STATE, state.name());
    if (getAdapter() != null) {
      getAdapter(InsertedItemListAdapter.class).setInsertedViewVisible(state == Constants.RateNoticeState.SHOULD_SHOW_NOTICE);
      getAdapter().notifyDataSetChanged();
    }
  }

  // Refresh just account balance.
  private void refreshBalance() {

    mBalanceLoading = true;
    mBalanceText.setTextColor(mParent.getResources().getColor(R.color.wallet_balance_color_invalid));
    Utils.runAsyncTaskConcurrently(new LoadJustBalanceTask());
  }

  private void updateBalance() {

    if (mBalanceBtc == null || mBalanceText == null) {
      return; // Not ready yet.
    }

    // Balance is loaded! update the view
    mBalanceLoading = false;

    try {
      SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mParent);
      int activeAccount = prefs.getInt(Constants.KEY_ACTIVE_ACCOUNT, -1);

      // Save balance in preferences
      Editor editor = prefs.edit();
      editor.putString(String.format(Constants.KEY_ACCOUNT_BALANCE, activeAccount), mBalanceBtc);
      editor.putString(String.format(Constants.KEY_ACCOUNT_BALANCE_HOME, activeAccount), mBalanceNative);
      editor.putString(String.format(Constants.KEY_ACCOUNT_BALANCE_HOME_CURRENCY, activeAccount), mCurrencyNative);
      editor.commit();

      // Update the view.
      mBalanceText.setTextColor(mParent.getResources().getColor(R.color.wallet_balance_color));
      setBalance(mBalanceBtc);
      mBalanceHome.setText(String.format(mParent.getString(R.string.wallet_balance_home), mBalanceNative, mCurrencyNative));
    } catch (Exception e) {
      e.printStackTrace();
      ACRA.getErrorReporter().handleException(new RuntimeException("updateBalance()", e));
    }
  }

  public void refresh() {

    // Make balance appear invalidated
    mBalanceLoading = true;
    mBalanceText.setTextColor(mParent.getResources().getColor(R.color.wallet_balance_color_invalid));

    // Reload transactions + balance
    if(mSyncTask == null) {
      mSyncTask = new SyncTransactionsTask();
      Utils.runAsyncTaskConcurrently(mSyncTask);
    }
  }

  public void refreshComplete() {
    mPullToRefreshLayout.setRefreshComplete();
  }

  private void setHeaderPinned(boolean pinned) {

    mMainView.removeView(mListHeader);
    mListHeaderContainer.removeAllViews();

    if(pinned) {
      mMainView.addView(mListHeader, 0);
      System.out.println("Main view has " + mMainView.getChildCount());
    } else {
      mListHeaderContainer.addView(mListHeader);
    }
  }

  public void insertTransactionAnimated(final int insertAtIndex, final JSONObject transaction, final String category, final String status) {

    if (!PlatformUtils.hasHoneycomb()) {
      // Do not play animation!
      try {
        Utils.insertTransaction(mParent, transaction, Utils.createAccountChangeForTransaction(mParent, transaction, category), status);
        loadTransactionsList();
      } catch (Exception e) {
        throw new RuntimeException("Malformed JSON from Coinbase", e);
      }
      refreshBalance();
      return;
    }

    mAnimationPlaying = true;
    getListView().setEnabled(false);
    setRateNoticeState(Constants.RateNoticeState.NOTICE_NOT_YET_SHOWN, false);
    refreshBalance();
    getListView().post(new Runnable() {
      @Override
      public void run() {
        getListView().setSelection(0);
        getListView().postDelayed(new Runnable() {
          public void run() {
            _insertTransactionAnimated(insertAtIndex, transaction, category, status);
          }
        }, 500);
      }
    });
  }

  @TargetApi(Build.VERSION_CODES.HONEYCOMB)
  private void _insertTransactionAnimated(int insertAtIndex, JSONObject transaction, String category, String status) {

    // Step 1
    // Take a screenshot of the relevant part of the list view and put it over top of the real one
    Bitmap bitmap;
    final FrameLayout root = (FrameLayout) getView().findViewById(R.id.root);
    int height = 0, heightToCropOff = 0;
    boolean animateListView = true;
    if (mListHeaderContainer.getChildCount() > 0) { // Header not pinned
      bitmap = Bitmap.createBitmap(getListView().getWidth(), getListView().getHeight(), Bitmap.Config.ARGB_8888);
      Canvas canvas = new Canvas(bitmap);
      getListView().draw(canvas);
      for (int i = 0; i <= insertAtIndex; i++) {
        heightToCropOff += getListView().getChildAt(i).getHeight();
      }
      height = getListView().getHeight() - heightToCropOff;
    } else { // Header pinned
      bitmap = null; // No list view animation is needed
      animateListView = false;
      heightToCropOff = mListHeader.getHeight();
      height = root.getHeight() - heightToCropOff;
    }

    DisplayMetrics metrics = getResources().getDisplayMetrics();
    final ImageView fakeListView = new ImageView(mParent);
    fakeListView.setImageBitmap(bitmap);

    Matrix m = new Matrix();
    m.setTranslate(0, -heightToCropOff);
    fakeListView.setImageMatrix(m);
    fakeListView.setScaleType(ImageView.ScaleType.MATRIX);

    FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, height);
    params.topMargin = heightToCropOff + getListView().getDividerHeight();
    fakeListView.setLayoutParams(params);

    // Step 2
    // Create a fake version of the new list item and a background for it to animate onto
    JSONObject accountChange;
    View newListItem;
    try {
      accountChange = Utils.createAccountChangeForTransaction(mParent, transaction, category);

      newListItem = View.inflate(mParent, R.layout.fragment_transactions_item, null);
      TransactionViewBinder binder = new TransactionViewBinder();
      for (int i : new int[] { R.id.transaction_title, R.id.transaction_amount,
              R.id.transaction_currency }) {
        if (newListItem.findViewById(i) != null) {
          binder.setViewValue(newListItem.findViewById(i), accountChange.toString());
        }
      }
      binder.setViewValue(newListItem.findViewById(R.id.transaction_status), status);
      newListItem.setBackgroundColor(Color.WHITE);
    } catch (JSONException e) {
      throw new RuntimeException("Malformed JSON from Coinbase", e);
    }
    int itemHeight = (int)(70 * metrics.density);
    FrameLayout.LayoutParams itemParams = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, itemHeight);
    itemParams.topMargin = heightToCropOff + getListView().getDividerHeight(); // account for divider
    newListItem.setLayoutParams(itemParams);

    final View background = new View(mParent);
    background.setBackgroundColor(animateListView ? Color.parseColor("#eeeeee") : Color.WHITE);
    FrameLayout.LayoutParams bgParams = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, height);
    bgParams.topMargin = heightToCropOff;
    background.setLayoutParams(bgParams);

    root.addView(background, root.getChildCount());
    if (animateListView) {
      root.addView(fakeListView, root.getChildCount());
    }
    root.addView(newListItem, root.getChildCount());

    // Step 3
    // Animate
    AnimatorSet set = new AnimatorSet();

    newListItem.setTranslationX(-metrics.widthPixels);
    ObjectAnimator itemAnimation = ObjectAnimator.ofFloat(newListItem, "translationX", -metrics.widthPixels, 0);
    ObjectAnimator listAnimation = ObjectAnimator.ofFloat(fakeListView, "translationY", 0, itemHeight);

    if (animateListView) {
      set.playSequentially(listAnimation, itemAnimation);
    } else {
      set.play(itemAnimation);
    }
    set.setDuration(300);
    final View _newListItem = newListItem;
    set.addListener(new Animator.AnimatorListener() {
      @Override
      public void onAnimationStart(Animator animation) {

      }

      @Override
      public void onAnimationEnd(Animator animation) {
        mAnimationPlaying = false;
        root.removeView(_newListItem);
        root.removeView(fakeListView);
        root.removeView(background);
        getListView().setEnabled(true);
      }

      @Override
      public void onAnimationCancel(Animator animation) {

      }

      @Override
      public void onAnimationRepeat(Animator animation) {

      }
    });
    set.start();

    // Step 4
    // Now that the animation is started, update the actual list values behind-the-scenes
    Utils.insertTransaction(mParent, transaction, accountChange, status);
    loadTransactionsList();
  }

  @TargetApi(Build.VERSION_CODES.HONEYCOMB)
  public void loadTransactionsList() {
    if (PlatformUtils.hasHoneycomb()) {
      new LoadTransactionsTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    } else {
      new LoadTransactionsTask().execute();
    }
  }

  @Override
  public void onResume() {

    super.onResume();
  }

  private CursorAdapter getAdapter() {
    return getAdapter(CursorAdapter.class);
  }

  private <T> T getAdapter(Class<T> adapterType) {
    Adapter adapter = mListView.getAdapter();
    while (adapter instanceof WrapperListAdapter && !adapterType.equals(adapter.getClass())) {
      adapter = ((WrapperListAdapter) adapter).getWrappedAdapter(); // Un-wrap adapter
    }
    return (T) adapter;
  }

  @Override
  public void onListItemClick(ListView l, View v, int position, long id) {

    if (mDetailsShowing) {
      return;
    }

    Cursor c = (Cursor) l.getItemAtPosition(position);
    if (c == null) {
      return; // Header / footer / rate notice
    }

    String transactionId = c.getString(c.getColumnIndex(TransactionEntry._ID));
    Bundle args = new Bundle();
    args.putString(TransactionDetailsFragment.EXTRA_ID, transactionId);
    TransactionDetailsFragment fragment = new TransactionDetailsFragment();
    fragment.setArguments(args);

    FragmentTransaction transaction = getChildFragmentManager().beginTransaction();
    transaction.add(R.id.transaction_details_host, fragment);
    transaction.addToBackStack("details");
    transaction.commit();

    showDetails();
  }

  private void showDetails() {

    mDetailsShowing = true;

    // 1. animate
    getView().findViewById(R.id.transaction_details_background).setVisibility(View.VISIBLE);
    getView().findViewById(R.id.transaction_details_background).startAnimation(
            AnimationUtils.loadAnimation(mParent, R.anim.transactiondetails_bg_enter));
    getView().findViewById(R.id.transaction_details_host).startAnimation(
            AnimationUtils.loadAnimation(mParent, R.anim.transactiondetails_enter));

    // 2. if necessary, change action bar
    mParent.setInTransactionDetailsMode(true);

    // 3. pull to refresh
    mPullToRefreshLayout.setEnabled(false);
  }

  protected void hideDetails(boolean animated) {

    mDetailsShowing = false;

    if(animated) {
      Animation bg = AnimationUtils.loadAnimation(mParent, R.anim.transactiondetails_bg_exit);
      bg.setAnimationListener(new Animation.AnimationListener() {
        @Override
        public void onAnimationStart(Animation animation) {

        }

        @Override
        public void onAnimationEnd(Animation animation) {
          getView().findViewById(R.id.transaction_details_background).setVisibility(View.GONE);
        }

        @Override
        public void onAnimationRepeat(Animation animation) {

        }
      });
      getView().findViewById(R.id.transaction_details_background).startAnimation(bg);
    } else {
      getView().findViewById(R.id.transaction_details_background).setVisibility(View.GONE);
    }

    FragmentTransaction transaction = getChildFragmentManager().beginTransaction();
    transaction.setCustomAnimations(0, animated ? R.anim.transactiondetails_exit : 0);
    transaction.remove(getChildFragmentManager().findFragmentById(R.id.transaction_details_host));
    transaction.commit();

    // 2. action bar
    mParent.setInTransactionDetailsMode(false);

    // 3. pull to refresh
    mPullToRefreshLayout.setEnabled(true);
  }

  public boolean onBackPressed() {

    if(mDetailsShowing) {
      hideDetails(true);
      return true;
    } else {
      return false;
    }
  }

  @Override
  public void onSwitchedTo() {

    int appUsageCount = Utils.getPrefsInt(mParent, Constants.KEY_ACCOUNT_APP_USAGE, 0);
    if (appUsageCount >= 2 && !mAnimationPlaying) {
      setRateNoticeState(Constants.RateNoticeState.SHOULD_SHOW_NOTICE, false);
    }
  }

  @Override
  public void onPINPromptSuccessfulReturn() {
    if (mDetailsShowing) {

      ((TransactionDetailsFragment ) getChildFragmentManager().findFragmentById(R.id.transaction_details_host)).onPINPromptSuccessfulReturn();
    } else {
      // Not used
    }
  }

  @Override
  public String getTitle() {
    return getString(R.string.title_transactions);
  }
}
