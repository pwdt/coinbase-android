package com.coinbase.android;

import android.app.Activity;
import android.content.Intent;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.ActivityController;

import static org.fest.assertions.api.Assertions.*;
import static org.robolectric.Robolectric.*;

@Config(emulateSdk = 18, qualifiers = "v10", reportSdk = 10)
@RunWith(RobolectricTestRunner.class)
public class MainActivityTest {

    private final ActivityController<MainActivity> controller = buildActivity(MainActivity.class);

    @Before
    public void setUp() throws Exception {
      RobolectricMockResponses.setUp();
    }

    @Test
    public void shouldRedirectToLogin() throws Exception {
        Activity activity = controller.create().visible().start().resume().get();

        Intent expectedIntent = new Intent(activity, LoginActivity.class);
        assertThat(shadowOf(activity).getNextStartedActivity()).isEqualTo(expectedIntent);
    }

}
