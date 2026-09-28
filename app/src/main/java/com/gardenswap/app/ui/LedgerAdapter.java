package com.gardenswap.app.ui;

import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.gardenswap.app.api.LedgerEntry;

import java.util.ArrayList;
import java.util.List;

/**
 * RecyclerView adapter for the wallet ledger (UID-015). Optional header view
 * (balance card, expiry cue, cap lines, explainer, history title) followed by
 * {@link LedgerRowView} entry rows. Null-safe: null header or entries render
 * as empty.
 */
public class LedgerAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_ENTRY = 1;

    private View header;
    private List<LedgerEntry> entries = new ArrayList<>();

    public void setHeader(View header) {
        this.header = header;
        notifyDataSetChanged();
    }

    public void setEntries(List<LedgerEntry> entries) {
        this.entries = entries == null ? new ArrayList<>() : new ArrayList<>(entries);
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return (header != null && position == 0) ? TYPE_HEADER : TYPE_ENTRY;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_HEADER) {
            return new HeaderHolder(header);
        }
        LedgerRowView row = new LedgerRowView(parent.getContext());
        RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        int margin = Ui.dp(parent.getContext(), 8);
        params.bottomMargin = margin;
        row.setLayoutParams(params);
        return new EntryHolder(row);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof EntryHolder) {
            int index = header == null ? position : position - 1;
            ((EntryHolder) holder).row.bind(entries.get(index));
        }
    }

    @Override
    public int getItemCount() {
        return (header == null ? 0 : 1) + entries.size();
    }

    static class HeaderHolder extends RecyclerView.ViewHolder {
        HeaderHolder(View header) {
            super(header);
        }
    }

    static class EntryHolder extends RecyclerView.ViewHolder {
        final LedgerRowView row;

        EntryHolder(LedgerRowView row) {
            super(row);
            this.row = row;
        }
    }
}
