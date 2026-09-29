package com.gardenswap.test.ui;

import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.gardenswap.test.api.Listing;

import java.util.ArrayList;
import java.util.List;

/**
 * RecyclerView adapter rendering {@link ListingCardView} rows (UID-011).
 */
public class ListingCardAdapter extends RecyclerView.Adapter<ListingCardAdapter.ViewHolder> {

    /** Click callback; receives the bound listing. */
    public interface OnListingClickListener {
        void onListingClick(Listing listing);
    }

    private List<Listing> listings = new ArrayList<>();
    private OnListingClickListener clickListener;

    public ListingCardAdapter(List<Listing> listings) {
        setListings(listings);
    }

    public void setListings(List<Listing> listings) {
        this.listings = listings == null ? new ArrayList<>() : new ArrayList<>(listings);
        notifyDataSetChanged();
    }

    public void setOnListingClickListener(OnListingClickListener listener) {
        this.clickListener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ListingCardView card = new ListingCardView(parent.getContext());
        RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        int density = Math.round(parent.getContext().getResources().getDisplayMetrics().density);
        params.bottomMargin = 12 * density;
        card.setLayoutParams(params);
        return new ViewHolder(card);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Listing listing = listings.get(position);
        holder.card.bind(listing);
        holder.card.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onListingClick(listing);
            }
        });
    }

    @Override
    public int getItemCount() {
        return listings.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ListingCardView card;

        ViewHolder(ListingCardView card) {
            super(card);
            this.card = card;
        }
    }
}
