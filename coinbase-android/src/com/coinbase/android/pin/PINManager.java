package com.coinbase.android.pin;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.preference.PreferenceManager;

import com.coinbase.android.Constants;


public class PINManager {

  public static final long PIN_REPROMPT_TIME = 5 * 1000; // Five seconds

  private static PINManager INSTANCE = null;

  public static PINManager getInstance() {

    if(INSTANCE == null) {
      INSTANCE = new PINManager();
    }

    return INSTANCE;
  }

  private PINManager() { }

  boolean bad = false;

  private static boolean isQuitPINLock = false;

  /**
   * Should the user be allowed to access protected content?
   * @param context
   * @return false if access is denied (and a PIN reprompt is required)
   */
  public boolean shouldGrantAccess(Context context) {

    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
    int activeAccount = prefs.getInt(Constants.KEY_ACTIVE_ACCOUNT, -1);

    // Does the user have a PIN?
    boolean hasPin = prefs.getString(String.format(Constants.KEY_ACCOUNT_PIN, activeAccount), null) != null;
    if(!hasPin) {
      return true;
    }

    // Is the PIN edit-only?
    boolean pinViewAllowed = prefs.getBoolean(String.format(Constants.KEY_ACCOUNT_PIN_VIEW_ALLOWED, activeAccount), false);
    if(pinViewAllowed) {
      return true;
    }

    // Is a reprompt required?
    long timeSinceReprompt = System.currentTimeMillis() - prefs.getLong(String.format(Constants.KEY_ACCOUNT_LAST_PIN_ENTRY_TIME, activeAccount), -1);
    return timeSinceReprompt < PIN_REPROMPT_TIME;
  }

  /**
   * Should the user be allowed to edit protected content? If not, PIN prompt will be started.
   * @param context
   * @return true if you should proceed with the edit
   */
  public boolean checkForEditAccess(Activity activity) {

    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
    int activeAccount = prefs.getInt(Constants.KEY_ACTIVE_ACCOUNT, -1);

    // Does the user have a PIN?
    boolean hasPin = prefs.getString(String.format(Constants.KEY_ACCOUNT_PIN, activeAccount), null) != null;
    if(!hasPin) {
      return true;
    }

    // Is the PIN edit-only?
    boolean pinViewAllowed = prefs.getBoolean(String.format(Constants.KEY_ACCOUNT_PIN_VIEW_ALLOWED, activeAccount), false);
    if(!pinViewAllowed) {
      return true;
    }

    // Is a reprompt required?
    long timeSinceReprompt = System.currentTimeMillis() - prefs.getLong(String.format(Constants.KEY_ACCOUNT_LAST_PIN_ENTRY_TIME, activeAccount), -1);
    boolean repromptRequired = timeSinceReprompt > PIN_REPROMPT_TIME;

    if(repromptRequired) {

      Intent intent = new Intent(activity, PINPromptActivity.class);
      intent.setAction(PINPromptActivity.ACTION_PROMPT);
      activity.startActivity(intent);
      return false;
    } else {
      return true;
    }
  }

  /**
   * Called after the user has entered the PIN successfully.
   */
  public void resetPinClock(Context context) {

    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
    int activeAccount = prefs.getInt(Constants.KEY_ACTIVE_ACCOUNT, -1);
    Editor e = prefs.edit();
    e.putLong(String.format(Constants.KEY_ACCOUNT_LAST_PIN_ENTRY_TIME, activeAccount), System.currentTimeMillis());
    e.commit();
  }

  /**
   * Set the user's PIN.
   */
  public void setPin(Context context, String pin) {

    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
    int activeAccount = prefs.getInt(Constants.KEY_ACTIVE_ACCOUNT, -1);
    Editor e = prefs.edit();
    e.putString(String.format(Constants.KEY_ACCOUNT_PIN, activeAccount), pin);
    e.commit();
  }

  /**
   * Set quitting PIN Lock.
   */
  public void setQuitPINLock(boolean quitPINLock) {
      isQuitPINLock = quitPINLock;
  }

  /**
   * Return whether user wants to quit PIN Lock.
   */
  public boolean isQuitPINLock() {
      return isQuitPINLock;
  }
}
