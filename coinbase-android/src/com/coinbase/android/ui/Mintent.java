package com.coinbase.android.ui;

import com.coinbase.android.util.Section;

/**
 * A sort of "mini-intent" used for navigation around MainActivity
 */
public class Mintent {

  public static final Mintent TRANSACTIONS = new Mintent(Section.TRANSACTIONS, null);
  public static final Mintent SEND_REQUEST = new Mintent(Section.SEND_REQUEST, null);
  public static final Mintent BUY_SELL = new Mintent(Section.BUY_SELL, null);
  public static final Mintent SETTINGS = new Mintent(Section.SETTINGS, null);
  public static final Mintent POINT_OF_SALE = new Mintent(Section.POINT_OF_SALE, null);

  public Section section;
  public Object data;

  public Mintent(Section section, Object data) {
    this.section = section;
    this.data = data;
  }
}
