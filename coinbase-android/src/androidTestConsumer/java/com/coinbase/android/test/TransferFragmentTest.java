package com.coinbase.android.test;

import com.coinbase.android.TestTransferFragmentActivity;
import com.coinbase.api.entity.Transaction;
import com.coinbase.api.entity.TransactionsResponse;
import com.robotium.solo.Solo;

import org.joda.money.Money;
import org.joda.time.DateTime;

import java.util.ArrayList;
import java.util.List;

import static com.coinbase.android.test.MockResponses.mockContacts;
import static com.coinbase.android.test.MockResponses.mockExchangeRates;
import static com.coinbase.android.test.MockResponses.mockUser;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class TransferFragmentTest extends MockApiTest {
  public TransferFragmentTest() {
    super(TestTransferFragmentActivity.class);
  }

  public void setUp() throws Exception {
    super.setUp();

    doReturn("1E5RHUhMpBPJQ3U97JCmFtnqJERruE7GQu").when(mockLoginManager).getReceiveAddress();
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

  public void testRequestQRMispaid() throws Exception {
    List<Transaction> mockTransactions = new ArrayList<Transaction>();
    TransactionsResponse mockResponse = new TransactionsResponse();
    mockResponse.setTransactions(mockTransactions);

    doReturn(mockResponse).when(mockCoinbase).getTransactions();

    getSolo().pressSpinnerItem(0, 1); // Request Money
    assertTrue(getSolo().searchText("Request money"));

    getSolo().enterText(0, "1180.46");
    getSolo().enterText(1, "The notes");
    getSolo().clickOnText("QR Code");

    // TODO manually test that it works

    assertTrue(getSolo().searchText("Waiting for payment"));

    getSolo().sleep(2000);

    Transaction mockReceivedTransaction = new Transaction();
    mockReceivedTransaction.setRequest(false);
    mockReceivedTransaction.setCreatedAt(DateTime.now());
    mockReceivedTransaction.setAmount(Money.parse("BTC 1"));
    mockReceivedTransaction.setRecipient(mockUser());
    mockResponse.getTransactions().add(mockReceivedTransaction);

    assertTrue(getSolo().searchText("Received BTC1.00, but you requested"));

    getSolo().clickOnText("OK");
  }

  public void testRequestQRCorrect() throws Exception {
    List<Transaction> mockTransactions = new ArrayList<Transaction>();
    TransactionsResponse mockResponse = new TransactionsResponse();
    mockResponse.setTransactions(mockTransactions);

    doReturn(mockResponse).when(mockCoinbase).getTransactions();

    getSolo().pressSpinnerItem(0, 1); // Request Money
    getSolo().pressSpinnerItem(1, 1); // Choose BTC
    assertTrue(getSolo().searchText("Request money"));

    getSolo().enterText(0, "2.0");
    getSolo().enterText(1, "The notes");
    getSolo().clickOnText("QR Code");

    // TODO manually test that it works

    assertTrue(getSolo().searchText("Waiting for payment"));

    // Make sure dialogs don't crash on rotation and preserve state
    getSolo().setActivityOrientation(Solo.LANDSCAPE);
    getInstrumentation().waitForIdleSync();

    getSolo().sleep(2000);

    Transaction mockReceivedTransaction = new Transaction();
    mockReceivedTransaction.setRequest(false);
    mockReceivedTransaction.setCreatedAt(DateTime.now());
    mockReceivedTransaction.setAmount(Money.parse("BTC 2"));
    mockReceivedTransaction.setRecipient(mockUser());
    mockResponse.getTransactions().add(mockReceivedTransaction);

    getSolo().sleep(1000);

    assertTrue(getSolo().searchText("Received BTC2.00."));

    getSolo().clickOnText("OK");
  }

  // Note, cannot be run on emulator
  /*
  public void testRequestNFCMispaid() throws Exception {
    List<Transaction> mockTransactions = new ArrayList<Transaction>();
    TransactionsResponse mockResponse = new TransactionsResponse();
    mockResponse.setTransactions(mockTransactions);

    doReturn(mockResponse).when(mockCoinbase).getTransactions();

    getSolo().pressSpinnerItem(0, 1); // Request Money
    assertTrue(getSolo().searchText("Request money"));

    getSolo().enterText(0, "1180.46");
    getSolo().enterText(1, "The notes");
    getSolo().clickOnText("NFC");

    // TODO manually test that it works

    assertTrue(getSolo().searchText("Waiting for payment"));

    getSolo().sleep(2000);

    Transaction mockReceivedTransaction = new Transaction();
    mockReceivedTransaction.setRequest(false);
    mockReceivedTransaction.setCreatedAt(DateTime.now());
    mockReceivedTransaction.setAmount(Money.parse("BTC 1"));
    mockReceivedTransaction.setRecipient(mockUser());
    mockResponse.getTransactions().add(mockReceivedTransaction);

    assertTrue(getSolo().searchText("Received BTC1.00, but you requested"));

    getSolo().clickOnText("OK");
  }
  */

  @Override
  public void tearDown() throws Exception {
    verify(mockCoinbase, atLeast(0)).generateReceiveAddress();
    super.tearDown();
  }
}
