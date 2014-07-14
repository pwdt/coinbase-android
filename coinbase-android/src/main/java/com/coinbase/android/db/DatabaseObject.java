package com.coinbase.android.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

/**
 * Proxy all database calls through this class so that only one instance of
 * SQLiteDatabase is edited by any thread at any time.
 *
 * WARNING: Calling any method on this object on the UI thread could hang
 * the app for a few seconds if another thread (like, for example, the
 * transaction sync thread) is busy.
 */
public class DatabaseObject {

  private static DatabaseObject INSTANCE = null;

  public static DatabaseObject getInstance() {

    if (INSTANCE == null) {
      INSTANCE = new DatabaseObject();
    }

    return INSTANCE;
  }

  private DatabaseObject() {

  }

  /**
   * Lock on this object if you are doing something to the database that can not be
   * interrupted by other threads. BE CAREFUL not to do something else, like network
   * access, while locked on this object. Do not lock on main thread!
   */
  public final Object databaseLock = new Object();

  private SQLiteDatabase database = null;

  private void checkDb(Context context) {

    if (database == null) {
      Log.w("Coinbase", "Creating a database object - this should only happen once");
      database = new TransactionsDatabase(context).getWritableDatabase();
    }
  }

  public Cursor query(Context context, String table, String[] columns, String selection,
      String[] selectionArgs, String groupBy, String having, String orderBy) {

    synchronized (databaseLock) {

      checkDb(context);

      return database.query(table, columns, selection, selectionArgs, groupBy, having, orderBy);
    }
  }

  public Cursor query(Context context, String table, String[] columns, String selection,
      String[] selectionArgs, String groupBy, String having, String orderBy, String limit) {

    synchronized (databaseLock) {

      checkDb(context);

      return database.query(table, columns, selection, selectionArgs, groupBy, having, orderBy,
          limit);
    }
  }

  /**
   * If you are doing a transaction, remember to lock databaseLock for the entire duration of the transaction
   * or other threads will contribute to your transaction!
   * @param context
   */
  public void beginTransaction(Context context) {

    synchronized (databaseLock) {

      checkDb(context);

      database.beginTransaction();
    }
  }

  public void endTransaction(Context context) {

    synchronized (databaseLock) {

      checkDb(context);

      database.endTransaction();
    }
  }

  public void setTransactionSuccessful(Context context) {

    synchronized (databaseLock) {

      checkDb(context);

      database.setTransactionSuccessful();
    }
  }

  public long insertWithOnConflict(Context context, String table, String nullColumnHack,
                                   ContentValues initialValues, int conflictAlgorithm) {

    synchronized (databaseLock) {

      checkDb(context);

      return database.insertWithOnConflict(table, nullColumnHack, initialValues, conflictAlgorithm);
    }
  }

  public int delete(Context context, String table, String whereClause, String[] whereArgs) {

    synchronized (databaseLock) {

      checkDb(context);

      return database.delete(table, whereClause, whereArgs);
    }
  }

  public long insert(Context context, String table, String nullColumnHack, ContentValues values) {

    synchronized (databaseLock) {

      checkDb(context);

      return database.insert(table, nullColumnHack, values);
    }
  }
}
