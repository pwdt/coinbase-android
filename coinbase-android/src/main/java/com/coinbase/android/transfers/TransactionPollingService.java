package com.coinbase.android.transfers;

import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import com.coinbase.android.GetLatestTransactionsTask;
import com.coinbase.android.event.NewTransactionEvent;
import com.coinbase.api.entity.Transaction;
import com.google.inject.Inject;
import com.squareup.otto.Bus;

import org.joda.time.DateTime;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import roboguice.service.RoboService;

public class TransactionPollingService extends RoboService {
  private static final int POLLING_FREQUENCY_MILLIS = 2000;
  private static final int NOW = 0;

  private class PollingTask extends GetLatestTransactionsTask {
    public PollingTask() {
      super(TransactionPollingService.this);
    }

    @Override
    public void onSuccess(List<Transaction> transactions) {
      Log.d("PollingTask", "Got transactions with size " + transactions.size());
      for (Transaction transaction : transactions) {
        if (transaction.getCreatedAt().isAfter(mLastTransactionTime)) {
          Log.d("PollingTask", "Got new transaction");
          mBus.post(new NewTransactionEvent(transaction));
        }
      }

      if (transactions.size() > 0) {
        mLastTransactionTime = transactions.get(0).getCreatedAt();
      }
    }

    @Override
    public void onFinally() {
      mPollingTask = null;
    }
  }

  protected volatile Timer mPollingTimer;
  protected volatile PollingTask mPollingTask;
  protected volatile DateTime mLastTransactionTime;

  @Inject
  protected Bus mBus;

  public TransactionPollingService() {}

  @Override
  public void onCreate() {
    super.onCreate();

    mLastTransactionTime = DateTime.now();

    mPollingTimer = new Timer();

    mPollingTimer.schedule(new TimerTask() {
      @Override
      public void run() {
        if (mPollingTask == null) {
          Log.d("PollingService", "Dispatching polling task");
          mPollingTask = new PollingTask();
          mPollingTask.execute();
        }
      }
    }, NOW, POLLING_FREQUENCY_MILLIS);
  }

  @Override
  public IBinder onBind(Intent intent) {
    return new Binder();
  }

  @Override
  public void onDestroy() {
    mPollingTimer.cancel();
    if (mPollingTask != null) {
      mPollingTask.cancel(true);
    }
    super.onDestroy();
  }
}
