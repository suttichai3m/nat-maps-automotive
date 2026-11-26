package app.morphe.extension.mapsautomotive.patches;

import android.graphics.Canvas;
import android.graphics.Paint;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Objects;

/** Decoration of the existing native Compose battery row. No separate View or polling. */
public final class NatNativeCard {
    private static final String TAG = "NAT.NativeCard";
    public static final float WIDTH_DP = 220f, HEIGHT_DP = 124f;
    private static volatile Api api;
    private static volatile boolean reported;

    private NatNativeCard() {}

    /** Called with the native semantics modifier and the native Google forecast, never with a guessed SOC. */
    public static Object decorate(Object original, Object forecast) {
        if (original == null || forecast == null) return original;
        try {
            ClassLoader loader = original.getClass().getClassLoader();
            Api a = api;
            if (a == null || a.loader != loader) {
                synchronized (NatNativeCard.class) {
                    a = api;
                    if (a == null || a.loader != loader) api = a = new Api(loader);
                }
            }
            Integer soc = null;
            if (!Boolean.TRUE.equals(a.invalid.invoke(forecast))) {
                Object raw = a.socModel.get(forecast);
                if (raw != null) {
                    int value = a.socValue.getInt(raw);
                    if (value >= -99 && value <= 100) soc = value;
                }
            }
            boolean night = ColorScheme.nativeCardNight();
            // defaultMinSize participates in Compose's own measurement; never overflow the parent's constraints.
            // Draw behind the same measured row. Padding reserves actual space for the bottom bar.
            Object sized = a.minimum.invoke(null, original, WIDTH_DP, HEIGHT_DP, 0);
            Object painter = Proxy.newProxyInstance(loader, new Class<?>[]{a.function}, new Painter(a, soc, night));
            Object decorated = a.drawBehind.invoke(null, sized, painter);
            return a.padding.invoke(null, decorated, 12f, 24f, 12f, 22f);
        } catch (Exception | LinkageError error) {
            report(error);
            return original; // native forecast/icon still available; do not break navigation
        }
    }

    /** The native text renderer accepts a nullable FontWeight; no fabricated value on failure. */
    public static Object boldWeight() {
        Api a = api;
        return a == null ? null : a.boldWeight;
    }

    private static void report(Throwable error) {
        if (!reported) {
            reported = true;
            ReportLog.w(TAG, "Native card decoration unavailable; preserving Maps display", error);
        }
    }

    private static final class Api {
        final ClassLoader loader;
        final Class<?> function;
        final Method minimum, padding, drawBehind, invalid, size, density, round;
        final Field socModel, socValue;
        final Object fill, unit, boldWeight;
        final Method scopeContext, contextCanvas;
        final Field androidCanvas;
        final Class<?> androidCanvasWrapper;

        Api(ClassLoader loader) throws ReflectiveOperationException {
            this.loader = loader;
            Class<?> modifier = type("bmh"), scope = type("caq"), style = type("sp");
            function = type("aofi");
            minimum = method(type("anv"), "f", modifier, float.class, float.class, int.class);
            padding = method(type("qp"), "f", modifier, float.class, float.class, float.class, float.class);
            drawBehind = method(type("qn"), "c", modifier, function);
            Class<?> forecast = type("qut");
            invalid = method(forecast, "b");
            socModel = field(forecast, "b");
            socValue = field(type("qus"), "f");
            size = method(scope, "m");
            density = method(scope, "a");
            round = method(scope, "y", long.class, long.class, long.class, long.class, style, float.class);
            fill = field(type("bsa"), "a").get(null);
            unit = field(type("aobo"), "a").get(null);
            java.lang.reflect.Constructor<?> ctor = type("ckp").getDeclaredConstructor(int.class);
            ctor.setAccessible(true);
            boldWeight = ctor.newInstance(700);
            // Native draw context -> canvas wrapper -> underlying Android Canvas, all checked at patch time.
            scopeContext = method(scope, "o");
            contextCanvas = method(type("brv"), "b");
            androidCanvasWrapper = type("bpf");
            androidCanvas = field(androidCanvasWrapper, "a");
        }

        private Class<?> type(String name) throws ClassNotFoundException { return Class.forName(name, false, loader); }
        private static Method method(Class<?> c, String n, Class<?>... args) throws ReflectiveOperationException {
            Method m = c.getDeclaredMethod(n, args); m.setAccessible(true); return m;
        }
        private static Field field(Class<?> c, String n) throws ReflectiveOperationException {
            Field f = c.getDeclaredField(n); f.setAccessible(true); return f;
        }
    }

