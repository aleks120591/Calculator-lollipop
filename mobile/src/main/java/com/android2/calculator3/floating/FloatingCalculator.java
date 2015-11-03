package com.android2.calculator3.floating;

import android.support.v4.view.ViewPager;
import android.support.v7.widget.RecyclerView;
import android.text.InputType;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ViewSwitcher;

import com.android2.calculator3.Calculator;
import com.android2.calculator3.CalculatorExpressionEvaluator;
import com.android2.calculator3.CalculatorExpressionTokenizer;
import com.android2.calculator3.Clipboard;
import com.android2.calculator3.HistoryAdapter;
import com.android2.calculator3.R;
import com.android2.calculator3.view.BackspaceImageButton;
import com.android2.calculator3.view.CalculatorEditText;
import com.xlythe.floatingview.FloatingView;
import com.xlythe.math.Constants;
import com.xlythe.math.EquationFormatter;
import com.xlythe.math.History;
import com.xlythe.math.HistoryEntry;
import com.xlythe.math.Persist;
import com.xlythe.math.Solver;


public class FloatingCalculator extends FloatingView {
    // Calc logic
    private View.OnClickListener mListener;
    private ViewSwitcher mDisplay;
    private BackspaceImageButton mDelete;
    private ViewPager mPager;
    private Persist mPersist;
    private History mHistory;
    private CalculatorExpressionTokenizer mTokenizer;
    private CalculatorExpressionEvaluator mEvaluator;
    private State mState;

    private enum State {
        DELETE, CLEAR, ERROR
    }

    public View inflateButton() {
        return View.inflate(getContext(), R.layout.floating_calculator_icon, null);
    }

