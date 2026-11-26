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
    private static final Pattern NAV = Pattern.compile("(?iu)^\\s*[0-9]+(?:[.,][0-9]+)?\\s*(?:กม\\.?|km|mi|เมตร|m)\\s*[·•・]\\s*[0-9]{1,2}:[0-9]{2}(?:\\s*(?:น\\.?|AM|PM))?\\s*[·•・]\\s*([+−-]?[0-9]{1,3}\\s*[%％])(?:\\s|\\uFFFC|\\u200E|\\u200F)*$");
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
        private final float maxWidth;
        private final float density;
        NatSize(int value, boolean dark, boolean thai, String comparison, boolean navigation, float maxWidth, float density) { this.density = density; this.maxWidth = maxWidth; this.navigation = navigation; this.value = value; this.dark = dark; this.thai = thai; this.comparison = comparison; }
        private Paint numberPaint(Paint original) {
            Paint p = new Paint(original);
            p.setTextSize(navigation ? Math.max(original.getTextSize(), 50f * density) : original.getTextSize() * 2.0f);
            if (maxWidth > 0) { float measured = p.measureText(value + "%"); if (measured > maxWidth - original.getTextSize()) p.setTextSize(p.getTextSize() * Math.max(.1f, (maxWidth - original.getTextSize()) / measured)); }
            p.setTypeface(Typeface.create(original.getTypeface(), Typeface.BOLD));
            p.setColor(value < 0 ? (dark ? 0xffff8888 : 0xffb91c1c) : (dark ? 0xffffffff : 0xff111111));
            return p;
        }
        @Override public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
            float u = paint.getTextSize();
            if (navigation) {
                if (fm != null) { fm.ascent = Math.min(fm.ascent, -Math.round(92*density)); fm.descent = Math.max(fm.descent, Math.round(32*density)); fm.top = fm.ascent; fm.bottom = fm.descent; }
                return Math.round(220*density);
            }
            Paint p = numberPaint(paint);
            if (fm != null) {
                fm.ascent = Math.min(fm.ascent, (int)Math.floor(p.ascent() - u * 1.15f));
                fm.descent = Math.max(fm.descent, (int)Math.ceil(p.descent() + u * (comparison.isEmpty() ? .65f : 1.65f)));
                fm.top = Math.min(fm.top, fm.ascent);
                fm.bottom = Math.max(fm.bottom, fm.descent);
            }
            return (int)Math.ceil(Math.min(maxWidth > 0 ? maxWidth : Float.MAX_VALUE, Math.max(u * (navigation ? 7.5f : 5.2f), p.measureText(text, start, end) + u)));
        }
        @Override public void draw(Canvas c, CharSequence text, int start, int end, float x, int top, int y, int bottom, Paint paint) {
            if (navigation) {
                float d=density, t=y-92*d, w=220*d;
                Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
                p.setColor(dark?0xff172b40:0xffe5f1f7);
                c.drawRoundRect(new RectF(x,t,x+w,t+124*d),10*d,10*d,p);
                p.setColor(dark?0xffffffff:0xff172b40); p.setTextAlign(Paint.Align.CENTER); p.setTextSize(12*d);
                c.drawText(thai?"แบตตามเส้นทาง":"Route battery",x+w/2,t+20*d,p);
                p.setColor(value<0?0xffef4444:0xff179b64); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2*d);
                c.drawRoundRect(new RectF(x+12*d,t+40*d,x+44*d,t+65*d),3*d,3*d,p);
                p.setStyle(Paint.Style.FILL); c.drawRect(x+44*d,t+47*d,x+48*d,t+58*d,p);
                if(value>0)c.drawRect(x+15*d,t+43*d,x+(15+26*NatSignedSoc.bar(value)/100f)*d,t+62*d,p);
                p.setColor(value<0?(dark?0xffff8888:0xffb91c1c):(dark?0xffffffff:0xff172b40));
                p.setTypeface(Typeface.DEFAULT_BOLD); p.setTextSize(50*d);
                c.drawText(value+"%",x+130*d,t+79*d,p);
                p.setTypeface(Typeface.DEFAULT); p.setTextSize(11*d);
                if(value<0)c.drawText(thai?"ต้องชาร์จ":"Charge required",x+w/2,t+94*d,p);
                p.setColor(dark?0xff485461:0xffc2ced8); c.drawRoundRect(new RectF(x+12*d,t+106*d,x+208*d,t+116*d),3*d,3*d,p);
                if(value>0){p.setColor(0xff179b64);c.drawRoundRect(new RectF(x+12*d,t+106*d,x+(12+196*NatSignedSoc.bar(value)/100f)*d,t+116*d),3*d,3*d,p);}
                return;
            }
            float u = paint.getTextSize(), w = getSize(paint, text, start, end, null);
            Paint p = numberPaint(paint), shape = new Paint(Paint.ANTI_ALIAS_FLAG);
            float t = y + p.ascent() - u * 1.1f, b = y + p.descent() + u * (comparison.isEmpty() ? .6f : 1.6f);
            shape.setColor(dark ? 0xff123c60 : 0xffeaf3ff);
            c.drawRoundRect(new RectF(x, t, x+w, b), u*.35f, u*.35f, shape);
            Paint label = new Paint(p);
            label.setTextSize(u * .72f);
            String caption = navigation ? (thai ? "แบตตามเส้นทาง" : "Route battery") : (thai ? "แบตเมื่อถึง" : "Arrival battery");
            if (label.measureText(caption) > w-u*.4f) label.setTextSize(label.getTextSize() * (w-u*.4f)/label.measureText(caption));
            c.drawText(caption, x+(w-label.measureText(caption))/2f, t+u*.85f, label);
            c.drawText(text, start, end, x+(w-p.measureText(text,start,end))/2f, y, p);
            if (!comparison.isEmpty()) { label.setTextSize(u*.7f); c.drawText(comparison, x+(w-label.measureText(comparison))/2f, b-u*.65f, label); }
            float l=x+u*.4f, r=x+w-u*.4f, bt=b-u*.2f, h=u*.35f;
            shape.setColor(dark ? 0xff365c7c : 0xffb9cce3);
            c.drawRoundRect(new RectF(l,bt-h,r,bt),h/2,h/2,shape);
            shape.setColor(value < 20 ? 0xffef4444 : value < 35 ? 0xffd97706 : 0xff16c832);
            if(value > 0) c.drawRoundRect(new RectF(l,bt-h,l+(r-l)*NatSignedSoc.bar(value)/100f,bt),h/2,h/2,shape);
        }
    }
    private NatArrivalUi() {}
    private static final WeakHashMap<TextView, Boolean> WATCHED = new WeakHashMap<>();
    public static void install(Activity activity) {
        if (activity == null || INSTALLED.put(activity, true) != null) return;
        ReportLog.i("NatArrivalUi", "V3.1 battery-first hook installed");
        WeakReference<Activity> ref = new WeakReference<>(activity);
        MAIN.postDelayed(new Runnable() {
            @Override public void run() {
                Activity a = ref.get();
                if (a == null || a.isDestroyed() || a.isFinishing()) return;
                try {
                    View root = a.getWindow().peekDecorView();
                    if (root != null && a.hasWindowFocus() && SafeMode.on(a, SafeMode.BYD_BATTERY)) {
                        attach(root, a);
                        requestRefresh(root);
                    } else if (root != null) { NatNavigationCard.clear(root); }
                } catch (RuntimeException error) {
                    View root = a.getWindow().peekDecorView();
                    if (root != null) NatNavigationCard.clear(root);
                    ReportLog.w("NatArrivalUi", "Scan failed", error);
                }
                MAIN.postDelayed(this, a.hasWindowFocus() ? 4000 : 10000);
            }
        }, 1000);
    }
    private static final WeakHashMap<View, Boolean> ROOTS = new WeakHashMap<>();
    private static final WeakHashMap<View, Boolean> QUEUED = new WeakHashMap<>();
    private static void requestRefresh(View root) {
        if (QUEUED.put(root, true) != null) return;
        WeakReference<View> ref = new WeakReference<>(root);
        MAIN.postDelayed(() -> {
            View v = ref.get();
            if (v == null) return;
            QUEUED.remove(v);
            if (!v.isAttachedToWindow() || !v.hasWindowFocus() || !SafeMode.on(v.getContext(), SafeMode.BYD_BATTERY)) {
                NatNavigationCard.clear(v); NatDedicatedFrame.clear(v); return;
            }
            try {
                java.util.ArrayList<TextView> matches = new java.util.ArrayList<>();
                scan(v, 0, matches);
                if (matches.isEmpty()) collectSplitSummary(v, 0, matches);
                NatNavigationCard.clear(v);
                NatDedicatedFrame.refresh(v, matches);
            }
            catch (RuntimeException error) { NatNavigationCard.clear(v); }
        }, 250);
    }
    private static void attach(View root, Activity activity) {
        if (ROOTS.put(root, true) != null) return;
        WeakReference<View> r = new WeakReference<>(root);
        root.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            View v = r.get();
            if (v != null && v.hasWindowFocus()) requestRefresh(v);
        });
        root.getViewTreeObserver().addOnWindowFocusChangeListener(focused -> {
            View v = r.get();
            if (v == null) return;
            if (focused) requestRefresh(v); else { NatNavigationCard.clear(v); NatDedicatedFrame.clear(v); }
        });
    }
    private static void watch(TextView tv) {
        if (WATCHED.put(tv, true) != null) return;
        WeakReference<TextView> ref = new WeakReference<>(tv);
        tv.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                TextView v = ref.get();
                if (v == null || !v.hasWindowFocus()) return;
                View root = v.getRootView();
                if (NatNavigationSummary.hasRoute(s)) {
                    NatNavigationCard.invalidateIfChanged(root, NatNavigationSummary.parse(s));
                } else { NatNavigationCard.invalidateForecast(root); }
                requestRefresh(root);
            }
            public void afterTextChanged(Editable e) { }
        });
    }
    // Conservative fallback: require a unique SOC and distance/ETA in a small local group.
    private static void collectSplitSummary(View view, int depth, java.util.List<TextView> matches) {
        if (depth > 40 || !view.isShown()) return;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i=0;i<group.getChildCount();i++) collectSplitSummary(group.getChildAt(i),depth+1,matches);
        } else if (view instanceof TextView && !(view instanceof android.widget.EditText)) {
            TextView tv=(TextView)view;
            if (tv.getText()==null || NatDedicatedFrame.owns(tv)) return;
            Integer own = NatSignedSoc.parse(NatNavigationSummary.normalize(tv.getText()));
            if (own == null) own = NatNavigationSummary.localSoc(tv.getText());
            if (own == null) return;
            android.view.ViewParent parent=tv.getParent();
            for(int i=0;i<2 && parent instanceof ViewGroup;i++,parent=parent.getParent()) {
                StringBuilder local=new StringBuilder(); collect((View)parent,local,0);
                String text=NatNavigationSummary.normalize(local);
                if(text.length()>180) continue;
                Matcher m=PERCENT.matcher(text);
                if(m.find() && !m.find() && java.util.Objects.equals(own, NatNavigationSummary.localSoc(text))) {
                    matches.add(tv); watch(tv); return;
                }
            }
        }
    }
    // Render inside the native summary rather than searching for an empty overlay rectangle.
    private static void styleNavigation(TextView tv) {
        CharSequence text = tv.getText();
        if (text == null) return;
        Matcher m = PERCENT.matcher(text);
        if (!m.find()) return;
        int start=m.start(1), end=m.end(1);
        Integer value=NatSignedSoc.parse(m.group(1));
        if(value==null || m.find()) return;
        boolean dark=ColorScheme.nightColors(tv.getContext());
        boolean thai="th".equals(tv.getResources().getConfiguration().getLocales().get(0).getLanguage());
        if(text instanceof Spanned) {
            NatSize[] old=((Spanned)text).getSpans(0,text.length(),NatSize.class);
            if(old.length==1 && old[0].navigation && old[0].value==value && old[0].dark==dark) return;
        }
        SpannableString styled=new SpannableString(text);
        for(NatSize old:styled.getSpans(0,styled.length(),NatSize.class)) styled.removeSpan(old);
        styled.setSpan(new NatSize(value,dark,thai,value<0?(thai?"ต้องชาร์จ":"Charge required"):"",true,0,tv.getResources().getDisplayMetrics().density),start,end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        tv.setText(styled);
        tv.requestLayout();
        tv.invalidate();
    }
    private static void scan(View view, int depth, java.util.List<TextView> matches) {
        if (depth > 40 || !view.isShown()) return;
        if (view instanceof TextView) {
            TextView tv = (TextView) view;
            if (tv instanceof android.widget.EditText || tv.isTextSelectable() || NatDedicatedFrame.owns(tv)) return;
            CharSequence text = tv.getText();
            if (text == null) return;
            Matcher nav = NAV.matcher(text.toString().replace("\u00a0", " "));
            boolean navigation = nav.matches();
            // Active navigation is rendered by the independent card, not a shrinking span.
            if (navigation || NatNavigationSummary.hasRoute(text) || NatNavigationSummary.localSoc(text)!=null) {
                if (NatNavigationSummary.hasRoute(text) || NatNavigationSummary.localSoc(text)!=null) matches.add(tv);
                watch(tv); return;
            }
            Matcher m = navigation ? nav : ARRIVAL.matcher(text);
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

            String comparison = ""; // No verified route identity: do not mix independent forecast with native UI.

            boolean thai = "th".equals(tv.getResources().getConfiguration().getLocales().get(0).getLanguage()) || Pattern.compile("[\\u0E00-\\u0E7F]").matcher(text).find();
            if (value < 0) comparison = thai ? "ต้องชาร์จ" : "Charge required";
            boolean dark = ColorScheme.nightColors(tv.getContext());
            // Active navigation uses the native summary span, not a floating overlay.
            if (navigation && NatNavigationSummary.parse(text) == null) return;
            if (text instanceof Spanned) {
                NatSize[] existing = ((Spanned) text).getSpans(0, text.length(), NatSize.class);
                if (existing.length == 1 && existing[0].value == value && existing[0].dark == dark && existing[0].navigation == navigation && existing[0].comparison.equals(comparison)) return;
            }
            float maxWidth = 0;
            if (navigation) {
                int available = tv.getWidth() - tv.getCompoundPaddingLeft() - tv.getCompoundPaddingRight();
                float other = tv.getPaint().measureText(text.subSequence(0, m.start(1)).toString())
                        + tv.getPaint().measureText(text.subSequence(m.end(1), text.length()).toString());
                maxWidth = available - other - 2;
                if (maxWidth < tv.getTextSize() * 3f) return;
            }
            SpannableString styled = new SpannableString(text);
            for (NatSize old : styled.getSpans(0, styled.length(), NatSize.class)) styled.removeSpan(old);
            styled.setSpan(new NatSize(value, dark, thai, comparison, navigation, maxWidth, tv.getResources().getDisplayMetrics().density), m.start(1), m.end(1), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

            tv.setText(styled);

            ReportLog.i("NatArrivalUi", "V3.1 battery-first native span applied");
        } else if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) scan(group.getChildAt(i), depth + 1, matches);
        }
    }
}
