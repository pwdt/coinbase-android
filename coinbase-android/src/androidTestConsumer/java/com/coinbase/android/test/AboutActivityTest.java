package com.coinbase.android.test;

import android.app.Activity;
import android.test.ActivityInstrumentationTestCase2;
import android.test.AndroidTestCase;

import com.coinbase.android.AboutActivity;
import com.robotium.solo.Solo;

public class AboutActivityTest extends ActivityInstrumentationTestCase2<AboutActivity> {

  private Solo solo;

  public AboutActivityTest() {
    super(AboutActivity.class);
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
