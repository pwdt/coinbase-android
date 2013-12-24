package com.coinbase.android;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;

import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.MenuItem;

public class AboutActivity extends SherlockActivity {

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    
    setContentView(R.layout.activity_about);
    setTitle(String.format(getString(R.string.about_title), getString(R.string.app_name)));
    
    getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    getSupportActionBar().setDisplayShowHomeEnabled(false);
    getSupportActionBar().setBackgroundDrawable(new ColorDrawable(Color.rgb(52, 142, 218)));
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    
    if(item.getItemId() == android.R.id.home) {
      // Action bar up button
      finish();
    }
    
    return false;
  }

}