    public View inflateView() {
        // Rebuild constants. If the user changed their locale, it won't kill the app
        // but it might change a decimal point from . to ,
        Constants.rebuildConstants();

        final View child = View.inflate(getContext(), R.layout.floating_calculator, null);

        mTokenizer = new CalculatorExpressionTokenizer(this);
        mEvaluator = new CalculatorExpressionEvaluator(mTokenizer);

        mPager = (ViewPager) child.findViewById(R.id.panelswitch);

        mPersist = new Persist(this);
        mPersist.load();

        mHistory = mPersist.getHistory();

        mDisplay = (ViewSwitcher) child.findViewById(R.id.display);
        for (int i = 0; i < mDisplay.getChildCount(); i++) {
            final CalculatorEditText displayChild = (CalculatorEditText) mDisplay.getChildAt(i);
            displayChild.setSolver(mEvaluator.getSolver());
            displayChild.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    copyContent(displayChild.getCleanText());
                    return true;
                }
            });
            displayChild.setInputType(InputType.TYPE_NULL);
        }

        mDelete = (BackspaceImageButton) child.findViewById(R.id.delete);
        mListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switch (v.getId()) {
                    case R.id.delete:
                        if (mDelete.getState() == BackspaceImageButton.State.CLEAR) {
                            mDisplay.showNext();
                            onClear();
                        } else {
                            onDelete();
                        }
                        break;
                    case R.id.eq:
                        mEvaluator.evaluate(getActiveEditText().getCleanText(), new CalculatorExpressionEvaluator.EvaluateCallback() {
                            @Override
                            public void onEvaluate(String expr, String result, int errorResourceId) {
                                mDisplay.showNext();
                                if (errorResourceId != Calculator.INVALID_RES_ID) {
                                    onError(errorResourceId);
                                } else {
                                    setText(result);
                                }if (saveHistory(expr, result)) {
                                    RecyclerView history = (RecyclerView) child.findViewById(R.id.history);
                                    history.getLayoutManager().scrollToPosition(history.getAdapter().getItemCount() - 1);
                                }
                            }
                        });
                        break;
                    case R.id.parentheses:
                        setText("(" + getActiveEditText().getText() + ")");
                        break;
                    default:
                        if(((Button) v).getText().toString().length() >= 2) {
                            onInsert(((Button) v).getText().toString() + "(");
                        } else {
                            onInsert(((Button) v).getText().toString());
                        }
                        break;
                }
            }
        };
        mDelete.setOnClickListener(mListener);
        mDelete.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (mDelete.getState() == BackspaceImageButton.State.DELETE) {
                    mDisplay.showNext();
                    onClear();
                    return true;
                }

                return false;
            }
        });

        FloatingHistoryAdapter.HistoryItemCallback historyItemCallback = new FloatingHistoryAdapter.HistoryItemCallback() {
            @Override
            public void onHistoryItemSelected(HistoryEntry entry) {
                setState(State.DELETE);
                getActiveEditText().insert(entry.getResult());
            }
        };
        final FloatingCalculatorPageAdapter adapter = new FloatingCalculatorPageAdapter(
                getContext(), mListener, historyItemCallback, mEvaluator.getSolver(), mHistory);
        mPager.setAdapter(adapter);
        mPager.setCurrentItem(1);
        mPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            private int mActivePage = -1;

            @Override
            public void onPageScrolled(int i, float v, int i1) {
                // We're scrolling, so enable everything
                if (mActivePage != -1) {
                    mActivePage = -1;
                    setActivePage(mActivePage);
                }
            }

            @Override
            public void onPageSelected(int i) {
                // We've landed on a page, so disable all pages but this one
                mActivePage = i;
                setActivePage(mActivePage);
            }

            @Override
            public void onPageScrollStateChanged(int i) {
                // We've landed on a page (possibly the current page) so disable all pages but this one
                if (mActivePage == -1) {
                    mActivePage = mPager.getCurrentItem();
                    setActivePage(mActivePage);
                }
            }

            private void setActivePage(int page) {
                for (int i = 0; i < adapter.getCount(); i++) {
                    adapter.setEnabled(adapter.getViewAt(i), page == -1 || i == page);
                }
            }
        });

        child.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return true;
            }
        });

        setState(State.DELETE);

        return child;
    }

    @Override
    public void openView() {
        super.openView();
        if (mPager != null) {
            mPager.setCurrentItem(1);
        }

        if (mDisplay != null) {
            for (int i = 0; i < mDisplay.getChildCount(); i++) {
                final CalculatorEditText displayChild = (CalculatorEditText) mDisplay.getChildAt(i);
                displayChild.setSelection(displayChild.length());
            }
        }
    }

    @Override
    public void closeView(boolean returnToOrigin) {
        super.closeView(returnToOrigin);
        if (mPersist != null) {
            mPersist.save();
        }
    }

    private void onDelete() {
        setState(State.DELETE);
        getActiveEditText().backspace();
    }

    private void onClear() {
        setState(State.DELETE);
        getActiveEditText().clear();
    }

    private void setText(String text) {
        setState(State.CLEAR);
        getActiveEditText().setText(text);
    }

    private void onInsert(String text) {
        if (mState == State.ERROR || (mState == State.CLEAR && !Solver.isOperator(text))) {
            setText(text);
        } else {
            getActiveEditText().insert(text);
        }

        setState(State.DELETE);
    }

    private void onError(int resId) {
        setState(State.ERROR);
        getActiveEditText().setText(resId);
    }

    private void setState(State state) {
        mDelete.setState(state == State.DELETE ? BackspaceImageButton.State.DELETE : BackspaceImageButton.State.CLEAR);
        if(mState != state) {
            switch (state) {
                case CLEAR:
                    getActiveEditText().setTextColor(getResources().getColor(R.color.display_formula_text_color));
                    break;
                case DELETE:
                    getActiveEditText().setTextColor(getResources().getColor(R.color.display_formula_text_color));
                    break;
                case ERROR:
                    getActiveEditText().setTextColor(getResources().getColor(R.color.calculator_error_color));
                    break;
            }
            mState = state;
        }
    }

    private void copyContent(String text) {
        Clipboard.copy(getContext(), text);
    }

    private CalculatorEditText getActiveEditText() {
        return (CalculatorEditText) mDisplay.getCurrentView();
    }

    protected boolean saveHistory(String expr, String result) {
        if (mHistory == null) {
            return false;
        }

        if (!TextUtils.isEmpty(expr)
                && !TextUtils.isEmpty(result)
                && !Solver.equal(expr, result)
                && (mHistory.current() == null || !mHistory.current().getFormula().equals(expr))) {
            expr = EquationFormatter.appendParenthesis(expr);
            expr = Solver.clean(expr);
            expr = mTokenizer.getLocalizedExpression(expr);
            mHistory.enter(expr, result);
            return true;
        }
        return false;
    }
}
