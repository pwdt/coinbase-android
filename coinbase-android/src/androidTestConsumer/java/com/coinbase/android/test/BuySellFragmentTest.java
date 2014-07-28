package com.coinbase.android.test;

import com.coinbase.android.BuySellFragment;
import com.coinbase.android.TestAccountSettingsFragmentActivity;
import com.coinbase.android.TestBuySellFragmentActivity;
import com.coinbase.android.TestFragmentActivity;
import com.coinbase.api.entity.Quote;
import com.coinbase.api.entity.User;

import org.joda.money.BigMoneyProvider;
import org.joda.money.Money;
import org.mockito.ArgumentCaptor;

import static com.coinbase.android.test.MockResponses.mockAddressesResponse;
import static com.coinbase.android.test.MockResponses.mockBuyQuote;
import static com.coinbase.android.test.MockResponses.mockBuyTransfer;
import static com.coinbase.android.test.MockResponses.mockSellQuote;
import static com.coinbase.android.test.MockResponses.mockSellTransfer;
import static com.coinbase.android.test.MockResponses.mockUser;
import static com.coinbase.android.test.MockResponses.supportedCurrencies;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

public class BuySellFragmentTest extends MockApiTest {
  public BuySellFragmentTest() {
    super(TestBuySellFragmentActivity.class);
  }

  public void setUp() throws Exception {
    super.setUp();

    doReturn(mockBuyQuote(Money.parse("BTC 1"))).when(mockCoinbase).getBuyQuote(Money.parse("BTC 1"));
    doReturn(mockSellQuote(Money.parse("BTC 1"))).when(mockCoinbase).getSellQuote(Money.parse("BTC 1"));

    startTestActivity();
  }

  public void testBuy() throws Exception {
    doReturn(mockBuyQuote(Money.parse("BTC 1.1"))).when(mockCoinbase).getBuyQuote(Money.parse("BTC 1.1"));
    doReturn(mockBuyTransfer(Money.parse("BTC 1.1"))).when(mockCoinbase).buy(Money.parse("BTC 1.1"));

    getSolo().sleep(1000);
    assertTrue(getSolo().searchText("\\$590\\.23")); // Single BTC subtotal price
    getSolo().enterText(0, "1.1");
    getSolo().sleep(1000);
    assertTrue(getSolo().searchText("\\$649\\.25"));   // Subtotal
    assertTrue(getSolo().searchText("\\$6\\.49"));     // Coinbase Fee
    assertTrue(getSolo().searchText("\\$0\\.15"));     // Bank fee
    assertTrue(getSolo().searchText("\\$655\\.90"));   // Total
    getSolo().clickOnButton("Buy");
    assertTrue(getSolo().searchText("\\$655\\.90"));   // Total displayed for confirmation
    assertTrue(getSolo().searchText("BTC1\\.1"));   // Amount displayed for confirmation
    getSolo().clickOnButton("OK");

    getSolo().sleep(500);

    verify(mockCoinbase, atLeast(0)).getSellQuote(any(Money.class));
    verify(mockCoinbase, atLeast(0)).getBuyQuote(any(Money.class));
    verify(mockCoinbase, times(1)).buy(Money.parse("BTC 1.1"));
  }

  public void testSell() throws Exception {
    doReturn(mockSellQuote(Money.parse("BTC 1.1"))).when(mockCoinbase).getSellQuote(Money.parse("BTC 1.1"));
    doReturn(mockSellTransfer(Money.parse("BTC 1.1"))).when(mockCoinbase).sell(Money.parse("BTC 1.1"));

    getSolo().clickOnText("Sell", 0);
    getSolo().sleep(1000);
    assertTrue(getSolo().searchText("\\$590\\.23")); // Single BTC subtotal price
    getSolo().enterText(0, "1.1");
    getSolo().sleep(1000);
    assertTrue(getSolo().searchText("\\$649\\.25"));   // Subtotal
    assertTrue(getSolo().searchText("\\$6\\.49"));     // Coinbase Fee
    assertTrue(getSolo().searchText("\\$0\\.15"));     // Bank fee
    assertTrue(getSolo().searchText("\\$642\\.61"));   // Total
    getSolo().clickOnButton("Sell");
    assertTrue(getSolo().searchText("\\$642\\.61"));   // Total displayed for confirmation
    assertTrue(getSolo().searchText("BTC1\\.1"));   // Amount displayed for confirmation
    getSolo().clickOnButton("OK");

    getSolo().sleep(500);

    verify(mockCoinbase, atLeast(0)).getSellQuote(any(Money.class));
    verify(mockCoinbase, atLeast(0)).getBuyQuote(any(Money.class));
    verify(mockCoinbase, times(1)).sell(Money.parse("BTC 1.1"));
  }
}
