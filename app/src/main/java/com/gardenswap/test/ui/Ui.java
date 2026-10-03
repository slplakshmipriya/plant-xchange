package com.gardenswap.test.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.res.ResourcesCompat;

import com.gardenswap.test.R;
import com.gardenswap.test.brand.BrandConfig;
import com.gardenswap.test.util.ImageLoader;
import com.gardenswap.test.util.SitterServices;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Tiny programmatic-UI helpers (AND-010). Wave 1 uses no XML layouts; these
 * keep the onboarding/IDV screens consistent without duplication.
 *
 * <p>UID-003 adds the design-system components: pill buttons, cards, chips,
 * and type-scale text views matching the garden-swap-app-ui-design
 * prototype.
 */
public final class Ui {

    private Ui() {
    }

    public static int dp(Context context, int dp) {
        return Math.round(dp * context.getResources().getDisplayMetrics().density);
    }

    /** Vertical LinearLayout with uniform padding. */
    public static LinearLayout column(Context context, int paddingDp) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(context, paddingDp);
        layout.setPadding(padding, padding, padding, padding);
        return layout;
    }

    /**
     * Screen scaffold with a pinned title bar: {@code header} stays fixed at
     * the top while {@code content} fills the remaining space beneath it, so
     * the title never scrolls away. Give {@code content} its own scrolling
     * (a ScrollView, or a self-scrolling view like RecyclerView). Works with
     * {@link Nav#attach}: each tab keeps its own sticky header, so the title
     * bar is in the same position on every tab.
     */
    public static LinearLayout stickyHeaderScreen(
            Context context, View header, View content) {
        LinearLayout screen = new LinearLayout(context);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        screen.addView(content, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return screen;
    }

    /**
     * App brand row: the brand mark ({@link BrandConfig#TITLE_MARK_RES}) +
     * the app-name wordmark from {@code strings.xml}. Used as the sticky
     * header on every bottom-nav tab so the branding sits in the same
     * position app-wide. The header sits directly on the emerald window
     * background, so the wordmark uses the pale on-background token.
     */
    public static LinearLayout appTitleRow(Context context) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        ImageView mark = new ImageView(context);
        mark.setImageResource(BrandConfig.TITLE_MARK_RES);
        int size = dp(context, 32);
        row.addView(mark, new LinearLayout.LayoutParams(size, size));
        TextView title = display(context, context.getString(BrandConfig.APP_NAME_RES));
        textColor(context, title, R.color.garden_on_bg);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.leftMargin = dp(context, 8);
        row.addView(title, params);
        return row;
    }

    public static TextView label(Context context, String text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(14);
        return view;
    }

    /** Display heading: Libre Franklin bold 28sp. */
    public static TextView display(Context context, String text) {
        return styledText(context, text, R.style.TextAppearance_GardenSwap_Display);
    }

    /** Section heading: Libre Franklin bold 22sp. */
    public static TextView headline(Context context, String text) {
        return styledText(context, text, R.style.TextAppearance_GardenSwap_Headline);
    }

    /** Card title: Libre Franklin bold 18sp. */
    public static TextView title(Context context, String text) {
        return styledText(context, text, R.style.TextAppearance_GardenSwap_Title);
    }

    /** Body copy: DM Sans 15sp. */
    public static TextView body(Context context, String text) {
        return styledText(context, text, R.style.TextAppearance_GardenSwap_Body);
    }

    /** Eyebrow label: DM Sans bold 13sp, letterspaced, muted. */
    public static TextView eyebrow(Context context, String text) {
        TextView view = styledText(context, text.toUpperCase(), R.style.TextAppearance_GardenSwap_Label);
        return view;
    }

    /** Small print: DM Sans 12sp, muted. */
    public static TextView caption(Context context, String text) {
        return styledText(context, text, R.style.TextAppearance_GardenSwap_Caption);
    }

    private static TextView styledText(Context context, String text, int textAppearance) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextAppearance(textAppearance);
        return view;
    }

    public static TextView status(Context context) {
        return label(context, "");
    }

    public static EditText input(Context context, String hint, int inputType) {
        EditText view = new EditText(context);
        view.setHint(hint);
        view.setInputType(inputType);
        view.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return view;
    }

    public static Button button(Context context, String text) {
        Button view = new Button(context);
        view.setText(text);
        view.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return view;
    }

    /** Primary CTA: sky-blue pill, white text, DM Sans medium. */
    public static Button primaryButton(Context context, String text) {
        Button view = new Button(context);
        view.setText(text);
        view.setBackgroundResource(R.drawable.btn_primary);
        view.setTextColor(ResourcesCompat.getColor(
                context.getResources(), R.color.brand_button_text, context.getTheme()));
        view.setTypeface(ResourcesCompat.getFont(context, R.font.dm_sans), Typeface.BOLD);
        view.setAllCaps(false);
        view.setTextSize(16);
        view.setGravity(Gravity.CENTER);
        view.setMinHeight(dp(context, 52));
        view.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return view;
    }

    /** Secondary action: pale sky fill + sky outline pill, deep-sky text so
     *  it reads on both the emerald background and white surfaces. */
    public static Button secondaryButton(Context context, String text) {
        Button view = primaryButton(context, text);
        view.setBackgroundResource(R.drawable.btn_secondary);
        view.setTextColor(ResourcesCompat.getColor(
                context.getResources(), R.color.brand_button_dark, context.getTheme()));
        return view;
    }

    /**
     * Button sized for a horizontal row. {@link #primaryButton} and
     * {@link #secondaryButton} default to MATCH_PARENT width (correct in
     * vertical columns), which silently squeezes every sibling to zero width
     * when the button lands in a horizontal LinearLayout. Use this instead of
     * building the button and overriding its params by hand.
     *
     * @param primary true for the brand-sky primary style, false for the
     *                outline secondary style.
     */
    public static Button rowButton(Context context, String text, boolean primary) {
        Button view = primary ? primaryButton(context, text)
                : secondaryButton(context, text);
        view.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return view;
    }

    /** Card container: surface fill, 18dp radius, 16dp inner padding. */
    public static LinearLayout card(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundResource(R.drawable.card_bg);
        int padding = dp(context, 16);
        layout.setPadding(padding, padding, padding, padding);
        layout.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return layout;
    }

    /**
     * Filter chip. Call {@link #setChipSelected} to toggle the selected
     * (brand sky fill) state.
     */
    public static TextView chip(Context context, String text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setBackgroundResource(R.drawable.chip_bg);
        view.setTextColor(ResourcesCompat.getColor(
                context.getResources(), R.color.garden_ink, context.getTheme()));
        view.setTypeface(ResourcesCompat.getFont(context, R.font.dm_sans), Typeface.BOLD);
        view.setTextSize(13);
        view.setGravity(Gravity.CENTER);
        int hPad = dp(context, 4);
        view.setPadding(view.getPaddingLeft(), hPad, view.getPaddingRight(), hPad);
        return view;
    }

    public static void setChipSelected(Context context, TextView chip, boolean selected) {
        chip.setBackgroundResource(
                selected ? R.drawable.chip_bg_selected : R.drawable.chip_bg);
        chip.setTextColor(ResourcesCompat.getColor(context.getResources(),
                selected ? R.color.garden_nav_ink : R.color.garden_ink,
                context.getTheme()));
    }

    /**
     * Wrapped grid of service chips, laid out in rows by estimated width so
     * chips stay on-screen. {@code keys} are taxonomy keys (or any service
     * keys); labels come from {@link SitterServices#displayName(String)}.
     *
     * <p>When {@code clickable}, tapping a chip toggles its key in
     * {@code selectedKeys} and flips the selected visual state. Otherwise
     * the chips are read-only display.
     */
    public static LinearLayout serviceChipGrid(Context context, String[] keys,
                                               final Set<String> selectedKeys,
                                               boolean clickable) {
        return serviceChipGrid(context, keys, selectedKeys, clickable, null);
    }

    /**
     * Same as {@link #serviceChipGrid(Context, String[], Set, boolean)}, plus
     * {@code onChanged} (may be null) which runs on the UI thread after every
     * toggle — lets the caller refresh dependent state (price, button).
     */
    public static LinearLayout serviceChipGrid(Context context, String[] keys,
                                               final Set<String> selectedKeys,
                                               boolean clickable,
                                               final Runnable onChanged) {
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        float density = context.getResources().getDisplayMetrics().density;
        int maxWidth = (int) (context.getResources().getDisplayMetrics().widthPixels
                / density) - 48;
        LinearLayout row = chipRow(context);
        column.addView(row);
        int used = 0;
        boolean any = false;
        Set<String> seen = new LinkedHashSet<>();
        if (keys != null) {
            for (String raw : keys) {
                if (raw == null || raw.trim().isEmpty() || !seen.add(raw.trim())) {
                    continue;
                }
                final String key = raw.trim();
                String label = SitterServices.displayName(key);
                int estimate = label.length() * 8 + 48;
                if (used > 0 && used + estimate > maxWidth) {
                    gap(column, context, 8);
                    row = chipRow(context);
                    column.addView(row);
                    used = 0;
                }
                TextView chip = chip(context, label);
                chip.setSingleLine(true);
                if (clickable) {
                    setChipSelected(context, chip, selectedKeys.contains(key));
                    chip.setOnClickListener(v -> {
                        boolean now = !selectedKeys.contains(key);
                        if (now) {
                            selectedKeys.add(key);
                        } else {
                            selectedKeys.remove(key);
                        }
                        setChipSelected(context, chip, now);
                        if (onChanged != null) {
                            onChanged.run();
                        }
                    });
                } else {
                    chip.setClickable(false);
                    chip.setFocusable(false);
                }
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                params.setMarginEnd(dp(context, 8));
                chip.setLayoutParams(params);
                row.addView(chip);
                used += estimate;
                any = true;
            }
        }
        if (!any) {
            column.removeAllViews();
            column.addView(body(context, "Services on request"));
        }
        return column;
    }

    private static LinearLayout chipRow(Context context) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        return row;
    }

    /** Fixed-height vertical spacer. */
    public static void gap(LinearLayout parent, Context context, int dp) {
        View spacer = new View(context);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(context, dp)));
        parent.addView(spacer);
    }

    /** Sets a TextView's color from a color resource (theme-aware). */
    public static void textColor(Context context, TextView view, int colorRes) {
        view.setTextColor(ResourcesCompat.getColor(
                context.getResources(), colorRes, context.getTheme()));
    }

    /**
     * Person avatar: their photo (circular, via {@link ImageLoader}) when an
     * avatar URL is set, else a circular initials placeholder. Broken URLs
     * fall back to the leaf glyph, never a blank hole.
     */
    public static View avatarView(Context context, String name,
                                  String avatarUrl, int sizeDp) {
        int size = dp(context, sizeDp);
        if (avatarUrl != null && !avatarUrl.trim().isEmpty()) {
            ImageView view = new ImageView(context);
            view.setLayoutParams(new LinearLayout.LayoutParams(size, size));
            ImageLoader.loadCircularInto(view, avatarUrl);
            return view;
        }
        TextView view = new TextView(context);
        view.setText(initialsOf(name));
        view.setGravity(Gravity.CENTER);
        view.setTypeface(null, Typeface.BOLD);
        view.setTextSize(16);
        view.setTextColor(ResourcesCompat.getColor(context.getResources(),
                R.color.garden_ink, context.getTheme()));
        android.graphics.drawable.GradientDrawable bg =
                new android.graphics.drawable.GradientDrawable();
        bg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        bg.setColor(ResourcesCompat.getColor(context.getResources(),
                R.color.garden_line, context.getTheme()));
        view.setBackground(bg);
        view.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        return view;
    }

    private static String initialsOf(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "?";
        }
        String[] parts = name.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(2, parts.length); i++) {
            sb.append(Character.toUpperCase(parts[i].charAt(0)));
        }
        return sb.toString();
    }
}
