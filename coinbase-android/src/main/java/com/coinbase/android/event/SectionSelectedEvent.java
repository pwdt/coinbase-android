package com.coinbase.android.event;

import com.coinbase.android.ui.Mintent;
import com.coinbase.android.util.Section;


public class SectionSelectedEvent {
  Section selected;
  public SectionSelectedEvent(Section selected) {
    this.selected = selected;
  }
}
