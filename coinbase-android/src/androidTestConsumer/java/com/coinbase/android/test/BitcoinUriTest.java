package com.coinbase.android.test;

import com.coinbase.android.util.BitcoinUri;

import junit.framework.TestCase;

import java.math.BigDecimal;

public class BitcoinUriTest extends TestCase {

  public void testParsing() throws Exception {
    String uriString = "bitcoin:1NLsANR9nhi91CrekNPuvNoUy549cuEUZU?amount=2&message=derp&label=thelabel";
    BitcoinUri bitcoinUri = BitcoinUri.parse(uriString);
    assertEquals("1NLsANR9nhi91CrekNPuvNoUy549cuEUZU", bitcoinUri.getAddress());
    assertEquals(new BigDecimal("2.00000000"), bitcoinUri.getAmount());
    assertEquals("derp", bitcoinUri.getMessage());
    assertEquals("thelabel", bitcoinUri.getLabel());
  }

  public void testGenerating() throws Exception {
    BitcoinUri uri = new BitcoinUri();
    uri.setAddress("1NLsANR9nhi91CrekNPuvNoUy549cuEUZU");
    uri.setAmount(new BigDecimal("2.00000000"));
    uri.setMessage("derp");
    uri.setLabel("theLabel");
    assertEquals("bitcoin:1NLsANR9nhi91CrekNPuvNoUy549cuEUZU?amount=2.00000000&message=derp&label=theLabel", uri.toString());
  }

}
