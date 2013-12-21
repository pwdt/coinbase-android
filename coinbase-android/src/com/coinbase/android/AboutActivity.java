package com.coinbase.android;

import android.os.Bundle;
import android.widget.TextView;

import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.MenuItem;

public class AboutActivity extends SherlockActivity {

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    
    setContentView(R.layout.activity_about);
    setTitle(String.format(getString(R.string.about_title), getString(R.string.app_name)));
    
    TextView contributorsView = (TextView) findViewById(R.id.about_contributors);
    String[] contributors = getResources().getStringArray(R.array.contributors);
    String contributorsLabel = getString(R.string.about_contributors);
    
    StringBuffer contributorsText = new StringBuffer();
    
    for(int i = 0; i < contributors.length; i++) {

      contributorsText.append(contributors[i]);
      contributorsText.append('\n');
    }
    
    contributorsView.setText(String.format(contributorsLabel, contributorsText.toString()));
    
    getSupportActionBar().setDisplayHomeAsUpEnabled(true);
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
