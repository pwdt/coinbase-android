package com.coinbase.android;

import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.support.v4.app.FragmentTransaction;

import roboguice.activity.RoboFragmentActivity;

public class TestFragmentActivity<T extends Fragment> extends RoboFragmentActivity {
  protected T testFragment;
  protected Class<T> clazz;

  public TestFragmentActivity(Class<T> clazz) {
   this.clazz = clazz;
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.empty_test);

    FragmentTransaction fragmentTransaction = getSupportFragmentManager().beginTransaction();
    try {
      testFragment = clazz.newInstance();
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
    fragmentTransaction.add(R.id.empty_test_flipper, testFragment);
    fragmentTransaction.commit();
  }
}
