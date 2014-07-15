package com.coinbase.android.test;

import android.test.ActivityInstrumentationTestCase2;

import com.coinbase.android.AboutActivity;
import com.coinbase.android.MainActivity;
import com.robotium.solo.Solo;

public class MainActivityTest extends ActivityInstrumentationTestCase2<MainActivity> {

  private Solo solo;

  public MainActivityTest() {
    super(MainActivity.class);
  }

  public void setUp() throws Exception {
    solo = new Solo(getInstrumentation(), getActivity());
  }

  @Override
  public void tearDown() throws Exception {
    solo.finishOpenedActivities();
  }

  public void testCorrectTitle() throws Exception {
    solo.assertCurrentActivity("wrong activity", AboutActivity.class);
    assertEquals("About Coinbase", solo.getCurrentActivity().getTitle());
  }

}
