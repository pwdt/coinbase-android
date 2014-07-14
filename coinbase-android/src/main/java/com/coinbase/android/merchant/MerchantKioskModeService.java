package com.coinbase.android.merchant;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.coinbase.android.MainActivity;
import com.coinbase.android.R;

import java.util.Timer;
import java.util.TimerTask;

public class MerchantKioskModeService extends Service {

  private static final int CHECK_PERIOD = 1000;

  private boolean mAlreadyStarted = false;
  private Timer mTimer = null;

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {

    if (!mAlreadyStarted) {
      mAlreadyStarted = true;

      Notification foregroundNotif = new NotificationCompat.Builder(this)
              .setContentTitle(getString(R.string.kiosk_notif_title))
              .setContentText(getString(R.string.kiosk_notif_text))
              .setSmallIcon(android.R.drawable.ic_dialog_alert)
              .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.drawable.ic_launcher))
              .build();
      startForeground(10, foregroundNotif);

      mTimer = new Timer();
      mTimer.schedule(new TimerTask() {
        @Override
        public void run() {

          ActivityManager am = (ActivityManager)getApplicationContext().getSystemService(Context.ACTIVITY_SERVICE);
          ComponentName cn = am.getRunningTasks(1).get(0).topActivity;

          boolean notCoinbase = !cn.getClassName().equals("com.coinbase.android.MainActivity") &&
                  !cn.getPackageName().equals("com.coinbase.android.merchant");
          boolean notResolver = !cn.getClassName().equals("com.android.internal.app.ResolverActivity") &&
                  !cn.getPackageName().equals("android");

          if (cn != null && notCoinbase && notResolver) {
            // Another app has been opened! Quick, close it!
            Log.i("Coinbase", "Currently opened app not permitted; re-opening Coinbase Merchant: " + cn);
            Intent main = new Intent(MerchantKioskModeService.this, MainActivity.class);
            main.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(main);
          }
        }
      }, CHECK_PERIOD, CHECK_PERIOD);
    }

    return START_STICKY;
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    mTimer.cancel();
    mTimer = null;
  }

  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }
}
