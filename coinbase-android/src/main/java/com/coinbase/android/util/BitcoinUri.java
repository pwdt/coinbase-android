package com.coinbase.android.util;

import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicNameValuePair;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Scanner;

public class BitcoinUri {

  public static class InvalidBitcoinUriException extends Exception {
    InvalidBitcoinUriException() {}
    InvalidBitcoinUriException(Throwable ex) { super(ex); }
  }

  public static int BITCOIN_SCALE = 8;

  protected String address;
  protected String label;
  protected String message;

  public BigDecimal getAmount() {
    return amount;
  }

  public void setAmount(BigDecimal amount) {
    this.amount = amount;
  }

  public String getLabel() {
    return label;
  }

  public void setLabel(String label) {
    this.label = label;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public String getAddress() {
    return address;
  }

  public void setAddress(String address) {
    this.address = address;
  }

  protected BigDecimal amount;

  public static BitcoinUri parse(String uriString) throws InvalidBitcoinUriException {
    BitcoinUri result = new BitcoinUri();

    if (!uriString.startsWith("bitcoin:")) {
      throw new InvalidBitcoinUriException();
    }

    String schemeSpecificPart = uriString.substring("bitcoin:".length());
    String[] addressAndParams = schemeSpecificPart.split("\\?");

    result.setAddress(addressAndParams[0]);

    ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();
    URLEncodedUtils.parse(params, new Scanner(addressAndParams[1]), "UTF-8");

    for (NameValuePair param : params) {
      if ("label".equals(param.getName())) {
        result.setLabel(param.getValue());
      } else if ("message".equals(param.getName())) {
        result.setMessage(param.getValue());
      } else if ("amount".equals(param.getName())) {
        try {
          result.setAmount(new BigDecimal(param.getValue()).setScale(BITCOIN_SCALE, RoundingMode.HALF_EVEN));
        } catch (Exception ex) {
          throw new InvalidBitcoinUriException(ex);
        }
      }
    }

    return result;
  }

  public BitcoinUri() {}

  @Override
  public String toString() {
    StringBuilder uriBuilder = new StringBuilder("bitcoin:");

    uriBuilder.append(address);

    ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();
    params.add(new BasicNameValuePair("amount", amount.toPlainString()));
    params.add(new BasicNameValuePair("message", this.message));
    params.add(new BasicNameValuePair("label", this.label));

    uriBuilder.append('?');
    uriBuilder.append(URLEncodedUtils.format(params, "UTF-8"));

    return uriBuilder.toString();
  }

  @Override
  public boolean equals(Object other) {
    if (other instanceof BitcoinUri) {
      return toString().equals(other.toString());
    }
    return false;
  }
}
