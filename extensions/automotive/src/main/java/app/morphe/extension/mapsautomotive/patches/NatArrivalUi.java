package app.morphe.extension.mapsautomotive.patches;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.Spanned;
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
    private static final Pattern ARRIVAL = Pattern.compile("(?iu)^\\s*(?:Arrive with|แบตเมื่อถึง|แบตเตอรี่เมื่อถึง(?:ปลายทาง)?|แบตเมื่อไปถึง)\\s*([0-9]{1,3}\\s*[%％])\\s*$");
    private static final WeakHashMap<Activity, Boolean> INSTALLED = new WeakHashMap<>();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final class NatSize extends ReplacementSpan {
        private final int value;
        private final boolean dark;
        NatSize(int value, boolean dark) { this.value = value; this.dark = dark; }
        private Paint numberPaint(Paint original) {
            Paint p = new Paint(original);
            p.setTextSize(original.getTextSize() * 1.65f);
            p.setTypeface(Typeface.create(original.getTypeface(), Typeface.BOLD));
            p.setColor(dark ? 0xfff0f6ff : 0xff163f59);
            return p;
        }
        @Override public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
            float u = paint.getTextSize();
            Paint p = numberPaint(paint);
            if (fm != null) {
                fm.ascent = Math.min(fm.ascent, (int)Math.floor(p.ascent() - u * .3f));
                fm.descent = Math.max(fm.descent, (int)Math.ceil(p.descent() + u * .65f));
                fm.top = Math.min(fm.top, fm.ascent);
                fm.bottom = Math.max(fm.bottom, fm.descent);
            }
            return (int)Math.ceil(Math.max(u * 4f, p.measureText(text, start, end) + u));
        }
        @Override public void draw(Canvas c, CharSequence text, int start, int end, float x, int top, int y, int bottom, Paint paint) {
            float u = paint.getTextSize(), w = getSize(paint, text, start, end, null);
            Paint p = numberPaint(paint), shape = new Paint(Paint.ANTI_ALIAS_FLAG);
            float t = y + p.ascent() - u * .25f, b = y + p.descent() + u * .6f;
            shape.setColor(dark ? 0xff172b40 : 0xffe5f1f7);
            c.drawRoundRect(new RectF(x, t, x+w, b), u*.35f, u*.35f, shape);
            c.drawText(text, start, end, x+(w-p.measureText(text,start,end))/2f, y, p);
            float l=x+u*.4f, r=x+w-u*.4f, bt=b-u*.2f, h=u*.22f;
            shape.setColor(dark ? 0xff34485b : 0xffc9dce5);
            c.drawRoundRect(new RectF(l,bt-h,r,bt),h/2,h/2,shape);
            shape.setColor(value < 20 ? 0xffef4444 : value < 35 ? 0xffd97706 : 0xff179b64);
            if(value > 0) c.drawRoundRect(new RectF(l,bt-h,l+(r-l)*value/100f,bt),h/2,h/2,shape);
        }
    }
    private NatArrivalUi() {}
    public static void install(Activity activity) {
        if (activity == null || INSTALLED.put(activity, true) != null) return;
        WeakReference<Activity> ref = new WeakReference<>(activity);
        MAIN.postDelayed(new Runnable() {
            @Override public void run() {
                Activity a = ref.get();
                if (a == null || a.isDestroyed() || a.isFinishing()) return;
                try {
                    if (a.hasWindowFocus() && SafeMode.on(a, SafeMode.BYD_BATTERY)) {
                        View root = a.getWindow().peekDecorView();
                        if (root != null) scan(root, 0);
                    }
                } catch (RuntimeException ignored) { /* preserve navigation */ }
                MAIN.postDelayed(this, 1000);
            }
        }, 1000);
    }
    private static void scan(View view, int depth) {
        if (depth > 40 || view.getVisibility() != View.VISIBLE) return;
        if (view instanceof TextView) {
            TextView tv = (TextView) view;
            if (tv instanceof android.widget.EditText || tv.isTextSelectable()) return;
            CharSequence text = tv.getText();
            if (text == null) return;
            Matcher m = ARRIVAL.matcher(text);
            if (!m.matches()) return;
            int value = Integer.parseInt(m.group(1).replaceAll("[^0-9]", ""));
            if (value > 100) return;
            boolean dark = (tv.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
            if (text instanceof Spanned) {
                NatSize[] existing = ((Spanned) text).getSpans(0, text.length(), NatSize.class);
                if (existing.length == 1 && existing[0].value == value && existing[0].dark == dark) return;
            }
            SpannableString styled = new SpannableString(text);
            for (NatSize old : styled.getSpans(0, styled.length(), NatSize.class)) styled.removeSpan(old);
            styled.setSpan(new NatSize(value, dark), m.start(1), m.end(1), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            tv.setText(styled);
        } else if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) scan(group.getChildAt(i), depth + 1);
        }
    }
}
