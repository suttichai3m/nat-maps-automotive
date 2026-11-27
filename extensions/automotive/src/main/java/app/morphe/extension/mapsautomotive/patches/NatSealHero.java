package app.morphe.extension.mapsautomotive.patches;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;

/** Static hero, compressed from the approved simulation crop; embedded for patched-dex packaging. */
final class NatSealHero {
 private static final String WEBP =
  "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" +
  "736Whn80HcFtW5WErVrGqgs5p0WFjxz3kn7ToAA=";
 private static Bitmap cached;
 static synchronized Bitmap bitmap(){
  if(cached==null){
   byte[] bytes=Base64.decode(WEBP,Base64.DEFAULT);
   cached=BitmapFactory.decodeByteArray(bytes,0,bytes.length);
  }
  return cached;
 }
}
