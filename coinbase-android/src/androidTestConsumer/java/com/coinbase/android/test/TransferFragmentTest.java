package com.coinbase.android.test;

import com.coinbase.android.TestTransferFragmentActivity;
import com.coinbase.api.entity.Transaction;
import com.robotium.solo.Solo;

import static com.coinbase.android.test.MockResponses.mockContacts;
import static com.coinbase.android.test.MockResponses.mockExchangeRates;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class TransferFragmentTest extends MockApiTest {
  public TransferFragmentTest() {
    super(TestTransferFragmentActivity.class);
  }

  public void setUp() throws Exception {
    super.setUp();

    doReturn(mockContacts()).when(mockCoinbase).getContacts();
    doReturn(mockContacts()).when(mockCoinbase).getContacts(anyString());
    doReturn(mockExchangeRates()).when(mockCoinbase).getExchangeRates();
    startTestActivity();
  }

  public void testExchangeRateAndDefaults() throws Exception {
    assertTrue(getSolo().searchText("Send money"));
    assertTrue(getSolo().searchText("USD"));

    getSolo().enterText(1, "1180.46");

    getSolo().waitForText("≈");
    assertTrue(getSolo().searchText("BTC2.00"));
  }

  public void testNativeExchangeRate() throws Exception {
    getSolo().pressSpinnerItem(1, 1); // Choose BTC

    getSolo().enterText(1, "2");

    assertTrue(getSolo().searchText("\\$1,180.46"));
  }

  public void testSendMoney() throws Exception {
    getSolo().enterText(0, "hire@alexianus.com");
    getSolo().enterText(1, "1180.46");
    getSolo().clickOnButton("Send");

    // Confirmation dialog

    // Make sure dialogs don't crash on rotation and preserve state
    getSolo().setActivityOrientation(Solo.LANDSCAPE);
    getInstrumentation().waitForIdleSync();

    assertTrue(getSolo().searchText("\\$1,180.46"));
    assertTrue(getSolo().searchText("hire@alexianus.com"));

    getSolo().clickOnText("OK");

    getSolo().sleep(500);

    verify(mockCoinbase, times(1)).sendMoney(any(Transaction.class));
  }

  public void testChangeCurrency() throws Exception {
    getSolo().enterText(1, "6");

    assertTrue(getSolo().searchText("BTC0.01"));

    getSolo().pressSpinnerItem(1, 1); // Choose BTC

    getSolo().sleep(200);

    assertTrue(getSolo().searchText("\\$3,541.38"));
  }

  public void testRequestEmail() throws Exception {
    getSolo().pressSpinnerItem(0, 1); // Request Money
    assertTrue(getSolo().searchText("Request money"));

    getSolo().enterText(0, "1180.46");
    getSolo().clickOnText("Email");

    // Confirmation dialog
    // Test auto-complete
    getSolo().enterText(0, "us");
    getSolo().waitForText("user@example.com");
    getSolo().clickOnText("user@example.com");

    // Make sure dialogs don't crash on rotation and preserve state
    getSolo().setActivityOrientation(Solo.LANDSCAPE);
    getInstrumentation().waitForIdleSync();

    getSolo().sleep(1000);

    getSolo().clickOnText("user@example.com");
    getSolo().clickOnText("OK");

    getSolo().sleep(1000);

    verify(mockCoinbase, times(1)).requestMoney(any(Transaction.class));
  }
}
