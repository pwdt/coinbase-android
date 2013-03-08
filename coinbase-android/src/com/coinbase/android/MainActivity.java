package com.coinbase.android;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
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
import com.coinbase.api.LoginManager;
import com.google.zxing.client.android.Intents;
import com.justinschultz.pusherclient.Pusher;
import com.slidingmenu.lib.SlidingMenu;

@RequiresAuthentication
public class MainActivity extends CoinbaseActivity {

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

  private enum SlidingMenuMode {
    NORMAL,
    PINNED,
    FAKE_GINGERBREAD_COMPAT;
  }

  public static final int FRAGMENT_INDEX_TRANSACTIONS = 0;
  public static final int FRAGMENT_INDEX_TRANSFER = 1;
  public static final int FRAGMENT_INDEX_BUYSELL = 2;
  public static final int FRAGMENT_INDEX_ACCOUNT = 3;

  private int[] mFragmentTitles = new int[] {
      R.string.title_transactions,
      R.string.title_transfer,
      R.string.title_buysell,
      R.string.title_account,
  };
  private int[] mFragmentIcons = new int[] {
      R.drawable.ic_action_transactions,
      R.drawable.ic_action_transfer,
      R.drawable.ic_action_buysell,
      R.drawable.ic_action_account
  };
  private boolean[] mFragmentKeyboardPreferredStatus = new boolean[] {
      false,
      true,
      true,
      false,
  };
  private CoinbaseFragment[] mFragments = new CoinbaseFragment[4];

  ViewFlipper mViewFlipper;
  TransactionsFragment mTransactionsFragment;
  BuySellFragment mBuySellFragment;
  TransferFragment mTransferFragment;
  AccountSettingsFragment mSettingsFragment;
  SlidingMenu mSlidingMenu;
  Pusher mPusher;
  MenuItem mRefreshItem;
  boolean mRefreshItemState = false;
  SlidingMenuMode mSlidingMenuMode = SlidingMenuMode.NORMAL;
  boolean mSlidingMenuCompatShowing = false;
  long mLastRefreshTime = -1;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    if(Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
      mSlidingMenuMode = SlidingMenuMode.FAKE_GINGERBREAD_COMPAT;
    } else {
      mSlidingMenuMode = 
          getResources().getBoolean(R.bool.pin_sliding_menu) ? SlidingMenuMode.PINNED : SlidingMenuMode.NORMAL;
    }

    // Set up the ViewFlipper
    mViewFlipper = (ViewFlipper) findViewById(R.id.flipper);

    // configure the SlidingMenu
    mSlidingMenu = new SlidingMenu(this);
    mSlidingMenu.setMode(SlidingMenu.LEFT);
    mSlidingMenu.setTouchModeAbove(SlidingMenu.TOUCHMODE_FULLSCREEN);
    mSlidingMenu.setShadowWidthRes(R.dimen.main_menu_shadow_width);
    mSlidingMenu.setShadowDrawable(R.drawable.main_menu_shadow);
    mSlidingMenu.setBehindWidthRes(R.dimen.main_menu_width);
    mSlidingMenu.setFadeDegree(0f);
    mSlidingMenu.setBehindScrollScale(0);
    mSlidingMenu.setSlidingEnabled(mSlidingMenuMode == SlidingMenuMode.NORMAL);

    if(mSlidingMenuMode != SlidingMenuMode.FAKE_GINGERBREAD_COMPAT) {

      mSlidingMenu.attachToActivity(this, SlidingMenu.SLIDING_CONTENT);
    }

    ListView slidingList;
    if(mSlidingMenuMode == SlidingMenuMode.NORMAL) {

      findViewById(android.R.id.list).setVisibility(View.GONE);
      mSlidingMenu.setMenu(R.layout.activity_main_menu);
      slidingList = (ListView) mSlidingMenu.findViewById(android.R.id.list);
    } else {
      slidingList = (ListView) findViewById(android.R.id.list);

      if(mSlidingMenuMode == SlidingMenuMode.FAKE_GINGERBREAD_COMPAT) {
        findViewById(R.id.main_gingerbread_compat_overlay).setVisibility(View.GONE);
        findViewById(R.id.main_gingerbread_compat_overlay).setOnClickListener(new View.OnClickListener() {

          @Override
          public void onClick(View v) {
            // Close sliding menu
            hideSlidingMenu();
          }
        });
      }
    }

    mSlidingMenu.setOnClosedListener(new SlidingMenu.OnClosedListener() {

      @Override
      public void onClosed() {
        onSlidingMenuClosed();
      }
    });

    mSlidingMenu.setOnCloseListener(new SlidingMenu.OnCloseListener() {

      @Override
      public void onClose() {
        updateTitle();
      }
    });

