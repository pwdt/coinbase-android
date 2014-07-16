package com.coinbase.android;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolVersion;
import org.robolectric.Robolectric;
import org.robolectric.tester.org.apache.http.TestHttpResponse;

import java.io.InputStream;

import static org.robolectric.Robolectric.*;

public class RobolectricMockResponses {
  public static void setUp() throws Exception {
    ProtocolVersion httpProtocolVersion = new ProtocolVersion("HTTP", 1, 1);

    InputStream ratesResponseInputStream = getShadowApplication().getResources().getAssets().open("rates_response.json");
    String ratesResponseBody = IOUtils.toString(ratesResponseInputStream, "UTF-8");
    HttpResponse ratesResponse = new TestHttpResponse(200, ratesResponseBody);
    Robolectric.addHttpResponseRule("https://coinbase.com:443/api/v1/currencies/exchange_rates", ratesResponse);

    InputStream buyPriceInputStream = getShadowApplication().getResources().getAssets().open("buy_price_response.json");
    String buyPriceBody = IOUtils.toString(buyPriceInputStream, "UTF-8");
    HttpResponse buyPriceResponse = new TestHttpResponse(200, buyPriceBody);
    Robolectric.addHttpResponseRule("https://coinbase.com:443/api/v1/prices/buy?qty=1", buyPriceResponse);

    setDefaultHttpResponse(400, "Not allowed");
  }
}
