package app.morphe.extension.mapsautomotive.patches;

import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.text.Layout;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;

/** Integrated, non-interactive card anchored to the native navigation summary. Never changes SOC or controls. */
final class NatNavigationCard {
    private static final WeakHashMap<View, Card> CARDS = new WeakHashMap<>();
    static void clear(View root) {
        Card c = CARDS.remove(root);
        if (c != null) {
            root.getOverlay().remove(c);
            restoreNative(c);
        }
    }

    // Retain original characters for live SOC parsing; preserve layout width.
    private static final class HiddenSoc extends android.text.style.ReplacementSpan {
        final int width;
        HiddenSoc(int width) { this.width = width; }
        @Override public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
            return width;
        }
        @Override public void draw(Canvas canvas, CharSequence text, int start, int end, float x,
                                   int top, int y, int bottom, Paint paint) { }
    }
    private static void restoreNative(Card c) {
        TextView tv = c.source == null ? null : c.source.get();
        HiddenSoc hidden = c.hidden;
        android.text.Spanned original = c.original;
        c.hidden = null;
        c.original = null;
        if (tv == null || hidden == null) return;
        CharSequence current = tv.getText();
        if (!(current instanceof android.text.Spanned)) return;
        android.text.Spanned sp = (android.text.Spanned) current;
        if (sp.getSpanStart(hidden) < 0) return;
        android.text.SpannableString restored = new android.text.SpannableString(current);
        restored.removeSpan(hidden);
        if (original != null && current.toString().equals(original.toString())) {
            for (android.text.style.ReplacementSpan span : original.getSpans(0, original.length(), android.text.style.ReplacementSpan.class)) {
                int a = original.getSpanStart(span), b = original.getSpanEnd(span);
                if (a >= 0 && b <= restored.length() && restored.getSpanStart(span) < 0)
                    restored.setSpan(span, a, b, original.getSpanFlags(span));
            }
        }
        tv.setText(restored);
    }
    private static void hideNative(Card c, TextView tv, int start, int width) {
        CharSequence current = tv.getText();
        if (c.hidden != null && c.source != null && c.source.get() == tv
                && current instanceof android.text.Spanned
                && ((android.text.Spanned) current).getSpanStart(c.hidden) == start
                && ((android.text.Spanned) current).getSpanEnd(c.hidden) == current.length()) return;
        restoreNative(c);
        current = tv.getText();
        if (start < 0 || start >= current.length() || width <= 0) return;
        android.text.SpannableString styled = new android.text.SpannableString(current);
        for (android.text.style.ReplacementSpan span : styled.getSpans(start, styled.length(), android.text.style.ReplacementSpan.class)) {
            if (styled.getSpanStart(span) < start || styled.getSpanEnd(span) > styled.length()) return;
            styled.removeSpan(span);
        }
        c.original = new android.text.SpannableString(current);
        c.hidden = new HiddenSoc(width);
        styled.setSpan(c.hidden, start, styled.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        tv.setText(styled);
    }
    static boolean owns(TextView tv) {
        Card c=CARDS.get(tv.getRootView());
        return c!=null && c.source!=null && c.source.get()==tv;
    }
    private static final WeakHashMap<View, String> STATUS = new WeakHashMap<>();
    private static void status(View root, String message) {
        if (!message.equals(STATUS.put(root,message))) ReportLog.i("NatArrivalUi", "Integrated frame: " + message);
    }
    private static boolean visibleText(View v) {
        return v instanceof TextView && !(v instanceof android.widget.EditText)
            && !((TextView)v).isTextSelectable() && v.isShown() && v.getGlobalVisibleRect(new Rect());
    }
    private static void collect(View v,List<TextView> text,int depth) {
        if(depth>40 || !v.isShown() || !v.getGlobalVisibleRect(new Rect())) return;
        if(visibleText(v)) { if(!NatNavigationSummary.normalize(((TextView)v).getText()).isEmpty()) text.add((TextView)v); }
        else if(v instanceof ViewGroup) {
            ViewGroup g=(ViewGroup)v;
            for(int i=0;i<g.getChildCount();i++) collect(g.getChildAt(i),text,depth+1);
        }
    }
    // Resource names observed in the supplied APK. They are hints, not a verified navigation state hook.
    private static boolean panel(View v) {
        try {
            if(v.getId()==View.NO_ID) return false;
            String name=v.getResources().getResourceEntryName(v.getId());
            return name.equals("status_panel_visible_content") || name.equals("status_panel_single_row_center_view")
                || name.equals("status_panel_single_row_start_view");
        } catch(android.content.res.Resources.NotFoundException e) { return false; }
    }
    private static final class Match {
        final TextView anchor; final int soc; final String kind; final View panel;
        Match(TextView anchor,int soc,String kind){this.anchor=anchor;this.soc=soc;this.kind=kind;this.panel=summaryPanel(anchor);}
    }
    static boolean isNavigationSource(TextView v) {
        return summaryPanel(v)!=null && (NatNavigationSummary.parse(v.getText())!=null || NatNavigationSummary.parseSocPart(v.getText())!=null);
    }
    private static View summaryPanel(View v) {
        View best=null;
        for(android.view.ViewParent parent=v.getParent(); parent instanceof ViewGroup; parent=parent.getParent()) {
            View candidate=(View)parent;
            if(panel(candidate)) best=candidate;
        }
        return best;
    }
    private static List<Match> matches(View root) {
        ArrayList<TextView> all=new ArrayList<>(); collect(root,all,0);
        ArrayList<Match> result=new ArrayList<>();
        for(TextView tv:all) {
            Integer n=NatNavigationSummary.parse(tv.getText());
            if(n!=null) {
                Match m=new Match(tv,n,"single");
                if(m.panel!=null || NatNavigationSummary.parseLegacy(tv.getText())!=null) result.add(m);
                continue;
            }
            Integer part=NatNavigationSummary.parseSocPart(tv.getText());
            if(part==null) continue;
            // Unlabelled standalone SOC must be in a known status-panel subtree with one complete summary.
            View nativePanel=summaryPanel(tv);
            android.view.ViewParent parent=tv.getParent();
            for(int depth=0;depth<12 && parent instanceof ViewGroup;depth++,parent=parent.getParent()) {
                View group=(View)parent;
                if(!panel(group)) {
                    // The nearest compact row inside a named status panel may itself have an obfuscated ID.
                    boolean insideKnown=nativePanel!=null && ancestor(nativePanel,group);
                    if(!insideKnown && (!NatNavigationSummary.isExplicitSoc(tv.getText()) || depth>1)) continue;
                    float density=group.getResources().getDisplayMetrics().density;
                    if(group.getHeight()>200*density || group.getWidth()>900*density) continue;
                }
                ArrayList<TextView> nearby=new ArrayList<>(); collect(group,nearby,0);
                if(nearby.size()<2 || nearby.size()>6) continue;
                ArrayList<String> pieces=new ArrayList<>();
                for(TextView t:nearby) pieces.add(NatNavigationSummary.normalize(t.getText()));
                Integer combined=NatNavigationSummary.parsePanelParts(pieces);
                if(combined!=null && combined.equals(part)) { result.add(new Match(tv,part,"split")); break; }
                if(group==nativePanel) break;
            }
        }
        boolean known=false;
        for(Match m:result) if(m.panel!=null) known=true;
        if(known) result.removeIf(m->m.panel==null);
        return result;
    }
    // Measure the actual rendered text, not the width of a match-parent TextView.
    // Layout selection includes ReplacementSpan/emoji widths. Unknown layout fails closed.
    private static Rect textRange(TextView tv,int start,int end) {
        Layout l=tv.getLayout(); Rect visible=new Rect();
        if(l==null || !tv.getGlobalVisibleRect(visible) || start<0 || end>l.getText().length() || start>=end) return null;
        Path path=new Path(); l.getSelectionPath(start,end,path); RectF f=new RectF(); path.computeBounds(f,true);
        if(f.isEmpty()) return null;
        int[] location=new int[2]; tv.getLocationOnScreen(location);
        // Global visible rect uses root coordinates; convert screen location using the root's screen origin.
        View root=tv.getRootView(); int[] screen=new int[2]; root.getLocationOnScreen(screen);
        Rect rootVisible=new Rect(); Point rootOffset=new Point();
        if(!root.getGlobalVisibleRect(rootVisible,rootOffset)) return null;
        int x=location[0]-screen[0]+rootOffset.x+tv.getTotalPaddingLeft()-tv.getScrollX();
        int y=location[1]-screen[1]+rootOffset.y+tv.getTotalPaddingTop()-tv.getScrollY();
        Rect r=new Rect((int)Math.floor(f.left)+x,(int)Math.floor(f.top)+y,(int)Math.ceil(f.right)+x,(int)Math.ceil(f.bottom)+y);
        return r.intersect(visible)?r:null;
    }
    private static final Pattern TAIL=Pattern.compile("(?iu)(?:Arrival battery|Arrive with|Battery on arrival|แบตเมื่อถึง(?:ปลายทาง)?|แบตเตอรี่เมื่อถึง(?:ปลายทาง)?|ไปถึงโดยเหลือ)?\\s*[:：]?\\s*(?:\\uFFFC|🔋)?\\s*("+NatSignedSoc.TOKEN+")(?:\\s|\\uFFFC|🔋)*$");
    private static Rect[] sourceRegions(Match m) {
        TextView tv=m.anchor; CharSequence t=tv.getText();
        Matcher suffix=TAIL.matcher(t.toString().replace('\u00a0',' ').replace('\u202f',' ').replaceAll("[\\u200e\\u200f\\u202a-\\u202e\\u2066-\\u2069]", " "));
        if(!suffix.find()) return null;
        int start=suffix.start();
        while (start>0 && Character.isWhitespace(t.charAt(start-1))) start--;
        if (start>0 && "·•・|".indexOf(t.charAt(start-1))>=0) start--;
        // Preserve ETA, distance, and every unrelated glyph.
        Rect soc=textRange(tv,start,t.length());
        Rect prefix=start==0?null:textRange(tv,0,start);
        if(soc==null || (start>0 && prefix==null)) return null;
        return new Rect[]{soc,prefix};
    }
    private static boolean hasDrawable(TextView tv) {
        for(Drawable d:tv.getCompoundDrawables()) if(d!=null) return true;
        return false;
    }
    private static boolean ancestor(View v,View child) {
        for(android.view.ViewParent p=child.getParent();p instanceof View;p=p.getParent()) if(p==v) return true;
        return false;
    }
    // The only native content we may visually replace is the complete, verified SOC text.
    // Never intercept touches; protect other text and controls even inside the status panel.
    private static boolean blocked(View v, Rect area, Match match, Rect[] source,int depth) {
        if(depth>40 || !v.isShown()) return false;
        Rect r=new Rect();
        if(!v.getGlobalVisibleRect(r) || !Rect.intersects(r,area)) return false;
        if(v==match.anchor && source!=null && !v.isClickable() && !v.isLongClickable() && !hasDrawable(match.anchor)) {
            if(source[1]!=null && Rect.intersects(source[1],area)) return true;
            // The verified suffix will be hidden before this card is displayed.
            return false;
        }
        if(v instanceof ViewGroup) {
            ViewGroup g=(ViewGroup)v;
            if((g.isClickable() || g.isLongClickable()) && !ancestor(g,match.anchor) && g.getWidth()<=area.width()*3 && g.getHeight()<=area.height()*3) return true;
            for(int i=0;i<g.getChildCount();i++) if(blocked(g.getChildAt(i),area,match,source,depth+1)) return true;
            return false;
        }
        if(v instanceof TextView) {
            TextView tv=(TextView)v;
            if(v.isClickable() || v.isLongClickable() || v.isFocusable() || hasDrawable(tv)) return true;
            if(tv.getText().length()==0) return false;
            Rect text=textRange(tv,0,tv.getText().length());
            return text==null || Rect.intersects(text,area);
        }
        if(v instanceof android.widget.ImageView || v.isClickable() || v.isLongClickable() || v.isFocusable())
            return v.getWidth()<=area.width()*3 && v.getHeight()<=area.height()*3;
        return false;
    }
    private static NatBatteryPlacement.Box box(Rect r){return new NatBatteryPlacement.Box(r.left,r.top,r.width(),r.height());}
    static void refresh(View root) {
        if (!root.isShown() || !root.hasWindowFocus() || !SafeMode.on(root.getContext(), SafeMode.BYD_BATTERY)) { clear(root); status(root,"inactive"); return; }
        List<Match> matches=matches(root);
        if(matches.size()!=1) { clear(root); status(root,matches.isEmpty()?"no verified summary":"ambiguous summaries"); return; }
        Match match=matches.get(0); TextView tv=match.anchor; int soc=match.soc;
        Rect visible=new Rect(), viewport=new Rect(); Point offset=new Point();
        if(!tv.getGlobalVisibleRect(visible) || !root.getGlobalVisibleRect(viewport,offset)) { clear(root); status(root,"not visible"); return; }
        Rect[] regions=sourceRegions(match);
        Rect anchor=regions==null?visible:regions[0];
        Rect panelRect=new Rect();
        boolean inside=match.panel!=null && match.panel.getGlobalVisibleRect(panelRect) && panelRect.intersect(viewport);
        float d=root.getResources().getDisplayMetrics().density;
        int gap=Math.max(1,Math.round(4*d)); Rect area=null; int chosen=0; String placement="";
        Card existing=CARDS.get(root);
        // Keep a single right-aligned card inside the native summary. A verified
        // suffix can share its cell; the distance/ETA prefix and controls cannot.
        if (inside && regions != null && !hasDrawable(tv)) {
            for (int size : new int[]{160,144,128,112}) {
                int w=Math.round(size*d), h=Math.round(size*.50f*d);
                for (NatBatteryPlacement.Box cell : NatBatteryPlacement.rightCell(box(panelRect),box(anchor),regions[1]==null?null:box(regions[1]),w,h,gap)) {
                    Rect candidate=new Rect(cell.x,cell.y,cell.x+cell.w,cell.y+cell.h);
                    if (!blocked(root,candidate,match,regions,0)) {
                        area=candidate; chosen=size; placement="integrated-right"; break;
                    }
                }
                if (area!=null) break;
            }
        }
        if (area==null) {
            clear(root);
            status(root,"no safe right cell; native SOC retained");
            return;
        }
        area.offset(-offset.x,-offset.y);
        boolean thai="th".equals(tv.getResources().getConfiguration().getLocales().get(0).getLanguage()) || tv.getText().toString().matches("(?s).*[\\u0E00-\\u0E7F].*");
        boolean dark=ColorScheme.nightColors(tv.getContext());
        Card c=existing;
        if(c==null) { c=new Card(); CARDS.put(root,c); root.getOverlay().add(c); }
        if(c.source!=null && c.source.get()!=tv) restoreNative(c);
        c.source=new java.lang.ref.WeakReference<>(tv); c.placement=placement;
        // Suppress only a verified suffix once a safe card location is available.
        Matcher suffix=TAIL.matcher(tv.getText().toString().replace('\u00a0',' ').replace('\u202f',' ').replaceAll("[\\u200e\\u200f\\u202a-\\u202e\\u2066-\\u2069]", " "));
        if(regions!=null && !hasDrawable(tv) && suffix.find()) {
            int start=suffix.start();
            CharSequence nativeText=tv.getText();
            while(start>0 && Character.isWhitespace(nativeText.charAt(start-1))) start--;
            if(start>0 && "·•・|".indexOf(nativeText.charAt(start-1))>=0) start--;
            hideNative(c,tv,start,regions[0].width());
        } else restoreNative(c);
        // Fail closed: do not display a duplicate if the native suffix could not be hidden.
        if (c.hidden==null || !(tv.getText() instanceof android.text.Spanned)
                || ((android.text.Spanned)tv.getText()).getSpanStart(c.hidden)<0) {
            clear(root);
            status(root,"native suffix not hidden; native SOC retained");
            return;
        }
        status(root,"visible " + placement + " " + match.kind + " " + chosen + "dp native SOC hidden");
        if(c.soc==soc && c.thai==thai && c.dark==dark && c.getBounds().equals(area)) return;
        c.soc=soc; c.thai=thai; c.dark=dark; c.setBounds(area); c.invalidateSelf();
    }
    private static final class Card extends Drawable {
        int soc; boolean thai,dark; String placement="";
        java.lang.ref.WeakReference<TextView> source;
        HiddenSoc hidden;
        android.text.Spanned original;
        final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        @Override public void draw(Canvas c) {
            Rect b=getBounds(); float u=b.width()/160f;
            int fg=dark?0xfff5f5f5:0xff263238;
            int accent=soc<20?0xffdc4040:soc<35?0xffd97706:0xff179b64;
            p.setStyle(Paint.Style.FILL); p.setColor(dark?0xff172b30:0xfff3f8f6);
            c.drawRoundRect(new RectF(b),12*u,12*u,p);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.2f*u);
            p.setColor(dark?0xff43645d:0xffc3d8d1);
            c.drawRoundRect(new RectF(b.left+u,b.top+u,b.right-u,b.bottom-u),11*u,11*u,p);
            p.setStyle(Paint.Style.FILL);
            p.setTextAlign(Paint.Align.LEFT); p.setTypeface(Typeface.DEFAULT); p.setTextSize(12*u); p.setColor(fg);
            c.drawText(thai?"แบตเมื่อถึง":"Arrival battery",b.left+10*u,b.top+16*u,p);
            p.setColor(soc<0?(dark?0xffff8888:0xffb91c1c):fg);
            p.setTypeface(Typeface.DEFAULT_BOLD); p.setTextSize((soc<0?36:40)*u);
            String number=soc+"%";
            float max=b.width()-58*u;
            if(p.measureText(number)>max) p.setTextSize(p.getTextSize()*max/p.measureText(number));
            c.drawText(number,b.left+10*u,b.top+(soc<0?47:53)*u,p);
            // Large battery pictogram to the right of the number, including negative SOC.
            float il=b.right-43*u, it=b.top+27*u, ir=b.right-10*u, ib=b.top+47*u;
            p.setColor(accent); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2*u);
            c.drawRoundRect(il,it,ir-3*u,ib,4*u,4*u,p);
            p.setStyle(Paint.Style.FILL);
            c.drawRoundRect(ir-2*u,it+6*u,ir,ib-6*u,u,u,p);
            if(soc>0) c.drawRoundRect(il+3*u,it+3*u,il+3*u+(ir-il-9*u)*NatSignedSoc.bar(soc)/100f,ib-3*u,2*u,2*u,p);
            if(soc<0) {
                p.setTypeface(Typeface.DEFAULT); p.setTextSize(12*u);
                p.setColor(dark?0xffff8888:0xffb91c1c);
                c.drawText(thai?"ต้องชาร์จ":"Charge required",b.left+10*u,b.top+69*u,p);
                return;
            }
            float l=b.left+10*u,r=b.right-15*u,t=b.bottom-15*u,bot=b.bottom-7*u;
            p.setColor(dark?0xff485461:0xffb9cbd4); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.2f*u);
            c.drawRoundRect(l,t,r,bot,2*u,2*u,p);
            p.setStyle(Paint.Style.FILL); c.drawRoundRect(r+2*u,t+2*u,r+5*u,bot-2*u,u,u,p);
            int fill=NatSignedSoc.bar(soc); p.setColor(accent);
            if(fill>0) c.drawRoundRect(l+2*u,t+2*u,l+2*u+(r-l-4*u)*fill/100f,bot-2*u,u,u,p);
        }
        @Override public void setAlpha(int a) { }
        @Override public void setColorFilter(ColorFilter f) { }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
