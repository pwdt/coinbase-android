package com.coinbase.android.delayedtx;

import android.content.Context;

import com.coinbase.android.Constants;
import com.coinbase.android.ISO8601;
import com.coinbase.android.Utils;

import org.json.JSONObject;
import org.json.JSONException;

/**
 * Transaction that has not yet been sent
 */
public class DelayedTransaction {

  public static enum Type {
    SEND,
    REQUEST,
    BUY,
    SELL;
  }

  public Type type;
  public String amount;
  public String currency;
  public String otherUser;
  public String notes;

  public DelayedTransaction(Type type, String amount, String currency, String otherUser, String notes) {
    this.type = type;
    this.amount = amount;
    this.currency = currency;
    this.otherUser = otherUser;
    this.notes = notes;
  }

  public JSONObject createTransaction(Context c) throws JSONException {
    JSONObject tx = new JSONObject();
    tx.put("id", "delayed-" + System.currentTimeMillis());
    tx.put("created_at", ISO8601.now());
    tx.put("request", false);
    tx.put("amount", new JSONObject().put("amount", amount).put("currency", currency));
    tx.put("status", "delayed");
    tx.put("notes", notes);

    if (otherUser.startsWith("1") && !otherUser.contains("@")) {
      tx.put("recipient_address", otherUser);
    } else {
      tx.put("recipient", new JSONObject().put("email", otherUser).put("name", otherUser));
    }
    tx.put("sender", new JSONObject().put("id",
            Utils.getPrefsString(c, Constants.KEY_ACCOUNT_ID, null)));

    return tx;
  }
}
