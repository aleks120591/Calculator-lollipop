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
package com.android2.calculator3;

import android.animation.Animator;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.CompoundButton;
import android.widget.ListView;
import android.widget.TextView;

import com.android2.calculator3.view.CalculatorEditText;
import com.android2.calculator3.view.DisplayOverlay;
import com.android2.calculator3.view.GraphView;
import com.rey.material.drawable.CheckBoxDrawable;
import com.rey.material.widget.CheckBox;
import com.xlythe.floatingview.AnimationFinishedListener;
import com.xlythe.math.GraphModule;

import java.util.Locale;

/**
 * Adds graphing and base switching to the basic calculator.
 * */
public abstract class GraphingCalculator extends HexCalculator {

    private DisplayOverlay mDisplayView;
    private CalculatorEditText mFormulaEditText;

    private String mX;
    private GraphView mMiniGraph;
    private View mGraphButtons;
    private ListView mActiveEquationsListView;
    private BaseAdapter mCurrentGraphsAdapter;

    private GraphController mGraphController;

    private boolean mMarkAsCleared = false;

    @Override
    protected void initialize(Bundle savedInstanceState) {
        super.initialize(savedInstanceState);

        // Load up all are variables (find views, find out what 'X' is called)
        mX = getString(R.string.var_x);
        mDisplayView = (DisplayOverlay) findViewById(R.id.display);
        mFormulaEditText = (CalculatorEditText) findViewById(R.id.formula);
        mMiniGraph = (GraphView) findViewById(R.id.mini_graph);
        mGraphButtons = findViewById(R.id.graph_buttons);
        mActiveEquationsListView = (ListView) findViewById(R.id.current_graphs);

        mGraphController = new GraphController(new GraphModule(getEvaluator().getSolver()), mMiniGraph);

        mMiniGraph.setOnCenterListener(new GraphView.OnCenterListener() {
            @Override
            public void onCentered() {
                ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) mMiniGraph.getLayoutParams();
                int displayHeight = getResources().getDimensionPixelSize(R.dimen.display_height_with_shadow);

                // Move the X axis so it lines up to the top of the view (so we have a good starting point)
                float initialY = -mMiniGraph.getHeight() / 2;
                // Now move it up to the top of the phone
                initialY -= params.topMargin;
                // Move the X axis down so it matches the bottom of the display
                initialY += displayHeight;
                // Move it up 50% between the formula and the end of the display
                initialY -= (displayHeight - mFormulaEditText.getHeight()) / 2;

                mMiniGraph.panBy(0, initialY);
            }
        });
        invalidateInlineBounds();
        mGraphButtons.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                if (android.os.Build.VERSION.SDK_INT < 16) {
                    mGraphButtons.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                } else {
                    mGraphButtons.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                }
                mGraphButtons.setTranslationY(mGraphButtons.getHeight());
                resetGraph();
            }
        });

        mCurrentGraphsAdapter = new ArrayAdapter<GraphView.Graph>(getBaseContext(), R.layout.graph_entry, mGraphController.getGraphs()) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                if (convertView == null) {
                    LayoutInflater inflater = getLayoutInflater();
                    convertView = inflater.inflate(R.layout.graph_entry, parent, false);
                }

                final GraphView.Graph item = getItem(position);

                String formula = item.getFormula();
                formula = formula.replace(mX, mX.toLowerCase(Locale.getDefault()));

                TextView textView = (TextView) convertView.findViewById(android.R.id.text1);
                textView.setText(String.format("f%s(%s)=%s", (position + 1), mX.toLowerCase(Locale.getDefault()), formula));

                return convertView;
            }
        };
        mActiveEquationsListView.setAdapter(mCurrentGraphsAdapter);
    }

    private void transitionToGraph() {
        mDisplayView.transitionToGraph(new AnimationFinishedListener() {
            @Override
            public void onAnimationFinished() {
                setState(CalculatorState.GRAPHING);
            }
        });
    }

    private void transitionToDisplay() {
        mDisplayView.transitionToDisplay(new AnimationFinishedListener() {
            @Override
            public void onAnimationFinished() {
                CalculatorState newState = getState() ==
                        CalculatorState.GRAPHING ? CalculatorState.INPUT : getState();
                setState(newState);

                if (mMarkAsCleared) {
                    mMarkAsCleared = false;
                    mGraphController.clear();
                }
            }
        });
    }

    private void enlargeGraph() {
        mDisplayView.expandGraph();
        mGraphButtons.animate().translationY(0).setListener(null);
    }

    private void shrinkGraph() {
        shrinkGraph(null);
    }

    private void shrinkGraph(Animator.AnimatorListener listener) {
        mDisplayView.collapseGraph();
        mGraphButtons.animate().translationY(mGraphButtons.getHeight()).setListener(listener);
    }

    private void resetGraph() {
        mMiniGraph.zoomReset();

    }

    private void invalidateInlineBounds() {
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) mMiniGraph.getLayoutParams();
        params.topMargin = getResources().getDimensionPixelSize(R.dimen.display_height_graph_expanded);
        params.topMargin -= getResources().getDimensionPixelSize(R.dimen.display_shadow);
        params.bottomMargin = mGraphButtons.getMeasuredHeight();
        params.bottomMargin -= getResources().getDimensionPixelSize(R.dimen.display_shadow);

        mMiniGraph.setLayoutParams(params);
    }

    @Override
    public void onButtonClick(View view) {
        switch (view.getId()) {
            case R.id.eq:
                mMarkAsCleared = true;
                break;
            case R.id.mini_graph:
                if (mDisplayView.isGraphExpanded()) {
                    shrinkGraph();
                } else {
                    enlargeGraph();
                }
                return;
            case R.id.btn_zoom_in:
                mMiniGraph.zoomIn();
                return;
            case R.id.btn_zoom_out:
                mMiniGraph.zoomOut();
                return;
            case R.id.btn_zoom_reset:
                resetGraph();
                return;
            case R.id.btn_close_graph:
                shrinkGraph();
                return;
        }
        super.onButtonClick(view);
    }

    @Override
    public void onBackPressed() {
        if (mDisplayView.isGraphExpanded()) {
            shrinkGraph();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onClear() {
        mMarkAsCleared = true;
        super.onClear();
    }

    @Override
    public void onEvaluate(String expr, String result, int errorResourceId) {
        if (getState() == CalculatorState.EVALUATE && expr.contains(mX)) {
            saveHistory(expr, "", false);
            incrementGroupId();
            mDisplayView.scrollToMostRecent();
            onResult(result);
        } else {
            super.onEvaluate(expr, result, errorResourceId);
        }

        if (expr.contains(mX)) {
            transitionToGraph();

            String formula = getNormalizedExpression(cleanExpression(mFormulaEditText.getCleanText()));

            if (mGraphController.getGraphs().size() == 0) {
                mGraphController.addNewGraph(formula);
            } else {
                mGraphController.changeLatestGraph(formula);
            }

            notifyDataSetChanged();
        } else {
            transitionToDisplay();
        }
    }

    @Override
    protected void onDestroy() {
        if (mGraphController != null) {
            mGraphController.destroy();
            mGraphController = null;
        }
        super.onDestroy();
    }

    private void notifyDataSetChanged() {
        mCurrentGraphsAdapter.notifyDataSetChanged();
        mGraphButtons.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                if (android.os.Build.VERSION.SDK_INT < 16) {
                    mGraphButtons.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                } else {
                    mGraphButtons.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                }
                mGraphButtons.setTranslationY(mGraphButtons.getHeight());
                invalidateInlineBounds();
            }
        });
    }
}
