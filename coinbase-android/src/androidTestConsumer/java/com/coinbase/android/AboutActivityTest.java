package com.coinbase.android;

import android.app.Activity;

import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@Config(emulateSdk = 18)
@RunWith(RobolectricTestRunner.class)
public class AboutActivityTest {
    @Test
    public void shouldHaveTitle() throws Exception {
        Activity activity = Robolectric.buildActivity(AboutActivity.class).create().get();
        assertEquals("About Coinbase", activity.getTitle());
    }
}
