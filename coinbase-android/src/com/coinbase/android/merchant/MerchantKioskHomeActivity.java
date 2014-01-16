package com.coinbase.android.merchant;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import com.coinbase.android.MainActivity;

public class MerchantKioskHomeActivity extends Activity {

  @Override
  public void onCreate(Bundle s) {
    super.onCreate(s);

    // Just start the main activity
    startActivity(new Intent(this, MainActivity.class));
    finish();
  }
}
