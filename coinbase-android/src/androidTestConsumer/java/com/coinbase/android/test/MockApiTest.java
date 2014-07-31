package com.coinbase.android.test;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.test.ActivityInstrumentationTestCase2;

import com.coinbase.android.Constants;
import com.coinbase.android.TestCaseEntryPointActivity;
import com.coinbase.android.event.BusModule;
import com.coinbase.api.Coinbase;
import com.coinbase.api.LoginManager;
import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.util.Modules;
import com.robotium.solo.Solo;
import com.squareup.otto.Bus;

import org.joda.money.Money;

import roboguice.RoboGuice;
import roboguice.config.DefaultRoboModule;

import static com.coinbase.android.test.MockResponses.mockUser;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.*;

public abstract class MockApiTest extends ActivityInstrumentationTestCase2 {
  private Solo solo;
  protected Coinbase mockCoinbase;
  protected LoginManager mockLoginManager;
  protected Bus testBus;

  /**
   * Real test activity.
   */
  protected Class<? extends Activity> testActivityClass;

  public class MockLoginManagerModule implements Module {
    @Override
    public void configure(Binder binder) {
      binder.bind(LoginManager.class).toInstance(mockLoginManager);
    }
  }

  public class TestBusModule implements Module {
    @Override
    public void configure(Binder binder) {
      binder.bind(Bus.class).toInstance(testBus);
    }
  }

  public MockApiTest(final Class<? extends Activity> testActivityClass) {
    // - Setting an empty activity as test entry point.
    super(TestCaseEntryPointActivity.class);

    // - Real test activity.
    this.testActivityClass = testActivityClass;
  }

  public void setUp() throws Exception {
    super.setUp();

    System.setProperty("dexmaker.dexcache", getInstrumentation().getTargetContext().getCacheDir().getPath());

    mockCoinbase = mock(Coinbase.class);
    mockLoginManager = mock(LoginManager.class);
    testBus = new Bus();

    // TODO remove the methods that take contexts
    doReturn(true).when(mockLoginManager).isSignedIn((Context) any());

    doReturn(mockCoinbase).when(mockLoginManager).getClient();
    doReturn(mockCoinbase).when(mockLoginManager).getClient(anyInt());
    doReturn(1).when(mockLoginManager).getActiveAccount();
    doReturn(true).when(mockLoginManager).isSignedIn();
    doReturn(mockUser().getId()).when(mockLoginManager).getActiveUserId();

    Application application = getActivity().getApplication();

    Module roboGuiceModule = RoboGuice.newDefaultRoboModule(application);
    Module testModules = Modules.combine(new TestBusModule(), new MockLoginManagerModule());
    Module testModule = Modules.override(roboGuiceModule).with(testModules);
    RoboGuice.setBaseApplicationInjector(application, RoboGuice.DEFAULT_STAGE, testModule);

    // Clear preferences
    SharedPreferences defaultPreferences = PreferenceManager.getDefaultSharedPreferences(getInstrumentation().getTargetContext());
    defaultPreferences.edit().clear().commit();

    // TODO Remove this when we centralize access through loginmanager
    SharedPreferences.Editor e = defaultPreferences.edit();
    e.putInt(Constants.KEY_ACTIVE_ACCOUNT, 1);
    e.commit();

    // - Initialize Robotium driver.
    solo = new Solo(getInstrumentation(), getActivity());
  }

  public void tearDown() throws Exception {
    ignoreSafeApiCalls();
    verifyNoMoreInteractions(mockCoinbase);

    Application app = getActivity().getApplication();
    DefaultRoboModule defaultModule = RoboGuice.newDefaultRoboModule(app);
    RoboGuice.setBaseApplicationInjector(app, RoboGuice.DEFAULT_STAGE, defaultModule);
    getSolo().finishOpenedActivities();
    super.tearDown();
  }

  protected void startTestActivity() {
    final Intent intent = new Intent(getActivity(), testActivityClass);
    solo.getCurrentActivity().startActivity(intent);
  }

  // We use strict verification to ensure no extraneous unsafe calls are made (purchases/transfers)
  // but we can ignore safe api calls
  protected void ignoreSafeApiCalls() throws Exception {
    verify(mockCoinbase, atLeast(0)).getContacts();
    verify(mockCoinbase, atLeast(0)).getContacts(anyString());
    verify(mockCoinbase, atLeast(0)).getExchangeRates();
    verify(mockCoinbase, atLeast(0)).getSupportedCurrencies();
    verify(mockCoinbase, atLeast(0)).getAddresses();
    verify(mockCoinbase, atLeast(0)).getUser();
    verify(mockCoinbase, atLeast(0)).getSellQuote(any(Money.class));
    verify(mockCoinbase, atLeast(0)).getBuyQuote(any(Money.class));
    verify(mockCoinbase, atLeast(0)).getTransactions();
  }

  protected Solo getSolo() {
    return solo;
  }

}
