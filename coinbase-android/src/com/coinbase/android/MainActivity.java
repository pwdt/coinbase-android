package com.coinbase.android;

import java.util.ArrayList;
import java.util.List;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.ActionBarDrawerToggle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.widget.DrawerLayout;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.ViewFlipper;

import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;
import com.coinbase.android.CoinbaseActivity.RequiresAuthentication;
import com.coinbase.android.CoinbaseActivity.RequiresPIN;
import com.coinbase.android.merchant.MerchantToolsFragment;
import com.coinbase.android.merchant.PointOfSaleFragment;
import com.coinbase.api.LoginManager;
import com.google.zxing.client.android.Intents;
import com.justinschultz.pusherclient.Pusher;

@RequiresAuthentication
@RequiresPIN
public class MainActivity extends CoinbaseActivity implements AccountsFragment.ParentActivity {

  public static final String ACTION_SCAN = "com.siriusapplications.coinbase.MainActivity.ACTION_SCAN";
  public static final String ACTION_TRANSFER = "com.siriusapplications.coinbase.MainActivity.ACTION_TRANSFER";
  public static final String ACTION_TRANSACTIONS = "com.siriusapplications.coinbase.MainActivity.ACTION_TRANSACTIONS";

  private static final String KEY_VISIBLE_FRAGMENT = "KEY_VISIBLE_FRAGMENT";

  private static final long RESUME_REFRESH_INTERVAL = 1 * 60 * 1000;

