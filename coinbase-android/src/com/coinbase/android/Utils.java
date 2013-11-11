package com.coinbase.android;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentManager;
import android.support.v4.widget.SimpleCursorAdapter;
import android.text.Html;
import android.widget.FilterQueryProvider;

import com.coinbase.android.db.DatabaseObject;
import com.coinbase.android.db.TransactionsDatabase.EmailEntry;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.NumberFormat;
import java.util.Hashtable;
import java.util.Locale;

public class Utils {

  private Utils() { }

  public static final void showMessageDialog(FragmentManager m, String message) {

    MessageDialogFragment fragment = new MessageDialogFragment();
    Bundle args = new Bundle();
    args.putString(MessageDialogFragment.ARG_MESSAGE, message);
    fragment.setArguments(args);

    try {
      fragment.show(m, "Utils.showMessageDialog");
    } catch(IllegalStateException e) {
      // Expected if application has been destroyed
      // Ignore
    }
  }

  public static enum CurrencyType {
    BTC(4, 2),
    TRADITIONAL(2, 2);


    int maximumFractionDigits;
    int minimumFractionDigits;

    CurrencyType(int max, int min) {
      maximumFractionDigits = max;
      minimumFractionDigits = min;
    }
  }

  public static final String formatCurrencyAmount(String amount) {
    return formatCurrencyAmount(new BigDecimal(amount), false, CurrencyType.BTC);
  }

  public static final String formatCurrencyAmount(BigDecimal amount) {
    return formatCurrencyAmount(amount, false, CurrencyType.BTC);
  }

  public static final String formatCurrencyAmount(String amount, boolean ignoreSign) {
    return formatCurrencyAmount(new BigDecimal(amount), ignoreSign, CurrencyType.BTC);
  }

  public static final String formatCurrencyAmount(BigDecimal balanceNumber, boolean ignoreSign, CurrencyType type) {

    Locale locale = Locale.getDefault();
    NumberFormat nf = NumberFormat.getInstance(locale);
    nf.setMaximumFractionDigits(type.maximumFractionDigits);
    nf.setMinimumFractionDigits(type.minimumFractionDigits);

    if(ignoreSign && balanceNumber.compareTo(BigDecimal.ZERO) == -1) {
      balanceNumber = balanceNumber.multiply(new BigDecimal(-1));
    }

    return nf.format(balanceNumber);
  }

  /** Based off of ZXing Android client code */
  public static Bitmap createBarcode(String contents, BarcodeFormat format,
                                     int desiredWidth, int desiredHeight) throws WriterException {

    Hashtable<EncodeHintType,Object> hints = new Hashtable<EncodeHintType,Object>(2);
    MultiFormatWriter writer = new MultiFormatWriter();
    BitMatrix result = writer.encode(contents, format, desiredWidth, desiredHeight, hints);

    int width = result.getWidth();
    int height = result.getHeight();
    int fgColor = 0xFF000000;
    int bgColor = 0x00FFFFFF;
    int[] pixels = new int[width * height];

    for (int y = 0; y < height; y++) {
      int offset = y * width;
      for (int x = 0; x < width; x++) {
        pixels[offset + x] = result.get(x, y) ? fgColor : bgColor;
      }
    }

    Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
    return bitmap;
  }

