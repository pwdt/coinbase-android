package com.coinbase.android.settings;

import java.io.Serializable;

class Timezone implements Serializable {
  private String displayText;
  private String timezone;

  public Timezone() {}

  public Timezone(String timezone, String displayText) {
    this.setDisplayText(displayText);
    this.setTimezone(timezone);
  }

  public String getDisplayText() {
    return displayText;
  }

  public void setDisplayText(String displayText) {
    this.displayText = displayText;
  }

  public String getTimezone() {
    return timezone;
  }

  public void setTimezone(String timezone) {
    this.timezone = timezone;
  }
}
