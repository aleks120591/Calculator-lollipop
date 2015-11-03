/*
* Copyright (C) 2014 The Android Open Source Project
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package com.android2.calculator3.view;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.view.MotionEventCompat;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewTreeObserver;

import com.android2.calculator3.Calculator;
import com.android2.calculator3.R;
import com.xlythe.floatingview.AnimationFinishedListener;

import io.codetail.widget.RevealFrameLayout;

public class CalculatorPadView extends RevealFrameLayout {
    private VelocityTracker mVelocityTracker;
    private int mTouchSlop;
    private int mMinimumFlingVelocity;
    private int mMaximumFlingVelocity;
    private float mInitialMotion;
    private float mLastMotion;
    private float mLastDelta;
    private float mOffset;
    private float mOverlayMargin;
    private boolean mInterceptingTouchEvents = false;
    private final DisplayAnimator mAnimator = new DisplayAnimator(0, 1f);
    private TranslateState mLastState = TranslateState.COLLAPSED;
    private TranslateState mState = TranslateState.COLLAPSED;

    private View mBase;
    private SolidLayout mOverlay;
    private FloatingActionButton mFab;
    private View mTray;

    private Animator mActiveAnimator;

    public CalculatorPadView(Context context) {
        super(context);
        setup();
    }

    public CalculatorPadView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setup();
    }

    public CalculatorPadView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setup();
    }

    public CalculatorPadView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        setup();
    }

    private void setup() {
        ViewConfiguration vc = ViewConfiguration.get(getContext());
        mMinimumFlingVelocity = vc.getScaledMinimumFlingVelocity();
        mMaximumFlingVelocity = vc.getScaledMaximumFlingVelocity();
        mTouchSlop = vc.getScaledTouchSlop();
        mOffset = getResources().getDimensionPixelSize(R.dimen.pad_page_margin);
        mOverlayMargin = getResources().getDimensionPixelSize(R.dimen.shadow_margin);
        getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                if (android.os.Build.VERSION.SDK_INT >= 16) {
                    getViewTreeObserver().removeOnGlobalLayoutListener(this);
                } else {
                    getViewTreeObserver().removeGlobalOnLayoutListener(this);
                }
                initializeLayout(getState());
            }
        });
    }

    public enum TranslateState {
        EXPANDED, COLLAPSED, PARTIAL
    }

    protected void onPageSelected(int position) {
    }

    public TranslateState getState() {
        return mState;
    }

    public boolean isExpanded() {
        return getState() == TranslateState.EXPANDED;
    }

    public boolean isCollapsed() {
        return getState() == TranslateState.COLLAPSED;
    }

    protected View getFab() {
        return mFab;
    }

    protected View getBase() {
        return mBase;
    }

    protected View getBaseOverlay() {
        return mOverlay;
    }

    protected View getTray() {
        return mTray;
    }

    /**
     * Sets up the height / position of the fab and tray
     *
     * Returns true if it requires a relayout
     * */
    protected boolean initializeLayout(TranslateState state) {
        boolean invalidate = false;

        int overlayWidth = getWidth() + (int) mOverlayMargin;
        if (mOverlay.getLayoutParams().width != overlayWidth) {
            mOverlay.getLayoutParams().width = overlayWidth;
            mOverlay.setLayoutParams(mOverlay.getLayoutParams());
            invalidate = true;
        }
        setEnabled(mOverlay, false);

        mFab.setTranslationX((mFab.getWidth() - getWidth() / 4) / 2);
        mFab.setTranslationY((mFab.getHeight() - getHeight() / 4) / 2);
        if (state == TranslateState.EXPANDED) {
            mOverlay.setTranslationX(-mOverlayMargin);
            mFab.setScaleX(1f);
            mFab.setScaleY(1f);
        } else {
            mOverlay.setTranslationX(getWidth() + mOffset - mOverlayMargin);
            mFab.setScaleX(0f);
            mFab.setScaleY(0f);
            mFab.setVisibility(View.GONE);
        }

        return invalidate;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mBase = findViewById(R.id.base);
        mOverlay = (SolidLayout) findViewById(R.id.overlay);
        mTray = findViewById(R.id.tray);
        mFab = (FloatingActionButton) findViewById(R.id.fab);

        mOverlay.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return true;
            }
        });
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        int action = MotionEventCompat.getActionMasked(ev);
        float pos = ev.getRawX();
        mInterceptingTouchEvents = false;

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                mInitialMotion = pos;
                mLastMotion = pos;
                handleDown();
                break;
            case MotionEvent.ACTION_MOVE:
                // Reset initial motion if the user drags in a different direction suddenly
                if ((pos - mInitialMotion) / Math.abs(pos - mInitialMotion) != (pos - mLastMotion) / Math.abs(pos - mLastMotion)) {
                    mInitialMotion = mLastMotion;
                }

                float delta = Math.abs(pos - mInitialMotion);
                if (delta > mTouchSlop) {
                    float dx = pos - mInitialMotion;
                    if (dx < 0) {
                        mInterceptingTouchEvents = getState() == TranslateState.COLLAPSED;
                    } else if (dx > 0) {
                        mInterceptingTouchEvents = getState() == TranslateState.EXPANDED;
                    }
                }
                mLastMotion = pos;
                break;
        }

        return mInterceptingTouchEvents;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!mInterceptingTouchEvents) {
            onInterceptTouchEvent(event);
            return true;
        }

        int action = MotionEventCompat.getActionMasked(event);

        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
        mVelocityTracker.addMovement(event);

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                // Handled in intercept
                break;
            case MotionEvent.ACTION_MOVE:
                handleMove(event);
                break;
            case MotionEvent.ACTION_UP:
                handleUp(event);
                break;
        }

        return true;
    }

    protected void handleDown() {
    }

    protected void handleMove(MotionEvent event) {
        float percent = getCurrentPercent();
        mAnimator.onUpdate(percent);
        mLastDelta = mLastMotion - event.getRawX();
        mLastMotion = event.getRawX();
        setState(TranslateState.PARTIAL);
        resetAnimator();
        setEnabled(mOverlay, true);
    }

    protected void handleUp(MotionEvent event) {
        mVelocityTracker.computeCurrentVelocity(1000, mMaximumFlingVelocity);
        if (Math.abs(mVelocityTracker.getXVelocity()) > mMinimumFlingVelocity) {
            // the sign on velocity seems unreliable, so use last delta to determine direction
            if (mLastDelta > 0) {
                expand();
            } else {
                collapse();
            }
        } else {
            if (mLastMotion > getWidth() / 2) {
                expand();
            } else {
                collapse();
            }
        }
        mVelocityTracker.recycle();
        mVelocityTracker = null;
    }

    private void resetAnimator() {
        if (mActiveAnimator != null) {
            mActiveAnimator.cancel();
            mActiveAnimator = null;
        }
    }

    public void expand() {
        expand(null);
    }

    public void expand(Animator.AnimatorListener listener) {
        resetAnimator();

        mActiveAnimator = new DisplayAnimator(getCurrentPercent(), 1f);
        if (listener != null) {
            mActiveAnimator.addListener(listener);
        }
        mActiveAnimator.addListener(new AnimationFinishedListener() {
            @Override
            public void onAnimationFinished() {
                showFab();
                setEnabled(mOverlay, true);
            }
        });
        mActiveAnimator.start();
        setState(TranslateState.EXPANDED);
    }

    public void collapse() {
        collapse(null);
    }

    public void collapse(Animator.AnimatorListener listener) {
        resetAnimator();

        mActiveAnimator = new DisplayAnimator(getCurrentPercent(), 0f);
        if (listener != null) {
            mActiveAnimator.addListener(listener);
        }
        mActiveAnimator.addListener(new AnimationFinishedListener() {
            @Override
            public void onAnimationFinished() {
                hideFab();
                setEnabled(mOverlay, false);
            }
        });
        mActiveAnimator.start();
        setState(TranslateState.COLLAPSED);
    }

    protected void showFab() {
        ((Calculator) getContext()).showFab();
    }

    protected void hideFab() {
        ((Calculator) getContext()).hideFab();
    }

    protected void showTray() {
        ((Calculator) getContext()).showTray();
    }

    protected void hideTray() {
        ((Calculator) getContext()).hideTray();
    }

    private void setState(TranslateState state) {
        if (mState != state) {
            mLastState = mState;
            mState = state;

            if (mState == TranslateState.EXPANDED) {
                onPageSelected(0);
            } else if (mState == TranslateState.COLLAPSED) {
                onPageSelected(1);
            }

            if (mState != TranslateState.EXPANDED) {
                hideFab();
                hideTray();
            }
        }
    }

    private void setEnabled(SolidLayout view, boolean enabled) {
        view.setPreventChildTouchEvents(!enabled);
        view.setPreventParentTouchEvents(!enabled);
    }

    protected float getCurrentPercent() {
        float percent = (mInitialMotion - mLastMotion) / getWidth();

        // Start at 100% if open
        if (mState == TranslateState.EXPANDED ||
                (mState == TranslateState.PARTIAL && mLastState == TranslateState.EXPANDED)) {
            percent += 1f;
        }
        percent = Math.min(Math.max(percent, 0f), 1f);
        return percent;
    }

    /**
     * An animator that goes from 0 to 100%
     **/
    private class DisplayAnimator extends ValueAnimator {
        public DisplayAnimator(float start, float end) {
            super();
            setFloatValues(start, end);
            addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    float percent = (float) animation.getAnimatedValue();
                    onUpdate(percent);
                }
            });
        }

        public void onUpdate(float percent) {
            // Update the drag animation
            View overlay = getChildAt(1);
            overlay.setTranslationX((getWidth() + mOffset) * (1 - percent) - mOverlayMargin);
        }

        float scale(float percent, float goal) {
            return 1f - percent * (1f - goal);
        }

        int mixColors(float percent, int initColor, int goalColor) {
            int a1 = Color.alpha(goalColor);
            int r1 = Color.red(goalColor);
            int g1 = Color.green(goalColor);
            int b1 = Color.blue(goalColor);

            int a2 = Color.alpha(initColor);
            int r2 = Color.red(initColor);
            int g2 = Color.green(initColor);
            int b2 = Color.blue(initColor);

            percent = Math.min(1, percent);
            percent = Math.max(0, percent);
            float a = a1 * percent + a2 * (1 - percent);
            float r = r1 * percent + r2 * (1 - percent);
            float g = g1 * percent + g2 * (1 - percent);
            float b = b1 * percent + b2 * (1 - percent);

            return Color.argb((int) a, (int) r, (int) g, (int) b);
        }
    }
}