  /** Important note: a call to disposeOfEmailAutocompleteAdapter must be made when you are done with the Adapter */
  public static SimpleCursorAdapter getEmailAutocompleteAdapter(final Context context) {

    String[] from = { EmailEntry.COLUMN_NAME_EMAIL };
    int[] to = { android.R.id.text1 };
    final SimpleCursorAdapter adapter = new SimpleCursorAdapter(context, android.R.layout.simple_spinner_dropdown_item, null,
      from, to, 0);

    adapter.setCursorToStringConverter(new SimpleCursorAdapter.CursorToStringConverter() {
      @Override
      public CharSequence convertToString(Cursor cursor) {
        int colIndex = cursor.getColumnIndexOrThrow(EmailEntry.COLUMN_NAME_EMAIL);
        return cursor.getString(colIndex);
      }
    });

    adapter.setFilterQueryProvider(new FilterQueryProvider() {
      @Override
      public Cursor runQuery(CharSequence description) {

        if(description == null) {
          description = "";
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        int activeAccount = prefs.getInt(Constants.KEY_ACTIVE_ACCOUNT, -1);

        // This method (runQuery) is called on a background thread
        // so it is OK to use DatabaseObject
        Cursor c = DatabaseObject.getInstance().query(context, EmailEntry.TABLE_NAME,
          null, EmailEntry.COLUMN_NAME_ACCOUNT + " = ? AND " + EmailEntry.COLUMN_NAME_EMAIL + " LIKE ?",
          new String[] { Integer.toString(activeAccount), "%" + description + "%" }, null, null, null);

        return c;
      }
    });

    return adapter;
  }

  public static void disposeOfEmailAutocompleteAdapter(SimpleCursorAdapter autocompleteAdapter) {

    // No longer needed with new DatabaseObject
  }

  public static CharSequence generateTransactionSummary(Context c, JSONObject t) throws JSONException {

    String category = t.getJSONObject("cache").getString("category");
    String otherName = t.getJSONObject("cache").getJSONObject("other_user").getString("name");
    boolean senderMe = t.getJSONObject("amount").getString("amount").startsWith("-");

    otherName = otherName.replace(" ", "\u00A0");

    String html = null;
    if("request".equals(category)) {
      if(senderMe) {
        html = String.format("<b>%1$s</b> requested money from you", otherName);
      } else {
        html = String.format("You requested money from <b>%1$s</b>", otherName);
      }
    } else if("invoice".equals(category)) {
      if(senderMe) {
        html = String.format("<b>%1$s</b> sent you an invoice", otherName);
      } else {
        html = String.format("You sent an invoice to <b>%1$s</b>", otherName);
      }
    } else if("transfer".equals(category)) {
      if(senderMe) {
        html = "You sold bitcoins";
      } else {
        html = "You purchased bitcoins";
      }
    } else {
      if(senderMe) {
        html = String.format("You sent money to <b>%1$s</b>", otherName);
      } else {
        html = String.format("<b>%1$s</b> sent you money", otherName);
      }
    }

    return Html.fromHtml(html);
  }

  public static String getErrorStringFromJson(JSONObject response, String delimiter) throws JSONException {


    JSONArray errors = response.getJSONArray("errors");
    String errorMessage = "";

    for(int i = 0; i < errors.length(); i++) {
      errorMessage += (errorMessage.equals("") ? "" : delimiter) + errors.getString(i);
    }
    return errorMessage;
  }

  @TargetApi(Build.VERSION_CODES.HONEYCOMB)
  public static <T> void runAsyncTaskConcurrently(AsyncTask<T, ?, ?> task, T... params) {

    if (Build.VERSION.SDK_INT>=Build.VERSION_CODES.HONEYCOMB) {
      task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, params);
    } else {
      task.execute(params);
    }
  }

  public static String md5(String original) {
    MessageDigest md;

    try {
      md = MessageDigest.getInstance("MD5");
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("MD5 does not exist", e);
    }

    md.update(original.getBytes());
    byte[] digest = md.digest();
    StringBuffer sb = new StringBuffer();
    for (byte b : digest) {
      sb.append(Integer.toHexString((int) (b & 0xff)));
    }
    return sb.toString();
  }

  public static int getActiveAccount(Context c) {

    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(c);
    int activeAccount = prefs.getInt(Constants.KEY_ACTIVE_ACCOUNT, -1);
    return activeAccount;
  }

  public static String getPrefsString(Context c, String key, String def) {

    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(c);
    int activeAccount = prefs.getInt(Constants.KEY_ACTIVE_ACCOUNT, -1);
    return prefs.getString(String.format(key, activeAccount), def);
  }

  public static boolean getPrefsBool(Context c, String key, boolean def) {

    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(c);
    int activeAccount = prefs.getInt(Constants.KEY_ACTIVE_ACCOUNT, -1);
    return prefs.getBoolean(String.format(key, activeAccount), def);
  }
}