  public static class SignOutFragment extends DialogFragment {


    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {

      AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
      builder.setMessage(R.string.sign_out_confirm);

      builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int id) {
          // Sign out
          ((MainActivity) getActivity()).changeAccount(-1);
        }
      });

      builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int id) {
          // Dismiss
        }
      });

      return builder.create();
    }
  }

  public static final int NUM_FRAGMENTS = 6;
  public static final int FRAGMENT_INDEX_TRANSACTIONS = 0;
  public static final int FRAGMENT_INDEX_TRANSFER = 1;
  public static final int FRAGMENT_INDEX_BUYSELL = 2;
  public static final int FRAGMENT_INDEX_ACCOUNT = 3;
  public static final int FRAGMENT_INDEX_MERCHANT_TOOLS = 4;
  public static final int FRAGMENT_INDEX_POINT_OF_SALE = 5;

  private int[] mFragmentTitles = new int[] {
     R.string.title_transactions,
     R.string.title_transfer,
     R.string.title_buysell,
     R.string.title_account,
     R.string.title_merchant_tools,
     R.string.title_point_of_sale,
  };
  private int[] mFragmentIcons = new int[] {
     R.drawable.ic_action_transactions,
     R.drawable.ic_action_transfer,
     R.drawable.ic_action_buysell,
     R.drawable.ic_action_account,
     R.drawable.ic_action_merchant_tools,
     R.drawable.ic_action_point_of_sale,
  };
  private boolean[] mFragmentHasSpacerAfter = new boolean[] {
     false,
     false,
     false,
     true,
     false,
     false,
  };
  private boolean[] mFragmentKeyboardPreferredStatus = new boolean[] {
     false,
     true,
     true,
     false,
     false,
     true,
  };
  private boolean[] mFragmentVisible = new boolean[] {
     true,
     true,
     true,
     true,
     true,
     true,
  };
  private CoinbaseFragment[] mFragments = new CoinbaseFragment[NUM_FRAGMENTS];

  ViewFlipper mViewFlipper;
  TransactionsFragment mTransactionsFragment;
  BuySellFragment mBuySellFragment;
  TransferFragment mTransferFragment;
  AccountSettingsFragment mSettingsFragment;
  MerchantToolsFragment mMerchantToolsFragment;
  PointOfSaleFragment mPointOfSaleFragment;
  OnSharedPreferenceChangeListener mSharedPreferenceChangeListener;
  DrawerLayout mSlidingMenu;
  ActionBarDrawerToggle mDrawerToggle;
  Pusher mPusher;
  MenuItem mRefreshItem;
  ListView mMenuListView;
  boolean mRefreshItemState = false;
  boolean mSlidingMenuCompatShowing = false;
  long mLastRefreshTime = -1;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    // Set up the ViewFlipper
    mViewFlipper = (ViewFlipper) findViewById(R.id.flipper);

    // Configure the DrawerLayout
    mSlidingMenu = (DrawerLayout) findViewById(R.id.main_layout);
    mMenuListView = (ListView) findViewById(R.id.drawer);
    mDrawerToggle = new ActionBarDrawerToggle(this, mSlidingMenu,
      R.drawable.ic_drawer, R.string.drawer_open, R.string.drawer_close) {

      int lastTimeIndex = 0;

      @Override
      public void onDrawerClosed(View drawerView) {
        onSlidingMenuClosed(lastTimeIndex != mViewFlipper.getDisplayedChild());
        lastTimeIndex = mViewFlipper.getDisplayedChild();
        updateTitle();
      }

      @Override
      public void onDrawerOpened(View drawerView) {
        updateTitle();
      }
      
    };
    mSlidingMenu.setDrawerListener(mDrawerToggle);
    mSlidingMenu.setDrawerShadow(R.drawable.drawer_shadow, Gravity.LEFT);
    getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    getSupportActionBar().setHomeButtonEnabled(true);

    // Set up Sliding Menu list
    mMenuListView.setAdapter(new SectionsListAdapter());
    mMenuListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {

      @Override
      public void onItemClick(AdapterView<?> arg0, View arg1, int arg2,
                              long arg3) {

        int fragment = (Integer) ((BaseAdapter) arg0.getAdapter()).getItem(arg2);
        if(fragment != -1) {
          switchTo(fragment);
        }
      }
    });

    // Refresh everything on app launch
    new Thread(new Runnable() {
      public void run() {
        runOnUiThread(new Runnable() {
          public void run() {
            // refresh();
          }
        });
      }
    }).start();
    

    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
    mSharedPreferenceChangeListener = new OnSharedPreferenceChangeListener() {

      @Override
      public void onSharedPreferenceChanged(SharedPreferences arg0, String arg1) {

        if(arg1.endsWith("enable_merchant_tools")) {
          // Update merchant tools visibility
          updateMerchantToolsVisibility();
        }
      }
    };
    prefs.registerOnSharedPreferenceChangeListener(mSharedPreferenceChangeListener);
    updateMerchantToolsVisibility();

    switchTo(0);

    onNewIntent(getIntent());
  }

  @Override
  protected void onPostCreate(Bundle savedInstanceState) {
    super.onPostCreate(savedInstanceState);
    // Sync the toggle state after onRestoreInstanceState has occurred.
    mDrawerToggle.syncState();
  }

  @Override
  public void onConfigurationChanged(Configuration newConfig) {
    super.onConfigurationChanged(newConfig);
    mDrawerToggle.onConfigurationChanged(newConfig);
  }
  
  private void updateMerchantToolsVisibility() {
    
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
    int activeAccount = prefs.getInt(Constants.KEY_ACTIVE_ACCOUNT, -1);
    String key = String.format(Constants.KEY_ACCOUNT_ENABLE_MERCHANT_TOOLS, activeAccount);
    mFragmentVisible[FRAGMENT_INDEX_MERCHANT_TOOLS] = false; //prefs.getBoolean(key, false);
    mFragmentVisible[FRAGMENT_INDEX_POINT_OF_SALE] = prefs.getBoolean(key, false);
    ((BaseAdapter) mMenuListView.getAdapter()).notifyDataSetChanged();
  }

  @Override
  public void onAttachFragment(Fragment fragment) {
    super.onAttachFragment(fragment);

    if(fragment instanceof TransactionsFragment) {
      mFragments[FRAGMENT_INDEX_TRANSACTIONS] = (CoinbaseFragment) fragment;
      mTransactionsFragment = (TransactionsFragment) fragment;
    } else if(fragment instanceof BuySellFragment) {
      mFragments[FRAGMENT_INDEX_BUYSELL] = (CoinbaseFragment) fragment;
      mBuySellFragment = (BuySellFragment) fragment;
    } else if(fragment instanceof TransferFragment) {
      mFragments[FRAGMENT_INDEX_TRANSFER] = (CoinbaseFragment) fragment;
      mTransferFragment = (TransferFragment) fragment;
    } else if(fragment instanceof AccountSettingsFragment) {
      mFragments[FRAGMENT_INDEX_ACCOUNT] = (CoinbaseFragment) fragment;
      mSettingsFragment = (AccountSettingsFragment) fragment;
    } else if(fragment instanceof MerchantToolsFragment) {
      mFragments[FRAGMENT_INDEX_MERCHANT_TOOLS] = (CoinbaseFragment) fragment;
      mMerchantToolsFragment = (MerchantToolsFragment) fragment;
    } else if(fragment instanceof PointOfSaleFragment) {
      mFragments[FRAGMENT_INDEX_POINT_OF_SALE] = (CoinbaseFragment) fragment;
      mPointOfSaleFragment = (PointOfSaleFragment) fragment;
    }
  }

  @Override
  protected void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);

    outState.putInt(KEY_VISIBLE_FRAGMENT, mViewFlipper.getDisplayedChild());
  }

  @Override
  protected void onRestoreInstanceState(Bundle savedInstanceState) {
    super.onRestoreInstanceState(savedInstanceState);

    // Update title, in case restoring the instance state has changed the current fragment
    switchTo(savedInstanceState.getInt(KEY_VISIBLE_FRAGMENT));
  }

  /**
   * Switch visible fragment.
   * @param index See the FRAGMENT_INDEX constants.
   */
  public void switchTo(int index) {

    boolean fragmentChanged = mViewFlipper.getDisplayedChild() != index;

    mViewFlipper.setDisplayedChild(index);
    updateTitle();
    mFragments[index].onSwitchedTo();

    hideSlidingMenu(fragmentChanged);
  }

  /** Called when close animation is complete */
  private void onSlidingMenuClosed(boolean fragmentChanged) {

    if(fragmentChanged) {

      boolean keyboardPreferredStatus = mFragmentKeyboardPreferredStatus[mViewFlipper.getDisplayedChild()];
      InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
  
      if(keyboardPreferredStatus) {
        inputMethodManager.toggleSoftInput(InputMethodManager.SHOW_FORCED, InputMethodManager.HIDE_IMPLICIT_ONLY);
      } else {
        inputMethodManager.hideSoftInputFromWindow(findViewById(android.R.id.content).getWindowToken(), 0);
      }
    }
  }

  private boolean isSlidingMenuShowing() {

    return mSlidingMenu.isDrawerOpen(Gravity.LEFT);
  }

  private void toggleSlidingMenu() {
    if(isSlidingMenuShowing()) {
      hideSlidingMenu(false);
    } else {
      showSlidingMenu();
    }
  }

  @Override
  public void onBackPressed() {

    if(isSlidingMenuShowing()) {
      hideSlidingMenu(false);
    }
  }

  private void showSlidingMenu() {

    mSlidingMenu.openDrawer(Gravity.LEFT);
  }

  private void hideSlidingMenu(boolean fragmentChanged) {

    if(mSlidingMenu != null) {
      mSlidingMenu.closeDrawers();
    }
  }

  private void updateTitle() {

    if((mSlidingMenu != null && isSlidingMenuShowing()) || mSlidingMenu.getDrawerLockMode(Gravity.LEFT) == DrawerLayout.LOCK_MODE_LOCKED_OPEN) {
      getSupportActionBar().setTitle(R.string.app_name);
    } else {
      getSupportActionBar().setTitle(mFragmentTitles[mViewFlipper.getDisplayedChild()]);
    }
  }

  @Override
  protected void onNewIntent(Intent intent) {

    super.onNewIntent(intent);
    setIntent(intent);

    if(intent.getData() != null && "bitcoin".equals(intent.getData().getScheme())) {
      // Handle bitcoin: URI
      switchTo(FRAGMENT_INDEX_TRANSFER);
      mTransferFragment.fillFormForBitcoinUri(getIntent().getData());
    } else if(ACTION_SCAN.equals(intent.getAction())) {
      // Scan barcode
      startBarcodeScan();
    } else if(ACTION_TRANSFER.equals(intent.getAction())) {

      switchTo(FRAGMENT_INDEX_TRANSFER);
    } else if(ACTION_TRANSACTIONS.equals(intent.getAction())) {

      switchTo(FRAGMENT_INDEX_TRANSACTIONS);
    }
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getSupportMenuInflater().inflate(R.menu.activity_main, menu);
    mRefreshItem = menu.findItem(R.id.menu_refresh);
    setRefreshButtonAnimated(mRefreshItemState);
    return true;
  }

  @Override
  public void onResume() {
    super.onResume();

    // Connect to pusher
    new Thread(new Runnable() {
      public void run() {

        // mPusher = new Pusher(CoinbasePusherListener.API_KEY);
        // mPusher.setPusherListener(new CoinbasePusherListener(mPusher, MainActivity.this));
        // mPusher.connect();
      }
    }).start();

    // Refresh
    if((System.currentTimeMillis() - mLastRefreshTime) > RESUME_REFRESH_INTERVAL) {
      refresh();
    }
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();

    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
    prefs.unregisterOnSharedPreferenceChangeListener(mSharedPreferenceChangeListener);
  }

  @Override
  protected void onPause() {
    super.onPause();

    if(mPusher != null) {
      mPusher.disconnect();
      mPusher = null;
    }

    // Since we manually opened the keyboard, we must close it when switching
    // away from the app
    InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
    inputMethodManager.hideSoftInputFromWindow(
      findViewById(android.R.id.content).getWindowToken(), 0);
  }

  public void openTransferMenu(boolean isRequest) {

    switchTo(FRAGMENT_INDEX_TRANSFER);
    mTransferFragment.switchType(isRequest);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {

    switch(item.getItemId()) {
      case R.id.menu_accounts:
        new AccountsFragment().show(getSupportFragmentManager(), "accounts");
        return true;
      case R.id.menu_sign_out:
        new SignOutFragment().show(getSupportFragmentManager(), "signOut");
        return true;
      case R.id.menu_about:
        startActivity(new Intent(this, AboutActivity.class));
        return true;
      case R.id.menu_barcode:
        startBarcodeScan();
        return true;
      case R.id.menu_refresh:
        if(isSlidingMenuShowing()){
          hideSlidingMenu(false);
        }

        refresh();
        return true;
      case R.id.menu_help:
        Intent helpIntent = new Intent(Intent.ACTION_VIEW);
        helpIntent.setData(Uri.parse("http://support.coinbase.com/"));
        startActivity(helpIntent);
        return true;
      case android.R.id.home:
        toggleSlidingMenu();
        return true;
    }

    return super.onOptionsItemSelected(item);
  }

  public class SectionsListAdapter extends BaseAdapter {

    private Integer[] items = null;

    public SectionsListAdapter() {
      buildData();
    }

    @Override
    public void notifyDataSetChanged() {
      buildData();
      super.notifyDataSetChanged();
    }

    private void buildData() {

      List<Integer> itemsList = new ArrayList<Integer>();
      for(int i = 0; i < NUM_FRAGMENTS; i++) {

        if(mFragmentVisible[i]) {
          itemsList.add(i);
        }

        if(mFragmentHasSpacerAfter[i]) {
          itemsList.add(-1);
        }
      }
      if(itemsList.get(itemsList.size() - 1) == -1) {
        itemsList.remove(itemsList.size() - 1); // Do not end in a divider
      }
      items = itemsList.toArray(new Integer[0]);
    }

    @Override
    public int getCount() {

      return items.length;
    }

    @Override
    public Object getItem(int position) {
      return items[position];
    }

    @Override
    public long getItemId(int position) {
      return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {

      if(convertView == null) {
        convertView = View.inflate(MainActivity.this, R.layout.activity_main_menu_item, null);
      }

      TextView title = (TextView) convertView.findViewById(R.id.main_menu_item_title);
      ImageView icon = (ImageView) convertView.findViewById(R.id.main_menu_item_icon);

      int fragment = items[position];

      if(fragment == -1) {
        // Spacer
        title.setText(null);
        icon.setImageDrawable(null);
        return convertView;
      }

      String name = getString(mFragmentTitles[fragment]);
      title.setText(name);

      icon.setImageResource(mFragmentIcons[fragment]);

      return convertView;
    }

  }

  public void onAccountChosen(int account) {
    changeAccount(account);
  }

  public void changeAccount(int account) {

    if(account == -1) {

      // Delete current account
      LoginManager.getInstance().deleteCurrentAccount(this);
    } else {

      // Change active account
      LoginManager.getInstance().switchActiveAccount(this, account);
    }

    finish();
    startActivity(new Intent(this, MainActivity.class));
  }

  public void onAddAccount() {

    Intent intent = new Intent(this, LoginActivity.class);
    intent.putExtra(LoginActivity.EXTRA_SHOW_INTRO, false);
    startActivity(intent);
    finish();
  }

  public void startBarcodeScan() {

    Intent intent = new Intent(this, com.google.zxing.client.android.CaptureActivity.class);
    intent.setAction(Intents.Scan.ACTION);
    intent.putExtra("SCAN_MODE", "QR_CODE_MODE");
    startActivityForResult(intent, 0);
  }

  public void onActivityResult(int requestCode, int resultCode, Intent intent) {
    if (requestCode == 0) {
      /*
       * Barcode scan
       */
      if (resultCode == RESULT_OK) {

        String contents = intent.getStringExtra("SCAN_RESULT");
        String format = intent.getStringExtra("SCAN_RESULT_FORMAT");

        Uri uri = Uri.parse(contents);
        if(uri != null && "bitcoin".equals(uri.getScheme())) {
          // Is bitcoin URI
          switchTo(FRAGMENT_INDEX_TRANSFER); // Switch to transfer fragment
          mTransferFragment.fillFormForBitcoinUri(uri);
        }

      } else if (resultCode == RESULT_CANCELED) {
        // Barcode scan was cancelled
      }
    } else if(requestCode == 1) {
      /*
       * Transaction details
       */
      if(resultCode == RESULT_OK) {
        // Refresh needed
        refresh();
      }
    }
  }

  public BuySellFragment getBuySellFragment() {
    return mBuySellFragment;
  }

  public TransferFragment getTransferFragment() {
    return mTransferFragment;
  }

  public AccountSettingsFragment getAccountSettingsFragment() {
    return mSettingsFragment;
  }

  public void setRefreshButtonAnimated(boolean animated) {

    mRefreshItemState = animated;

    if(mRefreshItem == null) {
      return;
    }

    if(animated) {
      mRefreshItem.setEnabled(false);
      mRefreshItem.setActionView(R.layout.actionbar_indeterminate_progress);
    } else {
      mRefreshItem.setEnabled(true);
      mRefreshItem.setActionView(null);
    }
  }

  public void refresh() {

    mLastRefreshTime = System.currentTimeMillis();

    mTransactionsFragment.refresh();
    mBuySellFragment.refresh();
    mTransferFragment.refresh();
    mSettingsFragment.refresh();
    mPointOfSaleFragment.refresh();
  }
}
