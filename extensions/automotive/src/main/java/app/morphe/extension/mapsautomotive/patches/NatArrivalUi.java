
package app.morphe.extension.mapsautomotive.patches;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.Editable;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.text.style.ReplacementSpan;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class NatArrivalUi {

    // Navigation battery font scale.
    private static final float BATTERY_FONT_SCALE = 2.5f;

    // Route preview battery font scale.
    private static final float PREVIEW_FONT_SCALE = 2.0f;

    private static final Pattern ARRIVAL = Pattern.compile(
            "(?iu)^\\s*(?:Arrive with|Arrival battery|ไปถึงโดยเหลือ|"
                    + "แบตเมื่อถึง|แบตเตอรี่เมื่อถึง(?:ปลายทาง)?|แบตเมื่อไปถึง)"
                    + "\\s*([+−-]?[0-9]{1,3}\\s*[%％])\\s*$"
    );

    private static final Pattern PERCENT = Pattern.compile(
            "(?<![0-9+−-])([+−-]?[0-9]{1,3}\\s*[%％])"
    );

    private static final Pattern EXACT_PERCENT = Pattern.compile(
            "^\\s*[+−-]?[0-9]{1,3}\\s*[%％]\\s*$"
    );

    private static final Pattern DISTANCE = Pattern.compile(
            "(?iu)[0-9]+(?:[.,][0-9]+)?\\s*(?:กม\\.?|km|mi|เมตร|m\\b)"
    );

    private static final Pattern TIME = Pattern.compile(
            "(?iu)(?:[0-9]{1,2}:[0-9]{2}|"
                    + "[0-9]+\\s*(?:นาที|ชม\\.?|min|hr|hours?))"
    );

    private static final Handler MAIN =
            new Handler(Looper.getMainLooper());

    private static final WeakHashMap<Activity, Boolean> INSTALLED =
            new WeakHashMap<>();

    private static final WeakHashMap<TextView, Boolean> WATCHED =
            new WeakHashMap<>();

    private static final WeakHashMap<View, Boolean> ROOTS =
            new WeakHashMap<>();

    private static final WeakHashMap<View, Boolean> QUEUED =
            new WeakHashMap<>();

    private NatArrivalUi() {
    }

    /**
     * Custom span to identify our own font-size changes.
     */
    private static final class BatterySizeSpan extends RelativeSizeSpan {

        BatterySizeSpan(float scale) {
            super(scale);
        }
    }

    /**
     * Custom span to identify our own bold formatting.
     */
    private static final class BatteryBoldSpan extends StyleSpan {

        BatteryBoldSpan() {
            super(Typeface.BOLD);
        }
    }

    /**
     * Preserve the existing route-preview battery card.
     */
    private static final class NatSize extends ReplacementSpan {

        private final int value;
        private final boolean dark;
        private final boolean thai;
        private final String comparison;

        NatSize(
                int value,
                boolean dark,
                boolean thai,
                String comparison
        ) {
            this.value = value;
            this.dark = dark;
            this.thai = thai;
            this.comparison = comparison;
        }

        private Paint numberPaint(Paint original) {
            Paint p = new Paint(original);

            p.setTextSize(
                    original.getTextSize() * PREVIEW_FONT_SCALE
            );

            p.setTypeface(
                    Typeface.create(
                            original.getTypeface(),
                            Typeface.BOLD
                    )
            );

            p.setColor(
                    value < 0
                            ? (dark ? 0xffff8888 : 0xffb91c1c)
                            : (dark ? 0xffffffff : 0xff111111)
            );

            return p;
        }

        @Override
        public int getSize(
                Paint paint,
                CharSequence text,
                int start,
                int end,
                Paint.FontMetricsInt fm
        ) {
            float u = paint.getTextSize();
            Paint p = numberPaint(paint);

            if (fm != null) {
                fm.ascent = Math.min(
                        fm.ascent,
                        (int) Math.floor(p.ascent() - u * 1.15f)
                );

                fm.descent = Math.max(
                        fm.descent,
                        (int) Math.ceil(
                                p.descent()
                                        + u * (comparison.isEmpty()
                                        ? .65f
                                        : 1.65f)
                        )
                );

                fm.top = Math.min(fm.top, fm.ascent);
                fm.bottom = Math.max(fm.bottom, fm.descent);
            }

            return (int) Math.ceil(
                    Math.max(
                            u * 5.2f,
                            p.measureText(text, start, end) + u
                    )
            );
        }

        @Override
        public void draw(
                Canvas c,
                CharSequence text,
                int start,
                int end,
                float x,
                int top,
                int y,
                int bottom,
                Paint paint
        ) {
            float u = paint.getTextSize();
            float w = getSize(paint, text, start, end, null);

            Paint p = numberPaint(paint);
            Paint shape = new Paint(Paint.ANTI_ALIAS_FLAG);

            float t = y + p.ascent() - u * 1.1f;

            float b = y + p.descent()
                    + u * (comparison.isEmpty() ? .6f : 1.6f);

            shape.setColor(
                    dark ? 0xff123c60 : 0xffeaf3ff
            );

            c.drawRoundRect(
                    new RectF(x, t, x + w, b),
                    u * .35f,
                    u * .35f,
                    shape
            );

            Paint label = new Paint(p);

            label.setTextSize(u * .72f);

            String caption = thai
                    ? "แบตเมื่อถึง"
                    : "Arrival battery";

            if (label.measureText(caption) > w - u * .4f) {
                label.setTextSize(
                        label.getTextSize()
                                * (w - u * .4f)
                                / label.measureText(caption)
                );
            }

            c.drawText(
                    caption,
                    x + (w - label.measureText(caption)) / 2f,
                    t + u * .85f,
                    label
            );

            c.drawText(
                    text,
                    start,
                    end,
                    x + (w - p.measureText(text, start, end)) / 2f,
                    y,
                    p
            );

            if (!comparison.isEmpty()) {
                label.setTextSize(u * .7f);

                c.drawText(
                        comparison,
                        x + (w - label.measureText(comparison)) / 2f,
                        b - u * .65f,
                        label
                );
            }

            float l = x + u * .4f;
            float r = x + w - u * .4f;
            float bt = b - u * .2f;
            float h = u * .35f;

            shape.setColor(
                    dark ? 0xff365c7c : 0xffb9cce3
            );

            c.drawRoundRect(
                    new RectF(l, bt - h, r, bt),
                    h / 2,
                    h / 2,
                    shape
            );

            shape.setColor(
                    value < 20
                            ? 0xffef4444
                            : value < 35
                            ? 0xffd97706
                            : 0xff16c832
            );

            if (value > 0) {
                c.drawRoundRect(
                        new RectF(
                                l,
                                bt - h,
                                l + (r - l)
                                        * NatSignedSoc.bar(value) / 100f,
                                bt
                        ),
                        h / 2,
                        h / 2,
                        shape
                );
            }
        }
    }

    /**
     * Entry point called by the injected APK hook.
     */
    public static void install(Activity activity) {

        if (activity == null
                || INSTALLED.put(activity, true) != null) {
            return;
        }

        ReportLog.i(
                "NatArrivalUi",
                "Inline navigation battery hook installed"
        );

        WeakReference<Activity> ref =
                new WeakReference<>(activity);

        MAIN.postDelayed(new Runnable() {

            @Override
            public void run() {

                Activity a = ref.get();

                if (a == null
                        || a.isDestroyed()
                        || a.isFinishing()) {
                    return;
                }

                try {
                    View root = a.getWindow().peekDecorView();

                    if (root != null
                            && a.hasWindowFocus()
                            && SafeMode.on(
                            a,
                            SafeMode.BYD_BATTERY
                    )) {

                        attach(root);
                        requestRefresh(root);

                    } else if (root != null) {

                        clearCards(root);
                    }

                } catch (RuntimeException error) {

                    View root = a.getWindow().peekDecorView();

                    if (root != null) {
                        clearCards(root);
                    }

                    ReportLog.w(
                            "NatArrivalUi",
                            "Scan failed",
                            error
                    );
                }

                MAIN.postDelayed(
                        this,
                        a.hasWindowFocus() ? 4000 : 10000
                );
            }

        }, 1000);
    }

    private static void clearCards(View root) {
        NatNavigationCard.clear(root);
        NatDedicatedFrame.clear(root);
    }

    /**
     * Refresh native battery text instead of creating
     * an independent battery card.
     */
    private static void requestRefresh(View root) {

        if (QUEUED.put(root, true) != null) {
            return;
        }

        WeakReference<View> ref = new WeakReference<>(root);

        MAIN.postDelayed(() -> {

            View v = ref.get();

            if (v == null) {
                return;
            }

            QUEUED.remove(v);

            if (!v.isAttachedToWindow()
                    || !v.hasWindowFocus()
                    || !SafeMode.on(
                    v.getContext(),
                    SafeMode.BYD_BATTERY
            )) {

                clearCards(v);
                return;
            }

            try {
                // Do not create overlay or dedicated cards.
                clearCards(v);

                List<TextView> matches = new ArrayList<>();

                scan(v, 0, matches);

                if (matches.isEmpty()) {
                    collectSplitSummary(v, 0, matches);
                }

                for (TextView tv : matches) {

                    if (isNavigationBattery(tv)) {
                        styleNavigation(tv);
                    }
                }

            } catch (RuntimeException error) {

                clearCards(v);

                ReportLog.w(
                        "NatArrivalUi",
                        "Inline battery refresh failed",
                        error
                );
            }

        }, 250);
    }

    private static void attach(View root) {

        if (ROOTS.put(root, true) != null) {
            return;
        }

        WeakReference<View> ref = new WeakReference<>(root);

        root.getViewTreeObserver()
                .addOnGlobalLayoutListener(() -> {

                    View v = ref.get();

                    if (v != null && v.hasWindowFocus()) {
                        requestRefresh(v);
                    }
                });

        root.getViewTreeObserver()
                .addOnWindowFocusChangeListener(focused -> {

                    View v = ref.get();

                    if (v == null) {
                        return;
                    }

                    if (focused) {
                        requestRefresh(v);
                    } else {
                        clearCards(v);
                    }
                });
    }

    private static void watch(TextView tv) {

        if (WATCHED.put(tv, true) != null) {
            return;
        }

        WeakReference<TextView> ref =
                new WeakReference<>(tv);

        tv.addTextChangedListener(new TextWatcher() {

            @Override
            public void beforeTextChanged(
                    CharSequence s,
                    int start,
                    int count,
                    int after
            ) {
            }

            @Override
            public void onTextChanged(
                    CharSequence s,
                    int start,
                    int before,
                    int count
            ) {
                TextView v = ref.get();

                if (v == null || !v.hasWindowFocus()) {
                    return;
                }

                requestRefresh(v.getRootView());
            }

            @Override
            public void afterTextChanged(Editable e) {
            }
        });
    }

    private static boolean routeSummary(CharSequence text) {

        return text != null
                && DISTANCE.matcher(text).find()
                && TIME.matcher(text).find();
    }

    private static void collect(
            View view,
            StringBuilder out,
            int depth
    ) {

        if (depth > 5
                || view.getVisibility() != View.VISIBLE
                || out.length() > 400) {
            return;
        }

        if (view instanceof TextView) {

            out.append(
                    ((TextView) view).getText()
            ).append(' ');

        } else if (view instanceof ViewGroup) {

            ViewGroup group = (ViewGroup) view;

            for (int i = 0; i < group.getChildCount(); i++) {
                collect(
                        group.getChildAt(i),
                        out,
                        depth + 1
                );
            }
        }
    }

    private static boolean nearbyRoute(TextView tv) {

        android.view.ViewParent parent = tv.getParent();

        for (
                int i = 0;
                i < 2 && parent instanceof ViewGroup;
                i++, parent = parent.getParent()
        ) {

            StringBuilder out = new StringBuilder();

            collect((View) parent, out, 0);

            if (out.length() > 400) {
                continue;
            }

            String text = NatNavigationSummary.normalize(out);

            if (!routeSummary(text)) {
                continue;
            }

            Matcher m = PERCENT.matcher(text);

            if (m.find() && !m.find()) {
                return true;
            }
        }

        return false;
    }

    private static boolean isNavigationBattery(TextView tv) {

        CharSequence text = tv.getText();

        if (text == null) {
            return false;
        }

        if (NatNavigationSummary.localSoc(text) != null) {
            return true;
        }

        String normalized =
                NatNavigationSummary.normalize(text);

        return EXACT_PERCENT.matcher(normalized).matches()
                && NatSignedSoc.parse(normalized) != null
                && nearbyRoute(tv);
    }

    /**
     * Detect navigation summaries split across TextViews.
     */
    private static void collectSplitSummary(
            View view,
            int depth,
            List<TextView> matches
    ) {

        if (depth > 40 || !view.isShown()) {
            return;
        }

        if (view instanceof ViewGroup) {

            ViewGroup group = (ViewGroup) view;

            for (int i = 0; i < group.getChildCount(); i++) {

                collectSplitSummary(
                        group.getChildAt(i),
                        depth + 1,
                        matches
                );
            }

            return;
        }

        if (!(view instanceof TextView)
                || view instanceof android.widget.EditText) {
            return;
        }

        TextView tv = (TextView) view;

        if (tv.isTextSelectable()
                || tv.getText() == null
                || NatDedicatedFrame.owns(tv)) {
            return;
        }

        Integer own = NatSignedSoc.parse(
                NatNavigationSummary.normalize(tv.getText())
        );

        if (own == null) {
            own = NatNavigationSummary.localSoc(tv.getText());
        }

        if (own == null) {
            return;
        }

        android.view.ViewParent parent = tv.getParent();

        for (
                int i = 0;
                i < 2 && parent instanceof ViewGroup;
                i++, parent = parent.getParent()
        ) {

            StringBuilder local = new StringBuilder();

            collect((View) parent, local, 0);

            String text =
                    NatNavigationSummary.normalize(local);

            if (text.length() > 180) {
                continue;
            }

            Matcher m = PERCENT.matcher(text);

            if (m.find()
                    && !m.find()
                    && Objects.equals(
                    own,
                    NatNavigationSummary.localSoc(text)
            )) {

                matches.add(tv);
                watch(tv);
                return;
            }
        }
    }

    /**
     * Enlarge only the native battery percentage.
     *
     * Example:
     * 471 km · 04:09 · 16%
     *
     * Only "16%" receives the larger font and bold style.
     */
    private static void styleNavigation(TextView tv) {

        CharSequence text = tv.getText();

        if (text == null || !isNavigationBattery(tv)) {
            return;
        }

        Matcher matcher = PERCENT.matcher(text);

        if (!matcher.find()) {
            return;
        }

        int start = matcher.start(1);
        int end = matcher.end(1);

        Integer value = NatSignedSoc.parse(matcher.group(1));

        // Do not modify an ambiguous percentage.
        if (value == null || matcher.find()) {
            return;
        }

        // Avoid applying the same spans repeatedly.
        if (text instanceof Spanned) {

            Spanned spanned = (Spanned) text;

            BatterySizeSpan[] sizes = spanned.getSpans(
                    0,
                    text.length(),
                    BatterySizeSpan.class
            );

            BatteryBoldSpan[] bolds = spanned.getSpans(
                    0,
                    text.length(),
                    BatteryBoldSpan.class
            );

            if (sizes.length == 1
                    && bolds.length == 1
                    && sizes[0].getSizeChange()
                    == BATTERY_FONT_SCALE
                    && spanned.getSpanStart(sizes[0]) == start
                    && spanned.getSpanEnd(sizes[0]) == end
                    && spanned.getSpanStart(bolds[0]) == start
                    && spanned.getSpanEnd(bolds[0]) == end) {

                return;
            }
        }

        // Preserve existing native text and spans.
        SpannableString styled = new SpannableString(text);

        removeCustomSpans(styled);

        // Enlarge the percentage only.
        styled.setSpan(
                new BatterySizeSpan(BATTERY_FONT_SCALE),
                start,
                end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );

        // Make the percentage bold.
        styled.setSpan(
                new BatteryBoldSpan(),
                start,
                end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );

        tv.setText(styled);

        tv.requestLayout();
        tv.invalidate();

        ReportLog.i(
                "NatArrivalUi",
                "Inline battery percentage styled"
        );
    }

    private static void removeCustomSpans(
            SpannableString styled
    ) {

        int length = styled.length();

        for (NatSize span : styled.getSpans(
                0, length, NatSize.class
        )) {
            styled.removeSpan(span);
        }

        for (BatterySizeSpan span : styled.getSpans(
                0, length, BatterySizeSpan.class
        )) {
            styled.removeSpan(span);
        }

        for (BatteryBoldSpan span : styled.getSpans(
                0, length, BatteryBoldSpan.class
        )) {
            styled.removeSpan(span);
        }
    }

    /**
     * Preserve the route-preview UI.
     */
    private static void stylePreview(TextView tv) {

        CharSequence text = tv.getText();

        if (text == null) {
            return;
        }

        Matcher matcher = ARRIVAL.matcher(text);

        if (!matcher.matches()) {
            return;
        }

        Integer value = NatSignedSoc.parse(matcher.group(1));

        if (value == null) {
            return;
        }

        boolean dark = ColorScheme.nightColors(
                tv.getContext()
        );

        boolean thai = "th".equals(
                tv.getResources()
                        .getConfiguration()
                        .getLocales()
                        .get(0)
                        .getLanguage()
        ) || Pattern.compile(
                "[\\u0E00-\\u0E7F]"
        ).matcher(text).find();

        String comparison = value < 0
                ? (thai ? "ต้องชาร์จ" : "Charge required")
                : "";

        if (text instanceof Spanned) {

            NatSize[] existing = ((Spanned) text).getSpans(
                    0,
                    text.length(),
                    NatSize.class
            );

            if (existing.length == 1
                    && existing[0].value == value
                    && existing[0].dark == dark
                    && existing[0].thai == thai
                    && existing[0].comparison.equals(comparison)) {

                return;
            }
        }

        SpannableString styled = new SpannableString(text);

        removeCustomSpans(styled);

        styled.setSpan(
                new NatSize(value, dark, thai, comparison),
                matcher.start(1),
                matcher.end(1),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );

        tv.setText(styled);
    }

    /**
     * Find relevant TextViews in the original Google Maps UI.
     */
    private static void scan(
            View view,
            int depth,
            List<TextView> matches
    ) {

        if (depth > 40 || !view.isShown()) {
            return;
        }

        if (view instanceof TextView) {

            TextView tv = (TextView) view;

            if (tv instanceof android.widget.EditText
                    || tv.isTextSelectable()
                    || NatDedicatedFrame.owns(tv)) {

                return;
            }

            CharSequence text = tv.getText();

            if (text == null) {
                return;
            }

            // Navigation summary: use native inline formatting.
            if (isNavigationBattery(tv)) {

                matches.add(tv);

                watch(tv);

                return;
            }

            // Watch summaries even before the battery is available.
            if (NatNavigationSummary.hasRoute(text)) {

                watch(tv);

                return;
            }

            // Preserve the existing route-preview card.
            if (ARRIVAL.matcher(text).matches()) {

                watch(tv);

                stylePreview(tv);
            }

        } else if (view instanceof ViewGroup) {

            ViewGroup group = (ViewGroup) view;

            for (int i = 0; i < group.getChildCount(); i++) {

                scan(
                        group.getChildAt(i),
                        depth + 1,
                        matches
                );
            }
        }
    }
}
