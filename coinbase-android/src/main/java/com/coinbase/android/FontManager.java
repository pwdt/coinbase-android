package com.coinbase.android;

import java.util.HashMap;
import java.util.Map;

import android.content.Context;
import android.graphics.Typeface;

public class FontManager {
  
  private static Map<String, Typeface> mFonts = new HashMap<String, Typeface>();

  public static Typeface getFont(Context c, String name) {
    
    if(mFonts.containsKey(name)) {
      return mFonts.get(name);
    } else {
      mFonts.put(name, Typeface.createFromAsset(c.getAssets(), name + ".ttf"));
      return mFonts.get(name);
    }
  }

}
