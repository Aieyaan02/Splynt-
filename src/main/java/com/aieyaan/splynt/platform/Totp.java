package com.aieyaan.splynt.platform;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.util.Locale;
final class Totp {
 static String code(byte[] secret,long step){
  try{Mac mac=Mac.getInstance("HmacSHA1");mac.init(new SecretKeySpec(secret,"HmacSHA1"));byte[] h=mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());int o=h[h.length-1]&15;
   int n=((h[o]&127)<<24)|((h[o+1]&255)<<16)|((h[o+2]&255)<<8)|(h[o+3]&255);
   return String.format(Locale.ROOT,"%06d",n%1000000);
  }catch(Exception e){throw new IllegalStateException("Authenticator unavailable",e);}
 }
 static String base32(byte[] bytes){String alphabet="ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";StringBuilder out=new StringBuilder();int bits=0,value=0;for(byte b:bytes){value=(value<<8)|(b&255);bits+=8;while(bits>=5){bits-=5;out.append(alphabet.charAt((value>>bits)&31));}}if(bits>0)out.append(alphabet.charAt((value<<(5-bits))&31));return out.toString();}
}
