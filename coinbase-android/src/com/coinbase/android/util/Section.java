package com.coinbase.android.util;

import com.coinbase.android.MainActivity;

/** Section in MainActivity. */
public enum Section {
  TRANSACTIONS(MainActivity.FRAGMENT_INDEX_TRANSACTIONS),
  SEND_REQUEST(MainActivity.FRAGMENT_INDEX_TRANSFER),
  BUY_SELL(MainActivity.FRAGMENT_INDEX_BUYSELL),
  SETTINGS(MainActivity.FRAGMENT_INDEX_ACCOUNT),
  POINT_OF_SALE(MainActivity.FRAGMENT_INDEX_POINT_OF_SALE);

  int index;
  Section(int index) {
    this.index = index;
  }

  public static Section fromIndex(int index) {
    for (Section s : Section.values()) {
      if (s.index == index) {
        return s;
      }
    }
    return null;
  }
}