package com.coinbase.android.ui;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/*
* http://www.curious-creature.org/2012/12/11/android-recipe-1-image-with-rounded-corners/
*/
public class AvatarDrawable extends Drawable {

  private final RectF mRect = new RectF();
  private final Bitmap mOriginalBitmap;
  private final Paint mPaint;

  AvatarDrawable(Bitmap bitmap) {

    mOriginalBitmap = bitmap;

    mPaint = new Paint();
    mPaint.setAntiAlias(true);

  }

  @Override
  protected void onBoundsChange(Rect bounds) {
    super.onBoundsChange(bounds);

    BitmapShader shader = new BitmapShader(Bitmap.createScaledBitmap(mOriginalBitmap, bounds.width(), bounds.height(), true),
            Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
    mPaint.setShader(shader);

    mRect.set(0, 0, bounds.width(), bounds.height());
  }

  @Override
  public void draw(Canvas canvas) {
    canvas.drawRoundRect(mRect, mRect.width() / 2, mRect.height() / 2, mPaint);
  }

  @Override
  public int getOpacity() {
    return PixelFormat.TRANSLUCENT;
  }

  @Override
  public void setAlpha(int alpha) {
    mPaint.setAlpha(alpha);
  }

  @Override
  public void setColorFilter(ColorFilter cf) {
    mPaint.setColorFilter(cf);
  }
}