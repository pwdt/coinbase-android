package com.coinbase.android;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import android.content.Intent;

import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.coinbase.android.pin.PINManager;
import com.coinbase.android.pin.PINPromptActivity;
import com.coinbase.api.LoginManager;

public class CoinbaseActivity extends SherlockFragmentActivity {

  /** This activity requires authentication */
  @Retention(RetentionPolicy.RUNTIME)
  public static @interface RequiresAuthentication { }

  /** This activity requires PIN entry (if PIN is enabled) */
  @Retention(RetentionPolicy.RUNTIME)
  public static @interface RequiresPIN { }

  @Override
  public void onResume() {

    super.onResume();

    if(getClass().isAnnotationPresent(RequiresAuthentication.class)) {
      // Check authentication status
      if(!LoginManager.getInstance().isSignedIn(this)) {

        // Not signed in.
        // First check if there are any accounts available to sign in to:
        boolean success = LoginManager.getInstance().switchActiveAccount(this, 0);

        if(!success) {
          // Not signed in - open login activity.
          Intent intent = new Intent(this, LoginActivity.class);
          intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
          startActivity(intent);

          finish();
        } else {
          // Now signed in, continue with Activity initialization
        }
      }
    }

    if(getClass().isAnnotationPresent(RequiresPIN.class)) {
      // Check PIN status
      if(!PINManager.getInstance().shouldGrantAccess(this)) {
        // Check if user wants to quit PIN lock
        if(PINManager.getInstance().isQuitPINLock()){
          PINManager.getInstance().setQuitPINLock(false);
          finish();
        } else {
          // PIN reprompt required.
          Intent intent = new Intent(this, PINPromptActivity.class);
          intent.setAction(PINPromptActivity.ACTION_PROMPT);
          startActivity(intent);
        }
      }
    }
  }
}
