package app.morphe.extension.mapsautomotive.patches;

import java.net.URLEncoder;
import java.io.UnsupportedEncodingException;

/** Pure validation and fixed-host Maps URI construction. */
final class NatFavoriteRules {
 private NatFavoriteRules(){}
 static final int SLOTS=8;
 static String clean(String input,int max){
  if(input==null)return null;
  String s=input.trim();if(s.isEmpty()||s.length()>max)return null;
  for(int i=0;i<s.length();i++)if(Character.isISOControl(s.charAt(i)))return null;
  return s;
 }
 static int[] order(String saved){
  int[] out=new int[SLOTS];for(int i=0;i<SLOTS;i++)out[i]=i;
  if(saved==null)return out;String[] parts=saved.split(",",-1);if(parts.length!=SLOTS)return out;
  boolean[] seen=new boolean[SLOTS];int[] parsed=new int[SLOTS];
  try{for(int i=0;i<SLOTS;i++){int v=Integer.parseInt(parts[i]);if(v<0||v>=SLOTS||seen[v])return out;seen[v]=true;parsed[i]=v;}}catch(NumberFormatException ex){return out;}return parsed;
 }
 static double roundTrip(double current,double arrival){
  return Double.isFinite(current)&&Double.isFinite(arrival)&&current>=0&&current<=100&&arrival<=current&&arrival>=-100?2*arrival-current:Double.NaN;
 }
 static String name(String s){return clean(s,48);}
 static String destination(String s){return clean(s,256);}
 private static String encode(String s){
  try{return URLEncoder.encode(s,"UTF-8").replace("+","%20");}
  catch(UnsupportedEncodingException impossible){throw new IllegalStateException(impossible);}
 }
 static String directions(String input){
  String d=destination(input);
  return d==null?null:"https://www.google.com/maps/dir/?api=1&travelmode=driving&destination="+encode(d);
 }
 static String search(String input){
  String d=destination(input);return d==null?null:"geo:0,0?q="+encode(d);
 }
}
