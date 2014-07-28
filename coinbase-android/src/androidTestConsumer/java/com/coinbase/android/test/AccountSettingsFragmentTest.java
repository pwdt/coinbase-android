package com.coinbase.android.test;

import com.coinbase.android.TestAccountSettingsFragmentActivity;
import com.coinbase.api.entity.User;

import static com.coinbase.android.test.MockResponses.mockAddressesResponse;
import static com.coinbase.android.test.MockResponses.mockUser;
import static com.coinbase.android.test.MockResponses.supportedCurrencies;
import static org.mockito.Mockito.*;

public class AccountSettingsFragmentTest extends MockApiTest {
  protected User mockUser;

  public AccountSettingsFragmentTest() {
    super(TestAccountSettingsFragmentActivity.class);
  }

  public void setUp() throws Exception {
    super.setUp();

    mockUser = mockUser();

    doReturn(mockUser).when(mockCoinbase).getUser();
    doReturn(supportedCurrencies()).when(mockCoinbase).getSupportedCurrencies();
    doReturn(mockAddressesResponse()).when(mockCoinbase).getAddresses();

    startTestActivity();
  }

  public void testName() throws Exception {
    getSolo().waitForDialogToClose();
    assertTrue(getSolo().searchText("Name"));
    assertTrue(getSolo().searchText(mockUser.getName()));
    getSolo().clickOnText("Name");
    getSolo().clearEditText(0);
    getSolo().enterText(0, "Newlywed User");
    getSolo().clickOnText("OK");
    getSolo().waitForDialogToClose();
    assertTrue(getSolo().searchText("Newlywed User"));

    verify(mockCoinbase, times(1)).updateUser(anyString(), (User) any());
  }

  public void testEmail() throws Exception {
    getSolo().waitForDialogToClose();
    assertTrue(getSolo().searchText("Email"));
    assertTrue(getSolo().searchText(mockUser.getEmail()));
    getSolo().clickOnText("Email");
    getSolo().clearEditText(0);
    getSolo().enterText(0, "newlywed@example.com");
    getSolo().clickOnText("OK");
    getSolo().waitForDialogToClose();
    assertTrue(getSolo().searchText("newlywed@example.com"));

    verify(mockCoinbase, times(1)).updateUser(anyString(), (User) any());
  }

  public void testMerchantTools() throws Exception {
    getSolo().waitForDialogToClose();
    assertTrue(getSolo().searchText("Merchant tools"));
    assertTrue(getSolo().searchText("Now separate app"));

    // TODO Test that clicking takes us to external activity to download merchant app
  }

  public void testLimits() throws Exception {
    getSolo().waitForDialogToClose();
    assertTrue(getSolo().searchText("limits"));
    assertTrue(getSolo().searchText("Buy 3,000.00 USD per day, sell 3,000.00 USD per day"));

    // TODO Test that clicking takes us to external activity to view limits
  }

  public void testPin() throws Exception {
    getSolo().waitForDialogToClose();
    assertTrue(getSolo().searchText("passcode"));
    getSolo().clickOnText("passcode");
    getSolo().sleep(1000);
    getSolo().clickOnText("Require passcode to open the app");
    getSolo().clickOnText("OK");
    getSolo().sleep(1000);
    getSolo().clickOnText("1");
    getSolo().clickOnText("2");
    getSolo().clickOnText("3");
    getSolo().clickOnText("4");
    getSolo().clickOnText("SUBMIT");
    getSolo().sleep(1000);
    assertTrue(getSolo().searchText("Require passcode to open the app"));
  }

  public void testTimezone() throws Exception {
    getSolo().waitForDialogToClose();
    assertTrue(getSolo().searchText("Time zone"));
    assertTrue(getSolo().searchText("Pacific Time \\(US & Canada\\)"));
    getSolo().clickOnText("Time zone");
    getSolo().scrollListToTop(0);
    getSolo().clickOnText("\\(GMT-07:00\\) Mazatlan");
    getSolo().clickOnText("OK");
    getSolo().waitForDialogToClose();
    assertTrue(getSolo().searchText("Mazatlan"));

    verify(mockCoinbase, times(1)).updateUser(anyString(), (User) any());
  }

  public void testNativeCurrency() throws Exception {
    getSolo().waitForDialogToClose();
    assertTrue(getSolo().searchText("Native currency"));
    assertTrue(getSolo().searchText("USD"));
    getSolo().clickOnText("Native currency");
    getSolo().scrollListToTop(0);
    getSolo().clickOnText("CAD");
    getSolo().clickOnText("OK");
    getSolo().waitForDialogToClose();
    assertTrue(getSolo().searchText("CAD"));

    verify(mockCoinbase, times(1)).updateUser(anyString(), (User) any());
  }

  public void testReceiveAddresses() throws Exception {
    getSolo().waitForDialogToClose();
    assertTrue(getSolo().searchText("Bitcoin address"));
    assertTrue(getSolo().searchText(mockAddressesResponse().getAddresses().get(0).getAddress()));
    getSolo().clickOnText("Bitcoin address");
    getSolo().sleep(1000);
    assertEquals("ReceiveAddressesActivity", getSolo().getCurrentActivity().getLocalClassName());
  }

  @Override
  public void tearDown() throws Exception {
    verify(mockCoinbase, atLeast(0)).getSupportedCurrencies();
    verify(mockCoinbase, atLeast(0)).getAddresses();
    verify(mockCoinbase, atLeast(0)).getUser();
    super.tearDown();
  }
}