    private static final class Painter implements InvocationHandler {
        final Api api;
        final Integer soc;
        final boolean night;
        final String label;
        final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Painter(Api api, Integer soc, boolean night) {
            this.api = api; this.soc = soc; this.night = night;
            this.label = ColorScheme.nativeCardLabel();
            labelPaint.setTextAlign(Paint.Align.CENTER);
        }

        @Override public Object invoke(Object proxy, Method method, Object[] args) {
            switch (method.getName()) {
                case "hashCode": return Objects.hash(soc, night, label);
                case "toString": return "NAT native battery decoration";
                case "equals":
                    Object other = args == null ? null : args[0];
                    if (other == proxy) return true;
                    if (other == null || !Proxy.isProxyClass(other.getClass())) return false;
                    InvocationHandler h = Proxy.getInvocationHandler(other);
                    if (!(h instanceof Painter)) return false;
                    Painter p = (Painter) h;
                    return p.api == api && p.night == night && Objects.equals(p.soc, soc) && Objects.equals(p.label, label);
                case "a":
                    try { if (args != null && args.length == 1 && args[0] != null) paint(args[0]); }
                    catch (Exception | LinkageError error) { report(error); }
                    return api.unit;
                default: return null;
            }
        }

        private void paint(Object scope) throws ReflectiveOperationException {
            long bounds = (Long) api.size.invoke(scope);
            float width = Float.intBitsToFloat((int) (bounds >>> 32));
            float height = Float.intBitsToFloat((int) bounds);
            float d = (Float) api.density.invoke(scope);
            if (!(width > 0 && height > 0 && d > 0) || !Float.isFinite(width) || !Float.isFinite(height) || !Float.isFinite(d)) return;
            float r = Math.min(12f * d, Math.min(width, height) / 2);
            rect(scope, night ? 0xff234b70 : 0xffe4effb, 0, 0, width, height, r);
            float x = 12f * d, h = 7f * d, y = height - 12f * d - h, w = width - 2 * x;
            // At unusually small native constraints don't draw a bar over the content.
            if (w <= 0 || y < 24f * d) return;
            rect(scope, night ? 0xff526c83 : 0xffb6c8d8, x, y, w, h, h / 2);
            if (soc != null && soc > 0) {
                float filled = w * Math.min(100, soc) / 100f;
                rect(scope, soc <= 15 ? 0xffe57373 : (night ? 0xff4ade80 : 0xff16834a),
                        x, y, filled, h, Math.min(h / 2, filled / 2));
            }
            if (height >= 100f * d && width >= 100f * d) drawLabel(scope, width, d);
            // A negative estimate stays negative in Maps' native text; the level bar is empty, not fake charge.
        }

        private void drawLabel(Object scope, float width, float d) {
            // Some renderer backends do not expose an Android Canvas. Only the title is optional;
            // the native percentage, icon and level bar still render through Compose.
            try {
                Object context = api.scopeContext.invoke(scope);
                if (context == null) return;
                Object wrapper = api.contextCanvas.invoke(context);
                if (!api.androidCanvasWrapper.isInstance(wrapper)) return;
                Object raw = api.androidCanvas.get(wrapper);
                if (!(raw instanceof Canvas)) return;
                labelPaint.setColor(night ? 0xffe4effb : 0xff234b70);
                labelPaint.setTextSize(12f * d);
                float measured = labelPaint.measureText(label);
                if (measured > width - 24f * d && measured > 0)
                    labelPaint.setTextSize(labelPaint.getTextSize() * (width - 24f * d) / measured);
                ((Canvas) raw).drawText(label, width / 2, 18f * d, labelPaint);
            } catch (Exception | LinkageError ignored) { /* native semantics still identify the battery */ }
        }

        private void rect(Object scope, int argb, float x, float y, float w, float h, float r) throws ReflectiveOperationException {
            api.round.invoke(scope, ((long) argb & 0xffffffffL) << 32, pair(x, y), pair(w, h), pair(r, r), api.fill, 1f);
        }
    }

    static long pair(float a, float b) {
        return ((long) Float.floatToRawIntBits(a) << 32) | (Float.floatToRawIntBits(b) & 0xffffffffL);
    }
}
