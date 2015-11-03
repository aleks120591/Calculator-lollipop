/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android2.calculator3;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.text.Html;
import android.text.Spanned;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.android2.calculator3.view.GraphView;
import com.android2.calculator3.view.HistoryLine;
import com.xlythe.math.Constants;
import com.xlythe.math.EquationFormatter;
import com.xlythe.math.GraphModule;
import com.xlythe.math.History;
import com.xlythe.math.HistoryEntry;
import com.xlythe.math.Solver;

import java.util.List;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {
    private final Context mContext;
    private final Solver mSolver;
    private final List<HistoryEntry> mEntries;
    private final EquationFormatter mEquationFormatter;
    private final String mX;
    private OnItemClickListener mOnItemClickListener;
    private OnItemLongClickListener mOnItemLongClickListener;
    private HistoryEntry mDisplayEntry;

    public interface OnItemClickListener {
        void onItemClick(HistoryEntry entry);
    }

    public interface OnItemLongClickListener {
        void onItemLongClick(HistoryEntry entry);
    }

    public HistoryAdapter(Context context, Solver solver, History history) {
        mContext = context;
        mSolver = solver;
        mEntries = history.getEntries();
        mEquationFormatter = new EquationFormatter();
        mX = context.getString(R.string.var_x);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public HistoryLine historyLine;
        public TextView historyExpr;
        public TextView historyResult;
        public GraphView graphView;

        public ViewHolder(View v) {
            super(v);
            historyLine = (HistoryLine) v.findViewById(R.id.history_line);
            historyExpr = (TextView) v.findViewById(R.id.historyExpr);
            historyResult = (TextView) v.findViewById(R.id.historyResult);
            graphView = (GraphView) v.findViewById(R.id.graph);
        }
    }

    public void setOnItemClickListener(OnItemClickListener onItemClickListener) {
        mOnItemClickListener = onItemClickListener;
    }

    public void setOnItemLongclickListener(OnItemLongClickListener onItemLongClickListener) {
        mOnItemLongClickListener = onItemLongClickListener;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(getContext()).inflate(R.layout.history_entry, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        final HistoryEntry entry = getEntry(position);
        invalidate(holder, entry, position);
    }

    private void invalidate(final ViewHolder holder, final HistoryEntry entry, int position) {
        final HistoryEntry nextEntry = getNextEntry(position);
        final HistoryLine view = holder.historyLine;

        view.setAdapter(this);
        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mOnItemClickListener != null) {
                    mOnItemClickListener.onItemClick(entry);
                }
            }
        });
        view.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (mOnItemLongClickListener != null) {
                    mOnItemLongClickListener.onItemLongClick(entry);
                    return true;
                }
                return false;
            }
        });

        holder.historyExpr.setText(formatText(entry.getFormula()));
        holder.historyResult.setText(formatText(entry.getResult()));

        // Disable any and all graphs (the default state)
        GraphController controller = null;
        if (holder.graphView != null) {
            holder.graphView.setVisibility(View.GONE);
            controller = (GraphController) holder.graphView.getTag();
        }

        if (controller != null) {
            controller.destroy();
            controller.clear();
        }

        if (nextEntry != null && entry.getGroupId() == nextEntry.getGroupId()) {
            // Set a subitem background (so there's a divider instead of a shadow
            view.setBackgroundResource(R.drawable.white_card_subitem);
            view.setPadding(dp(16), dp(8), dp(16), dp(8));
        } else {
            view.setBackgroundResource(R.drawable.white_card);
            view.setPadding(dp(16), dp(8), dp(16), dp(8) + getContext().getResources().getDimensionPixelSize(R.dimen.display_shadow));

            // If this is a graph formula, start drawing the graph
            if (hasGraph(entry.getFormula())) {
                holder.historyResult.setText(R.string.panel_graph);
                if (holder.graphView != null) {
                    holder.graphView.setVisibility(View.VISIBLE);

                    if (controller == null) {
                        controller = new GraphController(new GraphModule(mSolver), holder.graphView);
                        holder.graphView.setTag(controller);
                    }
                    controller.addNewGraph(entry.getFormula());

                    int pos = position - 1;
                    HistoryEntry previousEntry = getEntry(pos);
                    while (previousEntry != null && previousEntry.getGroupId() == entry.getGroupId()) {
                        // We'll iterate over all the entries with the same group id as us and check for graphs
                        if (hasGraph(previousEntry.getFormula())) {
                            controller.addNewGraph(previousEntry.getFormula());
                        }
                        previousEntry = getEntry(--pos);
                    }
                }
            }
        }
    }

    public View parseView(ViewGroup parent, String formula, String result) {
        ViewHolder holder = onCreateViewHolder(parent, 0);
        invalidate(holder, new HistoryEntry(formula, result, -1), -1);
        return holder.itemView;
    }

    public boolean hasGraph(String formula) {
        return formula.contains(mX);
    }

    private int dp(int dp) {
        float density = getContext().getResources().getDisplayMetrics().density;
        return (int) (dp * density);
    }

    private HistoryEntry getEntry(int position) {
        if (mDisplayEntry != null && position == mEntries.size()) {
            return mDisplayEntry;
        }

        if (position < 0 || position >= mEntries.size()) {
            return null;
        }

        return mEntries.get(position);
    }

    private HistoryEntry getNextEntry(int position) {
        if (position == -1) return null;
        return getEntry(++position);
    }

    public HistoryEntry getDisplayEntry() {
        return mDisplayEntry;
    }

    public void setDisplayEntry(String formula, String result) {
        mDisplayEntry = new HistoryEntry(formula, result, -1);
        notifyDataSetChanged();
    }

    public void clearDisplayEntry() {
        mDisplayEntry = null;
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        if (mDisplayEntry == null) {
            return mEntries.size();
        } else {
            return mEntries.size() + 1;
        }
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    protected Spanned formatText(String text) {
        if (text == null) {
            return null;
        }

        if (text.matches(".*\\de[-" + Constants.MINUS + "]?\\d.*")) {
            text = text.replace("e", Constants.MUL + "10^");
        }
        return Html.fromHtml(
                mEquationFormatter.insertSupScripts(
                        mEquationFormatter.addComas(mSolver, text)));
    }

    public Context getContext() {
        return mContext;
    }
}
