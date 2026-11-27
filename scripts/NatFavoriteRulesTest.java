package app.morphe.extension.mapsautomotive.patches;
public final class NatFavoriteRulesTest {
 static int n;static void check(boolean b){if(!b)throw new AssertionError("case "+(n+1));n++;}
 public static void main(String[] args){
  check(NatFavoriteRules.SLOTS==8);check(NatFavoriteRules.name(" บ้าน ").equals("บ้าน"));
  check(NatFavoriteRules.name("")==null);check(NatFavoriteRules.name("a\nb")==null);
  check(NatFavoriteRules.name("x".repeat(49))==null);check(NatFavoriteRules.destination("x".repeat(257))==null);
  check(NatFavoriteRules.destination(null)==null);check(NatFavoriteRules.directions(" ")==null);
  check(NatFavoriteRules.directions("A & B").equals("https://www.google.com/maps/dir/?api=1&travelmode=driving&destination=A%20%26%20B"));
  check(NatFavoriteRules.directions("x&dir_action=navigate").endsWith("x%26dir_action%3Dnavigate"));
  check(NatFavoriteRules.search("13.7,100.5").equals("geo:0,0?q=13.7%2C100.5"));
  check(NatFavoriteRules.search("ไทย").contains("%E0%B9%84"));
  check(!NatFavoriteRules.directions("Airport").contains("dir_action"));
  System.out.println("PASS "+n+" favorite destination cases");
 }
}
