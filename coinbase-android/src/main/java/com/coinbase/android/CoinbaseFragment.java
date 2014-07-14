package com.coinbase.android;

public interface CoinbaseFragment {

  public void onSwitchedTo();
  public void onPINPromptSuccessfulReturn();
  public String getTitle();
}
