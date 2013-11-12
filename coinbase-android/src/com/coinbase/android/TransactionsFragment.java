package com.coinbase.android;

import android.animation.LayoutTransition;
import android.annotation.TargetApi;
import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.ListFragment;
import android.support.v4.widget.CursorAdapter;
import android.support.v4.widget.SimpleCursorAdapter;
import android.text.Html;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.FrameLayout;
import android.widget.HeaderViewListAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.coinbase.android.Utils.CurrencyType;
import com.coinbase.android.db.DatabaseObject;
import com.coinbase.android.db.TransactionsDatabase;
import com.coinbase.android.db.TransactionsDatabase.TransactionEntry;
import com.coinbase.api.LoginManager;
import com.coinbase.api.RpcManager;

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

import uk.co.senab.actionbarpulltorefresh.extras.actionbarsherlock.PullToRefreshAttacher;
import uk.co.senab.actionbarpulltorefresh.library.DefaultHeaderTransformer;
import uk.co.senab.actionbarpulltorefresh.library.PullToRefreshLayout;

public class TransactionsFragment extends ListFragment implements CoinbaseFragment {

  private class LoadBalanceTask extends AsyncTask<Void, Void, String[]> {

    @Override
    protected String[] doInBackground(Void... params) {

      try {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mParent);
        int activeAccount = prefs.getInt(Constants.KEY_ACTIVE_ACCOUNT, -1);

        JSONObject balance = RpcManager.getInstance().callGet(mParent, "account/balance");
        JSONObject exchangeRates = RpcManager.getInstance().callGet(mParent, "currencies/exchange_rates");

        String userHomeCurrency = prefs.getString(String.format(Constants.KEY_ACCOUNT_NATIVE_CURRENCY, activeAccount),
            "usd").toLowerCase(Locale.CANADA);
        BigDecimal homeAmount = new BigDecimal(balance.getString("amount")).multiply(
          new BigDecimal(exchangeRates.getString("btc_to_" + userHomeCurrency)));

        String balanceString = Utils.formatCurrencyAmount(balance.getString("amount"));
        String balanceHomeString = Utils.formatCurrencyAmount(homeAmount, false, CurrencyType.TRADITIONAL);

        String[] result = new String[] { balanceString, balance.getString("currency"),
                                         balanceHomeString, userHomeCurrency.toUpperCase(Locale.CANADA) };

        // Save balance in preferences
        Editor editor = prefs.edit();
        editor.putString(String.format(Constants.KEY_ACCOUNT_BALANCE, activeAccount), result[0]);
        editor.putString(String.format(Constants.KEY_ACCOUNT_BALANCE_HOME, activeAccount), result[2]);
        editor.putString(String.format(Constants.KEY_ACCOUNT_BALANCE_HOME_CURRENCY, activeAccount), result[3]);
        editor.commit();

        return result;

      } catch (IOException e) {

        e.printStackTrace();
      } catch (JSONException e) {

        ACRA.getErrorReporter().handleException(new RuntimeException("LoadBalance", e));
        e.printStackTrace();
      }

      return null;
    }

    @Override
    protected void onPreExecute() {

      mBalanceLoading = true;

      if(mBalanceText != null) {
        mBalanceText.setTextColor(mParent.getResources().getColor(R.color.wallet_balance_color_invalid));
      }
    }

    @Override
    protected void onPostExecute(String[] result) {

      mBalanceLoading = false;

      if(mBalanceText == null) {
        return;
      }

      if(result == null) {
        mBalanceText.setTextColor(mParent.getResources().getColor(R.color.wallet_balance_color_invalid));
      } else {

        mBalanceText.setTextColor(mParent.getResources().getColor(R.color.wallet_balance_color));
        setBalance(result[0]);
        mBalanceHome.setText(String.format(mParent.getString(R.string.wallet_balance_home), result[2], result[3]));
      }
    }

