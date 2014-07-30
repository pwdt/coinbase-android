package com.coinbase.android;

import android.net.Uri;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class BitcoinUri {

  public static class InvalidBitcoinUriException extends Exception {
    InvalidBitcoinUriException() {}
    InvalidBitcoinUriException(Throwable ex) { super(ex); }
  }

  public static int BITCOIN_SCALE = 8;
  public static String BITCOIN_SCHEME = "bitcoin";

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

    Uri uri = Uri.parse(uriString);

    if (!uri.getScheme().equals(BITCOIN_SCHEME)) {
      throw new InvalidBitcoinUriException();
    }

    result.setAddress(uri.getAuthority());
    result.setLabel(uri.getQueryParameter("label"));
    result.setMessage(uri.getQueryParameter("message"));

    String amountString = uri.getQueryParameter("amount");
    if (null != amountString) {
      try {
        result.setAmount(new BigDecimal(amountString).setScale(BITCOIN_SCALE, RoundingMode.HALF_EVEN));
      } catch (Exception ex) {
        throw new InvalidBitcoinUriException(ex);
      }
    }

    return result;
  }

  public BitcoinUri() {}

  public Uri toUri() {
    Uri.Builder uriBuilder = new Uri.Builder()
            .scheme("bitcoin")
            .authority(address);

    if(amount != null) {
      String amountString = amount.setScale(BITCOIN_SCALE, RoundingMode.HALF_EVEN).toPlainString();
      uriBuilder.appendQueryParameter("amount", amountString);
    }

    if(message != null && !"".equals(message)) {
      uriBuilder.appendQueryParameter("message", message);
    }

    if(label != null && !"".equals(label)) {
      uriBuilder.appendQueryParameter("label", message);
    }

    return uriBuilder.build();
  }

  @Override
  public String toString() {
    return toUri().toString();
  }

  @Override
  public boolean equals(Object other) {
    if (other instanceof BitcoinUri) {
      return toUri().equals(((BitcoinUri) other).toUri());
    } else if (other instanceof Uri) {
      return toUri().equals(other);
    } else {
      return false;
    }
  }

}
