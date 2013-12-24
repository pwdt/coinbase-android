package com.coinbase.android;

import android.util.Log;

/**
 * Created by isaac_000 on 23/12/13.
 */
public class Profiler {

  long startTime = -1;
  long lastSegmentTime = -1;

  public Profiler() {

    startTime = lastSegmentTime = System.currentTimeMillis();
  }

  public void segmentDone(String name) {
    long now = System.currentTimeMillis();
    Log.i("Coinbase", "Segment " + name + " done in " + (now - lastSegmentTime) + "ms (" + (now - startTime) + "ms total)");
    lastSegmentTime = now;
  }
}