    @Override
    protected void onCancelled(String[] result) {

      mBalanceLoading = false;
    }

  }

  private class SyncTransactionsTask extends AsyncTask<Integer, Void, Boolean> {

    public static final int MAX_PAGES = 3;
    public static final int MAX_ENDLESS_PAGES = 10;

    /**
     * Number of pages of transactions to sync extra transfer-related data.
     */
    public static final int MAX_TRANSFER_SYNC_PAGES = 3;

    @Override
    protected Boolean doInBackground(Integer... params) {

      List<JSONObject> transactions = new ArrayList<JSONObject>();
      String currentUserId = null;
      SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mParent);
      int activeAccount = prefs.getInt(Constants.KEY_ACTIVE_ACCOUNT, -1);
      Map<String, JSONObject> extraInfo = new HashMap<String, JSONObject>();

      int startPage = (params.length == 0 || params[0] == null) ? 0 : params[0];
      int loadedPage;

      // Make API call to download list of transactions
      try {

        int numPages = 1; // Real value will be set after first list iteration
        loadedPage = startPage;

        // Loop is required to sync all pages of transaction history
        for(int i = startPage + 1; i <= startPage + Math.min(numPages, MAX_PAGES); i++) {

          List<BasicNameValuePair> getParams = new ArrayList<BasicNameValuePair>();
          getParams.add(new BasicNameValuePair("page", Integer.toString(i)));
          JSONObject response = RpcManager.getInstance().callGet(mParent, "account_changes", getParams);

          currentUserId = response.getJSONObject("current_user").getString("id");

          JSONArray transactionsArray = response.optJSONArray("account_changes");
          numPages = response.getInt("num_pages");

          if(transactionsArray == null) {
            // No transactions
            continue;
          }

          for(int j = 0; j < transactionsArray.length(); j++) {

            JSONObject transaction = transactionsArray.getJSONObject(j);

            if(transaction == null) {
              continue; // This transaction is empty
            }

            transactions.add(transaction);
          }

          loadedPage++;
        }

        mMaxPage = numPages;

        // Also fetch extra info from /transactions call
        // for first ~30 transactions
        JSONObject extraJson = RpcManager.getInstance().callGet(mParent, "transactions");
        JSONArray extras = extraJson.getJSONArray("transactions");
        for(int i = 0; i < extras.length(); i++) {
          JSONObject extra = extras.getJSONObject(i).getJSONObject("transaction");
          extraInfo.put(extra.getString("id"), extra);
        }

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
            db.delete(mParent, TransactionEntry.TABLE_NAME, TransactionEntry.COLUMN_NAME_ACCOUNT + " = ?", new String[] { Integer.toString(activeAccount) });
          }

          // Update user ID
          Editor editor = prefs.edit();
          editor.putString(String.format(Constants.KEY_ACCOUNT_ID, activeAccount), currentUserId);
          editor.commit();

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

            db.insert(mParent, TransactionEntry.TABLE_NAME, null, values);
          }

          db.setTransactionSuccessful(mParent);
          mLastLoadedPage = loadedPage;

          // Update list
          loadTransactionsList();

          // Update transaction widgets
          updateWidgets();

          // Update the buy / sell history list
          mParent.getBuySellFragment().onTransactionsSynced();

          return true;

        } catch (JSONException e) {
          // Malformed response from Coinbase.
          Log.e("Coinbase", "Could not parse JSON response from Coinbase, aborting refresh of transactions.");
          e.printStackTrace();

          return false;
        } finally {

          db.endTransaction(mParent);
        }
      }
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private void updateWidgets() {
      if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
        AppWidgetManager widgetManager = AppWidgetManager.getInstance(mParent);
        widgetManager.notifyAppWidgetViewDataChanged(
          widgetManager.getAppWidgetIds(new ComponentName(mParent, TransactionsAppWidgetProvider.class)),
          R.id.widget_list);
      }
    }

    @Override
    protected void onPreExecute() {

      ((MainActivity) mParent).setRefreshButtonAnimated(true);

      if(mSyncErrorView != null) {
        mSyncErrorView.setVisibility(View.GONE);
      }
    }

    @Override
    protected void onPostExecute(Boolean result) {

      ((MainActivity) mParent).setRefreshButtonAnimated(false);

      if(result != null && !result && mSyncErrorView != null) {
        mSyncErrorView.setVisibility(View.VISIBLE);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mParent);
        int activeAccount = prefs.getInt(Constants.KEY_ACTIVE_ACCOUNT, -1);
        if(!LoginManager.getInstance().getAccountValid(mParent, activeAccount)) {
          // Request failed because account is no longer valid
          if(getFragmentManager() != null) {
            new AccountInvalidDialogFragment().show(getFragmentManager(), "accountinvalid");
          }
        }
      }

      mSyncTask = null;
    }

  }

  private class TransactionViewBinder implements SimpleCursorAdapter.ViewBinder {

    @Override
    public boolean setViewValue(View arg0, Cursor arg1, int arg2) {

      try {
        JSONObject item = new JSONObject(new JSONTokener(arg1.getString(arg2)));

        switch(arg0.getId()) {

          case R.id.transaction_title:

            ((TextView) arg0).setText(Utils.generateTransactionSummary(mParent, item));
            ((TextView) arg0).setTypeface(FontManager.getFont(mParent, "Roboto-Light"));
            return true;

          case R.id.transaction_amount:

            String amount = item.getJSONObject("amount").getString("amount");
            String balanceString = Utils.formatCurrencyAmount(amount);
            if(balanceString.startsWith("-")) {
              balanceString = balanceString.substring(1);
            }
            balanceString = "\u0E3F" + balanceString;

            int sign = new BigDecimal(amount).compareTo(BigDecimal.ZERO);
            int color = sign == -1 ? R.color.transaction_negative : (sign == 0 ? R.color.transaction_neutral : R.color.transaction_positive);

            ((TextView) arg0).setText(balanceString);
            ((TextView) arg0).setTextColor(getResources().getColor(color));
            return true;

          case R.id.transaction_status:

            boolean confirmed = item.optBoolean("confirmed");

            String readable;
            int scolor;
            if(confirmed) {
              readable = getString(R.string.transaction_status_complete);
              scolor = R.color.transaction_inlinestatus_complete;
            } else {
              readable = getString(R.string.transaction_status_pending);
              scolor = R.color.transaction_inlinestatus_pending;
            }

            ((TextView) arg0).setText(readable);
            ((TextView) arg0).setTextColor(getResources().getColor(scolor));
            ((TextView) arg0).setTypeface(FontManager.getFont(mParent, "RobotoCondensed-Regular"));
            return true;
        }

        return false;
      } catch (JSONException e) {
        // Malformed transaction JSON.
        Log.e("Coinbase", "Corrupted database entry! " + arg1.getInt(arg1.getColumnIndex(TransactionEntry._ID)));
        e.printStackTrace();

        return true;
      }
    }
  }

  private class LoadTransactionsTask extends AsyncTask<Void, Void, Cursor> {

    @Override
    protected Cursor doInBackground(Void... params) {

      SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mParent);
      int activeAccount = prefs.getInt(Constants.KEY_ACTIVE_ACCOUNT, -1);

      Cursor c = DatabaseObject.getInstance().query(mParent, TransactionsDatabase.TransactionEntry.TABLE_NAME,
        null, TransactionEntry.COLUMN_NAME_ACCOUNT + " = ?", new String[] { Integer.toString(activeAccount) }, null, null, null);
      return c;
    }

    @Override
    protected void onPostExecute(Cursor result) {

      if(mListView != null && result != null) {

        setHeaderPinned(!result.moveToFirst());

        if(mListView.getAdapter() != null) {

          // Just update existing adapter
          getAdapter().changeCursor(result);
          return;
        }

        String[] from = { TransactionEntry.COLUMN_NAME_JSON, TransactionEntry.COLUMN_NAME_JSON,
                          TransactionEntry.COLUMN_NAME_JSON, TransactionEntry.COLUMN_NAME_JSON };
        int[] to = { R.id.transaction_title, R.id.transaction_amount,
                     R.id.transaction_status, R.id.transaction_currency };
        SimpleCursorAdapter adapter = new SimpleCursorAdapter(mParent, R.layout.fragment_transactions_item, result,
          from, to, 0);
        adapter.setViewBinder(new TransactionViewBinder());
        mListView.setAdapter(adapter);
      }
    }
  }

  private class TransactionsInfiniteScrollListener implements OnScrollListener {

    @Override
    public void onScroll(AbsListView view, int firstVisibleItem,
                         int visibleItemCount, int totalItemCount) {

      int padding = 2;
      boolean shouldLoadMore = firstVisibleItem + visibleItemCount + padding >= totalItemCount;

      if(shouldLoadMore && mLastLoadedPage != -1 && mLastLoadedPage < SyncTransactionsTask.MAX_ENDLESS_PAGES &&
          mLastLoadedPage < mMaxPage) {

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

  boolean mBalanceLoading;
  FrameLayout mListHeaderContainer;
  ListView mListView;
  ViewGroup mListHeader, mMainView;
  TextView mBalanceText, mBalanceHome;
  TextView mSyncErrorView;
  PullToRefreshAttacher mPullToRefreshAttacher;
  boolean mDetailsShowing = false;

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
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container,
                           Bundle savedInstanceState) {

    // Inflate base layout
    ViewGroup view = (ViewGroup) inflater.inflate(R.layout.fragment_transactions, container, false);
    mMainView = view;

    mListView = (ListView) view.findViewById(android.R.id.list);

    // Inflate header (which contains account balance)
    mListHeader = (ViewGroup) inflater.inflate(R.layout.fragment_transactions_header, null, false);
    mListHeaderContainer = new FrameLayout(mParent);
    setHeaderPinned(true);
    mListView.addHeaderView(mListHeaderContainer);

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
    LayoutTransition transition = new LayoutTransition();
    transition.disableTransitionType(LayoutTransition.CHANGE_DISAPPEARING);
    mListHeader.setLayoutTransition(transition);
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

    // Load old balance
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mParent);
    int activeAccount = prefs.getInt(Constants.KEY_ACTIVE_ACCOUNT, -1);
    String oldBalance = prefs.getString(String.format(Constants.KEY_ACCOUNT_BALANCE, activeAccount), null);
    String oldHomeBalance = prefs.getString(String.format(Constants.KEY_ACCOUNT_BALANCE_HOME, activeAccount), null);
    String oldHomeCurrency = prefs.getString(String.format(Constants.KEY_ACCOUNT_BALANCE_HOME_CURRENCY, activeAccount), null);

    if(oldBalance != null) {
      setBalance(oldBalance);
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
    mPullToRefreshAttacher = PullToRefreshAttacher.get(mParent);
    PullToRefreshLayout ptrLayout =
            (PullToRefreshLayout) view.findViewById(R.id.fragment_transactions_ptr);
    ptrLayout.setPullToRefreshAttacher(mPullToRefreshAttacher,
            new uk.co.senab.actionbarpulltorefresh.library.PullToRefreshAttacher.OnRefreshListener() {
      @Override
      public void onRefreshStarted(View view) {
        mParent.refresh();
      }
    });
    DefaultHeaderTransformer ht =
            (DefaultHeaderTransformer) mPullToRefreshAttacher.getHeaderTransformer();
    ht.setPullText("Swipe down to refresh");
    ht.setRefreshingText("Refreshing...");

    // Load transaction list
    loadTransactionsList();

    return view;
  }

  private void setBalance(String balance) {
    mBalanceText.setText(Html.fromHtml(String.format("<b>%1$s</b> BTC", balance)));
  }

  public void refresh() {

    // Reload balance
    new LoadBalanceTask().execute();

    // Reload transactions
    if(mSyncTask == null) {
      mSyncTask = new SyncTransactionsTask();
      mSyncTask.execute();
    }
  }

  public void refreshComplete() {
    mPullToRefreshAttacher.setRefreshComplete();
  }

  private void setHeaderPinned(boolean pinned) {

    mMainView.removeView(mListHeader);
    mListHeaderContainer.removeAllViews();

    if(pinned) {
      mMainView.addView(mListHeader, 0);
    } else {
      mListHeaderContainer.addView(mListHeader);
    }
  }

  @TargetApi(Build.VERSION_CODES.HONEYCOMB)
  private void loadTransactionsList() {
    if (Build.VERSION.SDK_INT>=Build.VERSION_CODES.HONEYCOMB) {
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
    return ((CursorAdapter) ((HeaderViewListAdapter) mListView.getAdapter()).getWrappedAdapter());
  }

  @Override
  public void onListItemClick(ListView l, View v, int position, long id) {

    if(position == 0) {
      return; // Header view
    }

    position--;

    Cursor c = getAdapter().getCursor();
    c.moveToPosition(position);

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
    // Not used
  }
}