    mSlidingMenu.setOnOpenListener(new SlidingMenu.OnOpenListener() {

      @Override
      public void onOpen() {
        updateTitle();
      }
    });

    // Set up Sliding Menu list
    slidingList.setAdapter(new SectionsListAdapter());
    slidingList.setOnItemClickListener(new AdapterView.OnItemClickListener() {

      @Override
      public void onItemClick(AdapterView<?> arg0, View arg1, int arg2,
          long arg3) {

        switchTo(arg2);
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

    getSupportActionBar().setDisplayHomeAsUpEnabled(!(mSlidingMenuMode == SlidingMenuMode.PINNED));
    switchTo(0);

    onNewIntent(getIntent());
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


    mViewFlipper.setDisplayedChild(index);
    updateTitle();
    mFragments[index].onSwitchedTo();

    hideSlidingMenu();
  }

  /** Called when close animation is complete */
  private void onSlidingMenuClosed() {

    boolean keyboardPreferredStatus = mFragmentKeyboardPreferredStatus[mViewFlipper.getDisplayedChild()];
    InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);

    if(keyboardPreferredStatus) {
      inputMethodManager.toggleSoftInput(InputMethodManager.SHOW_FORCED, InputMethodManager.HIDE_IMPLICIT_ONLY);
    } else {
      inputMethodManager.hideSoftInputFromWindow(findViewById(android.R.id.content).getWindowToken(), 0);
    }
  }

  private boolean isSlidingMenuShowing() {

    if(mSlidingMenuMode == SlidingMenuMode.FAKE_GINGERBREAD_COMPAT) {
      return mSlidingMenuCompatShowing;
    } else {
      return mSlidingMenu.isMenuShowing();
    }
  }

  private void showSlidingMenu() {

    if(mSlidingMenuMode == SlidingMenuMode.FAKE_GINGERBREAD_COMPAT) {
      findViewById(R.id.main_gingerbread_compat_overlay).setVisibility(View.VISIBLE);
      findViewById(R.id.main_gingerbread_compat_overlay).bringToFront();
      mSlidingMenuCompatShowing = true;
      updateTitle();
    } else {
      mSlidingMenu.showMenu();
    }
  }

  private void hideSlidingMenu() {

    if(mSlidingMenuMode == SlidingMenuMode.FAKE_GINGERBREAD_COMPAT) {
      findViewById(R.id.main_gingerbread_compat_overlay).setVisibility(View.GONE);
      mSlidingMenuCompatShowing = false;
      updateTitle();
      onSlidingMenuClosed();
    } else if(mSlidingMenuMode == SlidingMenuMode.PINNED) {
      // Do nothing
      onSlidingMenuClosed();
    } else {
      if(mSlidingMenu != null) {
        mSlidingMenu.showContent();
      }
    }
  }

  private void updateTitle() {

    getSupportActionBar().setDisplayHomeAsUpEnabled(mSlidingMenuMode != SlidingMenuMode.PINNED &&
        !isSlidingMenuShowing());

    if((mSlidingMenu != null && isSlidingMenuShowing()) || mSlidingMenuMode == SlidingMenuMode.PINNED) {
      getSupportActionBar().setTitle(R.string.app_name);
    } else {
      getSupportActionBar().setTitle(mFragmentTitles[mViewFlipper.getDisplayedChild()]);
    }
  }

  @Override
  public void onConfigurationChanged(Configuration newConfig) {
    super.onConfigurationChanged(newConfig);

    // Screen width may have changed so this needs to be set again
    mSlidingMenu.setBehindWidthRes(R.dimen.main_menu_width);
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
  protected void onPause() {
    super.onPause();

    if(mPusher != null) {
      mPusher.disconnect();
      mPusher = null;
    }
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
      refresh();
      return true;
    case android.R.id.home:
      showSlidingMenu();
      return true;
    }

    return super.onOptionsItemSelected(item);
  }

  public class SectionsListAdapter extends BaseAdapter {

    @Override
    public int getCount() {
      return 4;
    }

    @Override
    public Object getItem(int position) {
      return position;
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

      String name = getString(mFragmentTitles[position]);

      ((TextView) convertView.findViewById(R.id.main_menu_item_title)).setText(name);

      ImageView icon = (ImageView) convertView.findViewById(R.id.main_menu_item_icon);
      icon.setImageResource(mFragmentIcons[position]);

      return convertView;
    }

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

  public void addAccount() {

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
          mViewFlipper.setDisplayedChild(FRAGMENT_INDEX_TRANSFER); // Switch to transfer fragment
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
  }
}
