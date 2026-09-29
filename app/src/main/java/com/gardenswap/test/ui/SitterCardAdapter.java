package com.gardenswap.test.ui;

import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.gardenswap.test.api.SitterProfile;

import java.util.ArrayList;
import java.util.List;

/**
 * RecyclerView adapter rendering {@link SitterCardView} rows (UID-017).
 */
public class SitterCardAdapter extends RecyclerView.Adapter<SitterCardAdapter.ViewHolder> {

    /** Click callback; receives the bound sitter. */
    public interface OnSitterClickListener {
        void onSitterClick(SitterProfile sitter);
    }

    private List<SitterProfile> sitters = new ArrayList<>();
    private OnSitterClickListener clickListener;

    public SitterCardAdapter(List<SitterProfile> sitters) {
        setSitters(sitters);
    }

    public void setSitters(List<SitterProfile> sitters) {
        this.sitters = sitters == null ? new ArrayList<>() : new ArrayList<>(sitters);
        notifyDataSetChanged();
    }

    public void setOnSitterClickListener(OnSitterClickListener listener) {
        this.clickListener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        SitterCardView card = new SitterCardView(parent.getContext());
        RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = Ui.dp(parent.getContext(), 12);
        card.setLayoutParams(params);
        return new ViewHolder(card);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SitterProfile sitter = sitters.get(position);
        holder.card.bind(sitter);
        holder.card.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onSitterClick(sitter);
            }
        });
    }

    @Override
    public int getItemCount() {
        return sitters.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final SitterCardView card;

        ViewHolder(SitterCardView card) {
            super(card);
            this.card = card;
        }
    }
}
