package com.coinbase.android.delayedtx;

import android.content.Context;
import android.util.Log;

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

  public DelayedTransaction(JSONObject txJson) {
    Log.v("Coinbase", txJson.toString());
    txJson = txJson.optJSONObject("delayed_transaction");
    this.type = Type.valueOf(txJson.optString("type"));
    this.amount = txJson.optString("amount");
    this.currency = txJson.optString("currency");
    this.otherUser = txJson.optString("otherUser");
    this.notes = txJson.optString("notes");
  }

  public String getCategory() {
    if (type == Type.BUY || type == Type.SELL) {
      return "transfer";
    } else if (type == Type.REQUEST) {
      return "request";
    } else {
      return "tx";
    }
  }

  public JSONObject createTransaction(Context c) throws JSONException {
    JSONObject tx = new JSONObject();
    tx.put("id", "delayed-" + System.currentTimeMillis());
    tx.put("created_at", ISO8601.now());
    tx.put("request", type == Type.REQUEST);
    tx.put("status", "delayed");
    tx.put("notes", notes);

    String amountWithSign = (type == Type.SELL || type == Type.SEND) ? ("-" + amount) : amount;
    tx.put("amount", new JSONObject().put("amount", amountWithSign).put("currency", currency));

    if (otherUser.startsWith("1") && !otherUser.contains("@")) {
      tx.put("recipient_address", otherUser);
    } else {
      tx.put("recipient", new JSONObject().put("email", otherUser).put("name", otherUser));
    }
    tx.put("sender", new JSONObject().put("id",
            Utils.getPrefsString(c, Constants.KEY_ACCOUNT_ID, null)));

    // delayed TX specific information
    JSONObject dtx = new JSONObject();
    dtx.put("type", type.toString());
    dtx.put("amount", amount);
    dtx.put("currency", currency);
    dtx.put("otherUser", otherUser);
    dtx.put("notes", notes);
    tx.put("delayed_transaction", dtx);

    return tx;
  }
}
