package com.coinbase.android.delayedtx;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.coinbase.android.CoinbaseApplication;
import com.coinbase.android.MainActivity;
import com.coinbase.android.R;
import com.coinbase.android.TransferFragment;
import com.coinbase.android.Utils;
import com.coinbase.android.db.DatabaseObject;
import com.coinbase.android.db.TransactionsDatabase;
import com.coinbase.api.RpcManager;

import org.apache.http.message.BasicNameValuePair;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public class DelayedTxSenderService extends Service {

  private static int NOTIF_ID = -1;

  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

  boolean threadRunning = false;
  public int onStartCommand (Intent intent, int flags, int startId) {
    if (!threadRunning) {
      new Thread(new Runnable() {
        public void run() {
          tryToSendAll();
          stopSelf();
        }
      }).start();
    }
    return START_NOT_STICKY;
  }

  private void tryToSendAll() {

    // Check database for delayed TX:
    DatabaseObject db = DatabaseObject.getInstance();
    List<JSONObject> txToSend = new ArrayList<JSONObject>();
    synchronized(db.databaseLock) {
      db.beginTransaction(this);
      Cursor c = db.query(this, TransactionsDatabase.TransactionEntry.TABLE_NAME,
              new String[] { TransactionsDatabase.TransactionEntry.COLUMN_NAME_JSON, TransactionsDatabase.TransactionEntry.COLUMN_NAME_ACCOUNT},
              TransactionsDatabase.TransactionEntry.COLUMN_NAME_STATUS + " = ?", new String[] { "delayed" }, null, null, null);
      while (c.moveToNext()) {
        try {
          JSONObject tx = new JSONObject(c.getString(c.getColumnIndex(TransactionsDatabase.TransactionEntry.COLUMN_NAME_JSON)));
          tx.put("account", c.getInt(c.getColumnIndex(TransactionsDatabase.TransactionEntry.COLUMN_NAME_ACCOUNT)));
          txToSend.add(tx);
        } catch (JSONException e) {
          continue;
        }
      }
      db.endTransaction(this);
    }

    // Attempt to send the delayed TX:
    Log.i("Coinbase", "Sending " + txToSend.size() + " delayed TX now...");
    int successfullySent = 0;
    for (JSONObject txJson : txToSend) {

      DelayedTransaction tx = new DelayedTransaction(txJson);
      try {
        if ((tx.type == DelayedTransaction.Type.SEND) || (tx.type == DelayedTransaction.Type.REQUEST)) {

          // Make request
          int account = txJson.getInt("account");
          List<BasicNameValuePair> params = new ArrayList<BasicNameValuePair>();
          params.add(new BasicNameValuePair("transaction[amount_string]", tx.amount));
          params.add(new BasicNameValuePair("transaction[amount_currency_iso]", tx.currency));

          if(tx.notes != null && !"".equals(tx.notes)) {
            params.add(new BasicNameValuePair("transaction[notes]", tx.notes));
          }

          params.add(new BasicNameValuePair(
                  String.format("transaction[%s]", tx.type == DelayedTransaction.Type.SEND ? "to" : "from"), tx.otherUser));

          JSONObject response = RpcManager.getInstance().callPostOverrideAccount(this,
                  String.format("transactions/%s_money", tx.type.toString().toLowerCase(Locale.CANADA)), params, account);

          // Request was successfully sent! (Actual send/request may not have been successful, but that's not important.)
          successfullySent++;
          showNotification(response, tx);
          Log.i("Coinbase", "Successfully sent delayed TX!");
          deleteTransaction(txJson.optString("transaction_id"));

          // Insert new TX into database
          if (response.optBoolean("success")) {
            JSONObject transaction = response.getJSONObject("transaction");
            Utils.insertTransaction(this, transaction,
                    Utils.createAccountChangeForTransaction(this, transaction, tx.getCategory()),
                    transaction.getString("status"), account);
          }
        } else {
          // Unimplemented
          throw new RuntimeException("Delayed buy/sell has not yet been implemented.");
        }
      } catch (IOException e) {
        e.printStackTrace();
        // Don't increment successfullySent - we will try again later.
      } catch (JSONException e) {
        e.printStackTrace();
        // Don't increment successfullySent - we will try again later.
      }
    }

    // Notify MainActivity so it can reload the transactions list.
    ((CoinbaseApplication) getApplication()).onDbChange();

    // Disable the broadcast receiver if all transactions were successfully sent.
    if (successfullySent == txToSend.size()) {
      PackageManager pm = getPackageManager();
      ComponentName br = new ComponentName(this, ConnectivityChangeReceiver.class);
      pm.setComponentEnabledSetting(br, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
    }
  }

  // Delete transaction from the database.
  private void deleteTransaction(String id) {
    DatabaseObject db = DatabaseObject.getInstance();
    synchronized(db.databaseLock) {
      int deleted = db.delete(this, TransactionsDatabase.TransactionEntry.TABLE_NAME, TransactionsDatabase.TransactionEntry._ID + " = ?",
              new String[] { id });
    }
  }

  private void showNotification(JSONObject result, DelayedTransaction tx) {

    String title, description;
    if (result.optBoolean("success")) {
      if (tx.type == DelayedTransaction.Type.REQUEST) {
        title = getString(R.string.delayed_notification_success_request);
      } else {
        title = getString(R.string.delayed_notification_success_send);
      }
      description = getString(R.string.delayed_notification_success_subtitle);
    } else {
      if (tx.type == DelayedTransaction.Type.REQUEST) {
        title = getString(R.string.delayed_notification_failure_request);
      } else {
        title = getString(R.string.delayed_notification_failure_send);
      }

      if (result.has("errors")) {
        StringBuilder b = new StringBuilder();
        JSONArray errors = result.optJSONArray("errors");
        for (int i = 0; i < errors.length(); i++) {
          b.append(errors.optString(i)).append(", ");
        }
        b.setLength(b.length() - 2);
        description = b.toString();
      } else if (result.has("error")) {
        description = result.optString("error");
      } else {
        description = null;
      }
    }
    title = String.format(title, tx.otherUser, tx.amount, tx.currency);

    NotificationCompat.Builder mBuilder =
        new NotificationCompat.Builder(this)
            .setSmallIcon(R.drawable.ic_notif_delayed)
            .setContentTitle(title)
            .setContentText(description)
            .setAutoCancel(true);
    Intent resultIntent = new Intent(this, MainActivity.class);
    PendingIntent resultPendingIntent = PendingIntent.getActivity(this, 0, resultIntent, 0);
    mBuilder.setContentIntent(resultPendingIntent);
    NotificationManager mNotificationManager =
            (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
    if (NOTIF_ID == -1) {
      NOTIF_ID = new Random().nextInt();
    }
    mNotificationManager.notify(NOTIF_ID++, mBuilder.build());
  }
}
