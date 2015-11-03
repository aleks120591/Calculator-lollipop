package com.android2.calculator3.view;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.v4.view.MotionEventCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.android2.calculator3.HistoryAdapter;
import com.android2.calculator3.R;
import com.xlythe.floatingview.AnimationFinishedListener;

/**
 * The display overlay is a container that intercepts touch events on top of:
 *      1. the display, i.e. the formula and result views
 *      2. the history view, which is revealed by dragging down on the display
 *
 * This overlay passes vertical scrolling events down to the history recycler view
 * when applicable.  If the user attempts to scroll up and the recycler is already
 * scrolled all the way up, then we intercept the event and collapse the history.
 */
public class DisplayOverlay extends RelativeLayout {

    /**
     * Alpha when history is pulled down
     * */
    private static final float MAX_ALPHA = 0.6f;

    private static boolean DEBUG = false;
    private static final String TAG = DisplayOverlay.class.getSimpleName();

    private VelocityTracker mVelocityTracker;
    private int mMinimumFlingVelocity;
    private int mMaximumFlingVelocity;
    private RecyclerView mRecyclerView;
    private View mMainDisplay;
    private View mDisplayBackground;
    private View mDisplayForeground;
    private GraphView mDisplayGraph;
    private CalculatorEditText mFormulaEditText;
    private CalculatorEditText mResultEditText;
    private View mCalculationsDisplay;
    private View mInfoText;
    private LinearLayoutManager mLayoutManager;
    private int mDisplayHeight;
    private int mTouchSlop;
    private float mInitialMotionY;
    private float mLastMotionY;
    private float mLastDeltaY;
    private TranslateState mLastState = TranslateState.COLLAPSED;
    private TranslateState mState = TranslateState.COLLAPSED;
    private int mMinTranslation = -1;
    private int mMaxTranslation = -1;
    private float mMaxDisplayScale = 1f;
    private int mFormulaInitColor = -1;
    private int mResultInitColor = -1;
    private View mFade;
    private final OnTouchListener mFadeOnTouchListener = new OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            collapse();
            return true;
        }
    };
    private final DisplayAnimator mAnimator = new DisplayAnimator(0, 1f);
    private View mTemplateDisplay;

    public DisplayOverlay(Context context) {
        super(context);
        setup();
    }

    public DisplayOverlay(Context context, AttributeSet attrs) {
        super(context, attrs);
        setup();
    }

    public DisplayOverlay(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setup();
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public DisplayOverlay(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        setup();
    }

    private void setup() {
        ViewConfiguration vc = ViewConfiguration.get(getContext());
        mMinimumFlingVelocity = vc.getScaledMinimumFlingVelocity();
        mMaximumFlingVelocity = vc.getScaledMaximumFlingVelocity();
        mTouchSlop = vc.getScaledTouchSlop();
        getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                if (android.os.Build.VERSION.SDK_INT >= 16) {
                    getViewTreeObserver().removeOnGlobalLayoutListener(this);
                } else {
                    getViewTreeObserver().removeGlobalOnLayoutListener(this);
                }
                evaluateHeight();
                setTranslationY(mMinTranslation);
                mRecyclerView.setTranslationY(-mDisplayHeight);
                mDisplayGraph.setTranslationY(-mMinTranslation);
                if (DEBUG) {
                    Log.v(TAG, String.format("mMinTranslation=%s, mMaxTranslation=%s", mMinTranslation, mMaxTranslation));
                }
                scrollToMostRecent();
            }
        });
    }

    public enum TranslateState {
        EXPANDED, COLLAPSED, PARTIAL, GRAPH_EXPANDED, MINI_GRAPH
    }

    private TranslateState getTranslateState() {
        return mState;
    }

    private void setState(TranslateState state) {
        if (mState != state) {
            mLastState = mState;
            mState = state;
        }
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mRecyclerView = (RecyclerView) findViewById(R.id.historyRecycler);
        mLayoutManager = new LinearLayoutManager(getContext());
        mLayoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        mLayoutManager.setStackFromEnd(true);
        mRecyclerView.setLayoutManager(mLayoutManager);

        mMainDisplay = findViewById(R.id.main_display);
        mDisplayBackground = findViewById(R.id.the_card);
        mDisplayForeground = findViewById(R.id.the_clear_animation);
        mDisplayGraph = (GraphView) findViewById(R.id.mini_graph);
        mFormulaEditText = (CalculatorEditText) findViewById(R.id.formula);
        mResultEditText = (CalculatorEditText) findViewById(R.id.result);
        mCalculationsDisplay = findViewById(R.id.calculations);
        mInfoText = findViewById(R.id.info);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (getTranslateState() == TranslateState.GRAPH_EXPANDED
                || getTranslateState() == TranslateState.MINI_GRAPH) {
            // Disable history when showing graph
            return super.onInterceptTouchEvent(ev);
        }

        int action = MotionEventCompat.getActionMasked(ev);
        float y = ev.getRawY();
        boolean intercepted = false;
        TranslateState state = getTranslateState();

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                mInitialMotionY = y;
                mLastMotionY = y;
                handleDown();
                break;
            case MotionEvent.ACTION_MOVE:
                float delta = Math.abs(y - mInitialMotionY);
                if (delta > mTouchSlop) {
                    float dy = y - mInitialMotionY;
                    if (dy < 0) {
                        intercepted = isScrolledToEnd() && state != TranslateState.COLLAPSED;
                    } else if (dy > 0) {
                        intercepted = state != TranslateState.EXPANDED;
                    }
                }
                break;
        }

        return intercepted;
    }

    private boolean isScrolledToEnd() {
        return mLayoutManager.findLastCompletelyVisibleItemPosition() ==
                mRecyclerView.getAdapter().getItemCount() - 1;
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
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

    private void handleDown() {
        evaluateHeight();

        if (getRange() == 0) {
            return;
        }

        if (isCollapsed()) {
            mFormulaInitColor = mFormulaEditText.getCurrentTextColor();
            mResultInitColor = mResultEditText.getCurrentTextColor();

            mDisplayBackground.setPivotX(mDisplayBackground.getWidth() / 2);
            mDisplayBackground.setPivotY(mDisplayBackground.getHeight());

            mFormulaEditText.setPivotX(0);
            mFormulaEditText.setPivotY(0);

            mResultEditText.setPivotX(mResultEditText.getWidth());
            mResultEditText.setPivotY(0);

            scrollToMostRecent();
        }
    }

    private void handleMove(MotionEvent event) {
        float percent = getCurrentPercent();
        mAnimator.onUpdate(percent);
        mLastDeltaY = mLastMotionY - event.getRawY();
        mLastMotionY = event.getRawY();
        setState(TranslateState.PARTIAL);
    }

    private void handleUp(MotionEvent event) {
        if (getRange() == 0) {
            return;
        }

        mVelocityTracker.computeCurrentVelocity(1000, mMaximumFlingVelocity);
        if (Math.abs(mVelocityTracker.getYVelocity()) > mMinimumFlingVelocity) {
            // the sign on velocity seems unreliable, so use last delta to determine direction
            if (mLastDeltaY < 0) {
                expand();
            } else {
                collapse();
            }
        } else {
            if (getCurrentPercent() > 0.5f) {
                expand();
            } else {
                collapse();
            }
        }
        mVelocityTracker.recycle();
        mVelocityTracker = null;
    }

    private void ensureTemplateViewExists() {
        final String formula = mFormulaEditText.getText().toString();
        final String result = mResultEditText.getText().toString();

        if (mTemplateDisplay != null) {
            final String currentFormula = ((TemplateHolder) mTemplateDisplay.getTag()).formula.getText().toString();
            final String currentResult = ((TemplateHolder) mTemplateDisplay.getTag()).result.getText().toString();
            if (currentFormula.equals(formula) && currentResult.equals(result)) {
                return;
            }
        }

        mTemplateDisplay = getAdapter().parseView(mRecyclerView, formula, result);
        int leftMargin = ((MarginLayoutParams) mTemplateDisplay.getLayoutParams()).leftMargin;
        int topMargin = ((MarginLayoutParams) mTemplateDisplay.getLayoutParams()).topMargin;
        int rightMargin = ((MarginLayoutParams) mTemplateDisplay.getLayoutParams()).rightMargin;
        mTemplateDisplay.measure(MeasureSpec.makeMeasureSpec(getWidth() - leftMargin - rightMargin, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(Integer.MAX_VALUE / 2, MeasureSpec.AT_MOST));
        mTemplateDisplay.layout(leftMargin, topMargin, mTemplateDisplay.getMeasuredWidth() + leftMargin, mTemplateDisplay.getMeasuredHeight() + topMargin);

        // Cache all the children (so we don't keep calling findViewById
        TemplateHolder holder = new TemplateHolder();
        holder.formula = (TextView) mTemplateDisplay.findViewById(R.id.historyExpr);
        holder.result = (TextView) mTemplateDisplay.findViewById(R.id.historyResult);
        holder.historyLine = mTemplateDisplay.findViewById(R.id.history_line);
        holder.graph = mTemplateDisplay.findViewById(R.id.graph);
        mTemplateDisplay.setTag(holder);
        if (DEBUG) {
            Log.d(TAG, String.format("l=%s,t=%s,r=%s,b=%s,width=%s,height=%s", mTemplateDisplay.getLeft(), mTemplateDisplay.getTop(), mTemplateDisplay.getRight(), mTemplateDisplay.getBottom(), mTemplateDisplay.getWidth(), mTemplateDisplay.getHeight()));
        }
    }

    private float getCurrentPercent() {
        int maxDistance = mMaxTranslation == 0 ? getHeight() : getRecyclerHeight();
        float percent = (mLastMotionY - mInitialMotionY) / maxDistance;

        // Start at 100% if open
        if (mState == TranslateState.EXPANDED ||
                (mState == TranslateState.PARTIAL && mLastState == TranslateState.EXPANDED)) {
            percent += 1f;
        }
        percent = Math.min(Math.max(percent, 0f), 1f);
        return percent;
    }

    private float getRange() {
        return mMaxTranslation - mMinTranslation;
    }

    public void expand() {
        expand(null);
    }

    public void expand(Animator.AnimatorListener listener) {
        if (getRange() == 0) {
            if (listener != null) {
                listener.onAnimationStart(null);
                listener.onAnimationEnd(null);
            }
            return;
        }

        DisplayAnimator animator = new DisplayAnimator(getCurrentPercent(), 1f);
        if (listener != null) {
            animator.addListener(listener);
        }
        animator.start();

        // Close history when tapping on the background
        if (mFade != null) {
            mFade.setOnTouchListener(mFadeOnTouchListener);
        }
        setState(TranslateState.EXPANDED);
    }

    public void collapse() {
        collapse(null);
    }

    public void collapse(Animator.AnimatorListener listener) {
        if (getRange() == 0) {
            if (listener != null) {
                listener.onAnimationStart(null);
                listener.onAnimationEnd(null);
            }
            return;
        }

        DisplayAnimator animator = new DisplayAnimator(getCurrentPercent(), 0f);
        if (listener != null) {
            animator.addListener(listener);
        }
        animator.start();

        // Remove the background onTouchListener
        if (mFade != null) {
            mFade.setOnTouchListener(null);
        }
        setState(TranslateState.COLLAPSED);
    }

    public boolean isGraphExpanded() {
        return getTranslateState() == TranslateState.GRAPH_EXPANDED;
    }

    public boolean isExpanded() {
        return getTranslateState() == TranslateState.EXPANDED;
    }

    public boolean isCollapsed() {
        return getTranslateState() == TranslateState.COLLAPSED;
    }

    public void transitionToGraph(Animator.AnimatorListener listener) {
        if (mState == TranslateState.COLLAPSED) {
            setState(TranslateState.MINI_GRAPH);

            mDisplayGraph.setVisibility(View.VISIBLE);

            // We don't want the display resizing, so hardcode its width for now.
            mMainDisplay.measure(
                    View.MeasureSpec.makeMeasureSpec(mMainDisplay.getMeasuredWidth(), View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            );
            mMainDisplay.getLayoutParams().height = mMainDisplay.getMeasuredHeight();

            // Now we need to shrink the calculations display
            int oldHeight = mCalculationsDisplay.getMeasuredHeight();

            // Hide the result and then measure to grab new coordinates
            mResultEditText.setVisibility(View.GONE);
            mCalculationsDisplay.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
            mCalculationsDisplay.measure(
                    View.MeasureSpec.makeMeasureSpec(mCalculationsDisplay.getMeasuredWidth(), View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            );
            int newHeight = mCalculationsDisplay.getMeasuredHeight();

            // Now animate between the old and new heights
            float scale = mMaxDisplayScale = (float) newHeight / oldHeight;
            long duration = getResources().getInteger(android.R.integer.config_longAnimTime);

            // Due to a bug (?) setPivotY(0) does not work unless it's been set to a different pivotY
            // So we set it to the view's height first before setting it to 0 (where we want it).
            // Bug seen on Amazon's Fire Phone (4.2)
            mDisplayBackground.setPivotY(mDisplayBackground.getHeight());

            mDisplayBackground.setPivotY(0);
            mDisplayBackground.animate()
                    .scaleY(scale)
                    .setDuration(duration)
                    .setListener(null)
                    .start();

            // Update the foreground too (even though it's invisible)
            mDisplayForeground.setPivotY(0f);
            mDisplayForeground.animate()
                    .scaleY(scale)
                    .setDuration(duration)
                    .setListener(listener)
                    .start();
        } else {
            listener.onAnimationEnd(null);
        }
    }

    public void transitionToDisplay(Animator.AnimatorListener listener) {
        if (mState == TranslateState.MINI_GRAPH) {
            setState(TranslateState.COLLAPSED);

            // Show the result again
            mResultEditText.setVisibility(View.VISIBLE);

            // Now animate between the old and new heights
            float scale = mMaxDisplayScale = 1f;
            long duration = getResources().getInteger(android.R.integer.config_longAnimTime);
            mDisplayBackground.animate()
                    .scaleY(scale)
                    .setListener(new AnimationFinishedListener() {
                        @Override
                        public void onAnimationFinished() {
                            mDisplayGraph.setVisibility(View.GONE);
                        }
                    })
                    .setDuration(duration)
                    .start();

            // Update the foreground too (even though it's invisible)
            mDisplayForeground.animate()
                    .scaleY(scale)
                    .setListener(listener)
                    .setDuration(duration)
                    .start();
        }
    }

    public void expandGraph() {
        if (mState == TranslateState.MINI_GRAPH) {
            setState(TranslateState.GRAPH_EXPANDED);
            new GraphExpansionAnimator(0f, 1f).start();
            mDisplayGraph.setPanEnabled(true);
            mDisplayGraph.setZoomEnabled(true);
        }
    }

    public void collapseGraph() {
        if (mState == TranslateState.GRAPH_EXPANDED) {
            setState(TranslateState.MINI_GRAPH);
            new GraphExpansionAnimator(1f, 0f).start();
            mDisplayGraph.setPanEnabled(false);
            mDisplayGraph.setZoomEnabled(false);
        }
    }

    public void setAdapter(final RecyclerView.Adapter adapter) {
        mRecyclerView.setAdapter(adapter);
    }

    public void attachToRecyclerView(ItemTouchHelper itemTouchHelper) {
        itemTouchHelper.attachToRecyclerView(mRecyclerView);
    }

    public HistoryAdapter getAdapter() {
        return ((HistoryAdapter) mRecyclerView.getAdapter());
    }

    private boolean hasDisplayEntry() {
        HistoryAdapter adapter = (HistoryAdapter) mRecyclerView.getAdapter();
        return adapter.getDisplayEntry() != null;
    }

    private void evaluateHeight() {
        if (hasDisplayEntry()) {
            // The display turns into an entry item, which increases the recycler height by 1.
            // That means the height is dirty now :( Try again later.
            return;
        }

        mDisplayHeight = mMainDisplay.getHeight();

        if (mRecyclerView.getChildCount() == 0) {
            // If there's no history, set the range to 0
            mMinTranslation = mMaxTranslation = -getHeight() + mDisplayHeight;
            return;
        }

        mMinTranslation = -getHeight() + mDisplayHeight;
        int childHeight = getRecyclerHeight();
        if (childHeight < getHeight()) {
            mMaxTranslation = -getHeight() + childHeight;
        } else {
            mMaxTranslation = 0;
        }
    }

    private int getRecyclerHeight() {
        ensureTemplateViewExists();
        int childHeight = getAdapter().getDisplayEntry() != null ? 0 : mTemplateDisplay.getHeight();
        for (int i = 0; i < mRecyclerView.getChildCount(); i++) {
            childHeight += mRecyclerView.getChildAt(i).getHeight();
        }
        return childHeight;
    }

    public void scrollToMostRecent() {
        mRecyclerView.scrollToPosition(mRecyclerView.getAdapter().getItemCount()-1);
    }

    public void setFade(View view) {
        mFade = view;
    }

    /**
     * Controls the graph expanding to full screen (as well as the reverse)
     **/
    private class GraphExpansionAnimator extends ComplexAnimator {
        public GraphExpansionAnimator(float start, float end) {
            super(start, end);
        }

        public void onUpdate(float percent) {
            setTranslationY((1 - percent) * mMinTranslation);
            mDisplayGraph.setTranslationY((1 - percent) * -mMinTranslation);
            mMainDisplay.setTranslationY(percent * (mMainDisplay.getHeight() - getHeight()));
            mFormulaEditText.setAlpha(1 - percent * 4);
            mFormulaEditText.setEnabled(mFormulaEditText.getAlpha() > 0);
            mRecyclerView.setTranslationY((percent * (-getHeight() + mMainDisplay.getHeight())) - mMainDisplay.getHeight());
            mInfoText.setAlpha(1 - percent);

            int newHeight = getContext().getResources().getDimensionPixelSize(R.dimen.display_height_graph_expanded);
            int currentHeight = (int) (mDisplayBackground.getScaleY() * mDisplayBackground.getHeight());
            mDisplayBackground.setTranslationY(percent * (newHeight - currentHeight));

            mDisplayGraph.setTextColor(mixColors(percent, getResources().getColor(R.color.mini_graph_text), getResources().getColor(R.color.graph_text)));
        }
    }

    /**
     * Controls history being opened (and the reverse)
     **/
    private class DisplayAnimator extends ComplexAnimator {
        public DisplayAnimator(float start, float end) {
            super(start, end);
        }

        public void onUpdate(float percent) {
            if (getRange() == 0) {
                return;
            }

            // Update the drag animation
            float txY = mMinTranslation + percent * (getRange());
            setTranslationY(txY);

            // Update the background alpha
            if (mFade != null) {
                mFade.setAlpha(MAX_ALPHA * percent);
            }

            // Update the display
            final HistoryAdapter adapter = getAdapter();
            float adjustedTranslation = 0;

            // Get both our current width/height and the width/height we want to be
            View historyLine = ((TemplateHolder) mTemplateDisplay.getTag()).historyLine;
            int width = historyLine.getWidth();
            int height = historyLine.getHeight();
            int displayWidth = mDisplayBackground.getWidth();
            int displayHeight = mDisplayBackground.getHeight();

            // We're going to pretend the shadow doesn't exist (because, really, we want to scale the cards)
            // Scaling the shadow causes us to jump because we shrink too much
            int shadowSize = getContext().getResources().getDimensionPixelSize(R.dimen.display_shadow);

            // When we're fully expanded, turn the display into another row on the history adapter
            if (percent == 1f) {
                if (adapter.getDisplayEntry() == null) {
                    // We're at 100%, but haven't switched to the adapter yet. Time to do your thing.
                    adapter.setDisplayEntry(mFormulaEditText.getCleanText(), mResultEditText.getCleanText());
                    mDisplayBackground.setVisibility(View.GONE);
                    mCalculationsDisplay.setVisibility(View.GONE);
                    scrollToMostRecent();
                }

                // Adjust margins to match the entry
                adjustedTranslation += mTemplateDisplay.getHeight();
            } else if (adapter.getDisplayEntry() != null) {
                // We're no longer at 100%, so remove the entry (if it's attached)
                adapter.clearDisplayEntry();
                mDisplayBackground.setVisibility(View.VISIBLE);
                mCalculationsDisplay.setVisibility(View.VISIBLE);
            }

            float scaledWidth = scale(percent, (float) width / displayWidth);
            float scaledHeight = Math.min(scale(percent, (float) (height - shadowSize) / (displayHeight - shadowSize)), mMaxDisplayScale);

            // Scale the card behind everything
            mDisplayBackground.setScaleX(scaledWidth);
            mDisplayBackground.setScaleY(scaledHeight);

            // We have pivotY set to height, so we need to bump it up a little bit (because we ignored the shadow)
            // 3/4 is about the delta from the real shadow and the scaled shadow
            mDisplayBackground.setTranslationY(percent * -shadowSize * 3 / 4);

            // Move the formula over to the far left
            TextView exprView = ((TemplateHolder) mTemplateDisplay.getTag()).formula;
            float exprScale = exprView.getTextSize() / mFormulaEditText.getTextSize();
            mFormulaEditText.setScaleX(scale(percent, exprScale));
            mFormulaEditText.setScaleY(scale(percent, exprScale));
            float formulaWidth = exprView.getPaint().measureText(mFormulaEditText.getText().toString());
            mFormulaEditText.setTranslationX(percent * (
                    + formulaWidth
                            - exprScale * (mFormulaEditText.getWidth() - mFormulaEditText.getPaddingRight())
                            + getLeft(exprView, null)
            ));
            // TODO figure out why translation y = 0 works for formula edit text.
            mFormulaEditText.setTranslationY(0);
            mFormulaEditText.setTextColor(mixColors(percent, mFormulaInitColor, exprView.getCurrentTextColor()));

            // Move the result to keep in place with the display
            TextView resultView = ((TemplateHolder) mTemplateDisplay.getTag()).result;
            float resultScale = resultView.getTextSize() / mResultEditText.getTextSize();
            mResultEditText.setScaleX(scale(percent, resultScale));
            mResultEditText.setScaleY(scale(percent, resultScale));
            mResultEditText.setTranslationX(percent * (
                    // We have pivotX set at getWidth(), so the right sides will match up.
                    // Adjust the right edges of the real and the calculated views
                    -getRight(mResultEditText, mCalculationsDisplay)
                            + getRight(resultView, null)

                            // But getRight() doesn't include padding! So match the padding as well
                            + mResultEditText.getPaddingRight() * scale(percent, resultScale)
                            - resultView.getPaddingRight()
            ));
            mResultEditText.setTranslationY(percent * (
                    // Likewise, pivotY is set to 0, so the top sides will match up
                    // Adjust the top edges of the real and the calculated views
                    -getTop(mResultEditText, mCalculationsDisplay)
                            + getTop(resultView, null)

                            // But getTop() doesn't include padding! So match the padding as well
                            - mResultEditText.getPaddingTop() * scale(percent, resultScale)
                            + resultView.getPaddingTop()
            ));
            mResultEditText.setTextColor(mixColors(percent, mResultInitColor, resultView.getCurrentTextColor()));

            // Fade away HEX/RAD info text
            mInfoText.setAlpha(scale(percent, 0));

            // Handle readjustment of everything so it follows the finger
            adjustedTranslation += percent * (
                    + mDisplayBackground.getPivotY()
                            - mDisplayBackground.getPivotY() * height / mDisplayBackground.getHeight());

            mCalculationsDisplay.setTranslationY(adjustedTranslation);
            mInfoText.setTranslationY(adjustedTranslation);
            mRecyclerView.setTranslationY(-mDisplayHeight + adjustedTranslation);

            // Enable/disable the edit text.
            if (percent == 0) {
                mFormulaEditText.setEnabled(true);
                mFormulaEditText.setSelection(mFormulaEditText.getText().length());
            } else {
                mFormulaEditText.setEnabled(false);
            }

            if (DEBUG) {
                Log.d(TAG, String.format("percent=%s,txY=%s,alpha=%s,width=%s,height=%s,scaledWidth=%s,scaledHeight=%s",
                        percent, txY, mFade.getAlpha(), width, height, scaledWidth, scaledHeight));
            }
        }
    }

    /**
     * An animator that goes from 0 to 100%
     **/
    public abstract class ComplexAnimator extends ValueAnimator {
        public ComplexAnimator(float start, float end) {
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

        public abstract void onUpdate(float percent);

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

    private int getLeft(View view, View relativeTo) {
        if (view == null || view == relativeTo) {
            return 0;
        }
        return view.getLeft() + (view.getParent() instanceof View ? getLeft((View) view.getParent(), relativeTo) : 0);
    }

    private int getRight(View view, View relativeTo) {
        return getLeft(view, relativeTo) + view.getWidth();
    }

    private int getTop(View view, View relativeTo) {
        if (view == null || view == relativeTo) {
            return 0;
        }
        return view.getTop() + (view.getParent() instanceof View ? getTop((View) view.getParent(), relativeTo) : 0);
    }

    private static class TemplateHolder {
        View graph;
        TextView formula;
        TextView result;
        View historyLine;
    }
}
