package com.coinbase.android;

import android.util.Log;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;

import com.nineoldandroids.animation.Animator;
import com.nineoldandroids.animation.Animator.AnimatorListener;
import com.nineoldandroids.animation.AnimatorListenerAdapter;
import com.nineoldandroids.animation.ValueAnimator;
import com.nineoldandroids.view.ViewHelper;

import static com.nineoldandroids.view.ViewHelper.setAlpha;
import static com.nineoldandroids.view.ViewPropertyAnimator.animate;

/**
 * Created by isaac_000 on 10/11/13.
 * Based upon SwipeDismissTouchListener from https://github.com/nadavfima/cardsui-for-android
 */
public class BalanceTouchListener implements View.OnTouchListener {
  // Cached ViewConfiguration and system-wide constant values
  private int mSlop;
  private int mMinFlingVelocity;
  private int mMaxFlingVelocity;
  private long mAnimationTime;

  // Fixed properties
  private View mView;
  private OnDismissCallback mCallback;
  private int mViewWidth = 1; // 1 and not 0 to prevent dividing by zero

  // Transient properties
  private float mDownX;
  private boolean mSwiping;
  private Object mToken;
  private VelocityTracker mVelocityTracker;
  private float mTranslationX;


  public interface OnDismissCallback {

    void onDismiss(View view, Object token);
  }

  /**
   * Constructs a new swipe-to-dismiss touch listener for the given view.
   *
   * @param view
   *            The view to make dismissable.
   * @param token
   *            An optional token/cookie object to be passed through to the
   *            callback.
   * @param callback
   *            The callback to trigger when the user has indicated that she
   *            would like to dismiss this view.
   */
  public BalanceTouchListener(View view, Object token, OnDismissCallback callback) {
    ViewConfiguration vc = ViewConfiguration.get(view.getContext());
    mSlop = vc.getScaledTouchSlop() * 2;
    mMinFlingVelocity = vc.getScaledMinimumFlingVelocity();
    mMaxFlingVelocity = vc.getScaledMaximumFlingVelocity();
    mAnimationTime = view.getContext().getResources().getInteger(android.R.integer.config_shortAnimTime);
    mView = view;
    mToken = token;
    mCallback = callback;
  }

  @Override
  public boolean onTouch(View view, MotionEvent motionEvent) {
    // offset because the view is translated during swipe
    motionEvent.offsetLocation(mTranslationX, 0);

    if (mViewWidth < 2) {
      mViewWidth = mView.getWidth();
    }

    switch (motionEvent.getActionMasked()) {
      case MotionEvent.ACTION_DOWN: {
        // TODO: ensure this is a finger, and set a flag
        mDownX = motionEvent.getRawX();
        mVelocityTracker = VelocityTracker.obtain();
        mVelocityTracker.addMovement(motionEvent);
        view.onTouchEvent(motionEvent);
        return true;
      }

      case MotionEvent.ACTION_UP: {
        if (mVelocityTracker == null) {
          break;
        }

        float deltaX = motionEvent.getRawX() - mDownX;
        mVelocityTracker.addMovement(motionEvent);
        mVelocityTracker.computeCurrentVelocity(1000);
        float velocityX = Math.abs(mVelocityTracker.getXVelocity());
        float velocityY = Math.abs(mVelocityTracker.getYVelocity());
        boolean dismiss = false;
        boolean dismissRight = false;
        if (Math.abs(deltaX) > mViewWidth / 2) {
          dismiss = true;
          dismissRight = deltaX > 0;
        } else if (mMinFlingVelocity <= velocityX && velocityX <= mMaxFlingVelocity && velocityY < velocityX) {
          dismiss = true;
          dismissRight = mVelocityTracker.getXVelocity() > 0;
        }
        if (dismiss) {
          // dismiss
          animate(mView).translationX(dismissRight ? mViewWidth : -mViewWidth).alpha(0).setDuration(mAnimationTime).setListener(new AnimatorListener() {

            @Override
            public void onAnimationStart(Animator arg0) {
              // TODO Auto-generated method stub

            }

            @Override
            public void onAnimationRepeat(Animator arg0) {
              // TODO Auto-generated method stub

            }

            @Override
            public void onAnimationEnd(Animator arg0) {
              performDismiss();

            }

            @Override
            public void onAnimationCancel(Animator arg0) {
              // TODO Auto-generated method stub

            }
          });
        } else {
          // cancel
          animate(mView).translationX(0).alpha(1).setDuration(mAnimationTime).setListener(null);

        }
        mVelocityTracker = null;
        mTranslationX = 0;
        mDownX = 0;
        mSwiping = false;
        break;
      }

      case MotionEvent.ACTION_MOVE: {
        if (mVelocityTracker == null) {
          break;
        }

        mVelocityTracker.addMovement(motionEvent);
        float deltaX = motionEvent.getRawX() - mDownX;
        if (Math.abs(deltaX) > mSlop) {
          mSwiping = true;
          mView.getParent().requestDisallowInterceptTouchEvent(true);

          // Cancel listview's touch
          MotionEvent cancelEvent = MotionEvent.obtain(motionEvent);
          cancelEvent.setAction(MotionEvent.ACTION_CANCEL | (motionEvent.getActionIndex() << MotionEvent.ACTION_POINTER_INDEX_SHIFT));
          mView.onTouchEvent(cancelEvent);
        }

        if (mSwiping) {
          mTranslationX = deltaX;
          ViewHelper.setTranslationX(mView, deltaX);

          // TODO: use an ease-out interpolator or such
          setAlpha(mView, Math.max(0f, Math.min(1f, 1f - 2f * Math.abs(deltaX) / mViewWidth)));
          return true;
        }
        break;
      }
    }
    return false;
  }

  private void performDismiss() {
    mCallback.onDismiss(mView, mToken);
  }

  public void reset() {
    animate(mView).translationX(0).alpha(1).setDuration(mAnimationTime).setListener(null);
  }
}