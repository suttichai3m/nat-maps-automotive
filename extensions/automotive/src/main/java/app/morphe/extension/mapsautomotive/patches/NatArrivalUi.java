package app.morphe.extension.mapsautomotive.patches;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.Editable;
import android.view.ViewTreeObserver;
import android.text.style.ReplacementSpan;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.lang.ref.WeakReference;
import java.util.WeakHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Experimental: formats only an existing, explicitly labelled arrival percentage. */
public final class NatArrivalUi {
    private static final Pattern ARRIVAL = Pattern.compile("(?iu)^\\s*(?:Arrive with|Arrival battery|ไปถึงโดยเหลือ|แบตเมื่อถึง|แบตเตอรี่เมื่อถึง(?:ปลายทาง)?|แบตเมื่อไปถึง)\\s*([+−-]?[0-9]{1,3}\\s*[%％])\\s*$");
    // Only accept an unlabelled percentage in a route summary containing distance and time.
    private static final Pattern PERCENT = Pattern.compile("(?<![0-9+−-])([+−-]?[0-9]{1,3}\\s*[%％])");
    private static final Pattern DISTANCE = Pattern.compile("(?iu)[0-9]+(?:[.,][0-9]+)?\\s*(?:กม\\.?|km|mi|เมตร|m\\b)");
    private static final Pattern TIME = Pattern.compile("(?iu)(?:[0-9]{1,2}:[0-9]{2}|[0-9]+\\s*(?:นาที|ชม\\.?|min|hr|hours?))");
    static double routeKm(CharSequence text) {
        Matcher m=Pattern.compile("(?iu)([0-9]+(?:[.,][0-9]+)?)\\s*(?:กม\\.?|km)").matcher(text);
        if(!m.find()) return Double.NaN;
        String number=m.group(1);
        if(m.find()) return Double.NaN;
        try { return Double.parseDouble(number.replace(',','.')); } catch(NumberFormatException e) { return Double.NaN; }
    }
    private static boolean routeSummary(CharSequence text) {
        return DISTANCE.matcher(text).find() && TIME.matcher(text).find();
    }
    private static void collect(View v, StringBuilder out, int depth) {
        if (depth > 5 || v.getVisibility() != View.VISIBLE || out.length() > 400) return;
        if (v instanceof TextView) out.append(((TextView)v).getText()).append(' ');
        else if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup)v;
            for (int i=0; i<g.getChildCount(); i++) collect(g.getChildAt(i), out, depth+1);
        }
    }
    private static boolean nearbyRoute(TextView tv) {
        android.view.ViewParent parent = tv.getParent();
        for (int i=0; i<2 && parent instanceof ViewGroup; i++, parent=parent.getParent()) {
            StringBuilder out = new StringBuilder();
            collect((View)parent, out, 0);
            if (out.length() <= 400 && routeSummary(out)) {
                Matcher m = PERCENT.matcher(out);
                if (m.find() && !m.find()) return true;
            }
        }
        return false;
    }

    private static final WeakHashMap<Activity, Boolean> INSTALLED = new WeakHashMap<>();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final class NatSize extends ReplacementSpan {
        private final int value;
        private final boolean dark;
        private final boolean thai;
        private final String comparison;
        private final boolean navigation;
        NatSize(int value, boolean dark, boolean thai, String comparison, boolean navigation) { this.navigation = navigation; this.value = value; this.dark = dark; this.thai = thai; this.comparison = comparison; }
        private Paint numberPaint(Paint original) {
            Paint p = new Paint(original);
            p.setTextSize(original.getTextSize() * 2.0f);
            p.setTypeface(Typeface.create(original.getTypeface(), Typeface.BOLD));
            p.setColor(value < 0 ? (dark ? 0xffff8888 : 0xffb91c1c) : (dark ? 0xffffffff : 0xff111111));
            return p;
        }
        @Override public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
            float u = paint.getTextSize();
            Paint p = numberPaint(paint);
            if (fm != null) {
                fm.ascent = Math.min(fm.ascent, (int)Math.floor(p.ascent() - u * 1.15f));
                fm.descent = Math.max(fm.descent, (int)Math.ceil(p.descent() + u * (comparison.isEmpty() ? .65f : 1.65f)));
                fm.top = Math.min(fm.top, fm.ascent);
                fm.bottom = Math.max(fm.bottom, fm.descent);
            }
            return (int)Math.ceil(Math.max(u * 5.2f, p.measureText(text, start, end) + u));
        }
        @Override public void draw(Canvas c, CharSequence text, int start, int end, float x, int top, int y, int bottom, Paint paint) {
            float u = paint.getTextSize(), w = getSize(paint, text, start, end, null);
            Paint p = numberPaint(paint), shape = new Paint(Paint.ANTI_ALIAS_FLAG);
            float t = y + p.ascent() - u * 1.1f, b = y + p.descent() + u * (comparison.isEmpty() ? .6f : 1.6f);
            shape.setColor(dark ? 0xff080808 : 0xffffffff);
            c.drawRoundRect(new RectF(x, t, x+w, b), u*.35f, u*.35f, shape);
            Paint label = new Paint(p);
            label.setTextSize(u * .72f);
            String caption = thai ? "แบตเมื่อถึง" : "Arrival battery";
            c.drawText(caption, x+(w-label.measureText(caption))/2f, t+u*.85f, label);
            c.drawText(text, start, end, x+(w-p.measureText(text,start,end))/2f, y, p);
            if (!comparison.isEmpty()) { label.setTextSize(u*.7f); c.drawText(comparison, x+(w-label.measureText(comparison))/2f, b-u*.65f, label); }
            float l=x+u*.4f, r=x+w-u*.4f, bt=b-u*.2f, h=u*.22f;
            shape.setColor(dark ? 0xff353535 : 0xffdddddd);
            c.drawRoundRect(new RectF(l,bt-h,r,bt),h/2,h/2,shape);
            shape.setColor(value < 20 ? 0xffef4444 : value < 35 ? 0xffd97706 : 0xff179b64);
            if(value > 0) c.drawRoundRect(new RectF(l,bt-h,l+(r-l)*NatSignedSoc.bar(value)/100f,bt),h/2,h/2,shape);
        }
    }
    private NatArrivalUi() {}
    private static final WeakHashMap<TextView, Boolean> WATCHED = new WeakHashMap<>();
    public static void install(Activity activity) {
        if (activity == null || INSTALLED.put(activity, true) != null) return;
        ReportLog.i("NatArrivalUi", "V3.1 integrated navigation battery hook installed");
        WeakReference<Activity> ref = new WeakReference<>(activity);
        MAIN.postDelayed(new Runnable() {
            boolean focused;
            @Override public void run() {
                Activity a = ref.get();
                if (a == null || a.isDestroyed() || a.isFinishing()) return;
                try {
                    View root = a.getWindow().peekDecorView();
                    if (root != null && a.hasWindowFocus() && SafeMode.on(a, SafeMode.BYD_BATTERY)) {
                        if(!focused){focused=true;ColorScheme.refreshOnFocus();}
                        attach(root, a);
                        NatNavigationCard.refresh(root);
                        scan(root, 0);
                        try { NatMinimalEv.refresh(a,root); } catch (RuntimeException ignored) { NatMinimalEv.clear(a); }
                    } else { focused=false; if(root!=null){NatNavigationCard.clear(root);NatMinimalEv.clear(a);} }
                } catch (RuntimeException error) {
                    View root = a.getWindow().peekDecorView();
                    if (root != null) NatNavigationCard.clear(root);
                    NatMinimalEv.clear(a);
                    ReportLog.w("NatArrivalUi", "Scan failed", error);
                }
                MAIN.postDelayed(this, 1000);
            }
        }, 1000);
    }
    private static final WeakHashMap<View, Boolean> ROOTS = new WeakHashMap<>();
    static void refreshColors() {
        MAIN.post(() -> {
            for (View root : new java.util.ArrayList<>(ROOTS.keySet())) {
                if (root == null || !root.hasWindowFocus() || !SafeMode.on(root.getContext(), SafeMode.BYD_BATTERY)) continue;
                try { NatNavigationCard.refresh(root); scan(root, 0); }
                catch (RuntimeException error) { NatNavigationCard.clear(root); }
            }
        });
    }
    private static void attach(View root, Activity activity) {
        if (ROOTS.put(root, true) != null) return;
        WeakReference<View> r = new WeakReference<>(root);
        WeakReference<Activity> a = new WeakReference<>(activity);
        root.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            boolean queued;
            @Override public void onGlobalLayout() {
                if (queued) return;
                queued = true;
                MAIN.post(() -> {
                    queued = false;
                    View v = r.get(); Activity owner = a.get();
                    if (v == null || owner == null) return;
                    if (owner.isDestroyed() || !owner.hasWindowFocus() || !SafeMode.on(owner, SafeMode.BYD_BATTERY)) { NatNavigationCard.clear(v); return; }
                    try { NatNavigationCard.refresh(v); } catch (RuntimeException ignored) { NatNavigationCard.clear(v); }
                });
            }
        });
    }
    private static void watch(TextView tv) {
        if (WATCHED.put(tv, true) != null) return;
        WeakReference<TextView> ref = new WeakReference<>(tv);
        tv.addTextChangedListener(new TextWatcher() {
            boolean queued;
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (queued) return;
                queued = true;
                MAIN.post(() -> {
                    queued = false;
                    TextView v = ref.get();
                    if (v == null || !v.isShown() || !v.hasWindowFocus() || !SafeMode.on(v.getContext(), SafeMode.BYD_BATTERY)) return;
                    try {
                        NatNavigationCard.refresh(v.getRootView());
                        scan(v, 0);
                    } catch (RuntimeException ignored) { NatNavigationCard.clear(v.getRootView()); }
                });
            }
            public void afterTextChanged(Editable e) { }
        });
    }
    private static void scan(View view, int depth) {
        if (depth > 40 || view.getVisibility() != View.VISIBLE || NatMinimalEv.owned(view)) return;
        if (view instanceof TextView) {
            TextView tv = (TextView) view;
            if (tv instanceof android.widget.EditText || tv.isTextSelectable()) return;
            CharSequence text = tv.getText();
            if (text == null) return;
            // Native navigation summary stays intact; the separate card owns this case.
            if (NatNavigationCard.owns(tv)) {
                watch(tv);
                // A recycled TextView may still carry the preview-only span.
                if (text instanceof Spanned && ((Spanned) text).getSpans(0, text.length(), NatSize.class).length > 0) {
                    SpannableString nativeText = new SpannableString(text);
                    for (NatSize span : nativeText.getSpans(0, nativeText.length(), NatSize.class)) nativeText.removeSpan(span);
                    tv.setText(nativeText);
                }
                return;
            }
            boolean navigation = NatNavigationCard.isNavigationSource(tv) || NatNavigationSummary.parseLegacy(text) != null;
            if (navigation) watch(tv);
            Matcher m = ARRIVAL.matcher(text);
            if (!m.matches()) {
                m = PERCENT.matcher(text);
                if (!m.find()) return;
                int first = m.start();
                if (m.find()) return; // ambiguous: do not guess which battery value is arrival
                m.reset();
                if (!m.find() || m.start() != first) return;
                if (!routeSummary(text) && !(text.toString().trim().matches("[+−-]?[0-9]{1,3}\\s*[%％]" ) && nearbyRoute(tv))) return;
            }
            Integer parsed = NatSignedSoc.parse(m.group(1));
            if (parsed == null) return;
            int value = parsed;
            watch(tv);
            double km = routeKm(text);
            String comparison = ""; // No verified route identity: do not mix independent forecast with native UI.

            boolean thai = "th".equals(tv.getResources().getConfiguration().getLocales().get(0).getLanguage()) || Pattern.compile("[\\u0E00-\\u0E7F]").matcher(text).find();
            if (value < 0) comparison = thai ? "ต้องชาร์จ" : "Charge required";
            boolean dark = ColorScheme.nightColors(tv.getContext());
            if (text instanceof Spanned) {
                NatSize[] existing = ((Spanned) text).getSpans(0, text.length(), NatSize.class);
                if (existing.length == 1 && existing[0].value == value && existing[0].dark == dark && existing[0].navigation == navigation && existing[0].comparison.equals(comparison)) return;
            }
            SpannableString styled = new SpannableString(text);
            for (NatSize old : styled.getSpans(0, styled.length(), NatSize.class)) styled.removeSpan(old);
            styled.setSpan(new NatSize(value, dark, thai, comparison, navigation), m.start(1), m.end(1), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

            tv.setText(styled);

            ReportLog.i("NatArrivalUi", "V3.1 alpha2.1 arrival span applied");
        } else if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) scan(group.getChildAt(i), depth + 1);
        }
    }
}
