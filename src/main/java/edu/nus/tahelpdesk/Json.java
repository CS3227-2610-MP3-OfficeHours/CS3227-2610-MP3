package edu.nus.tahelpdesk;
import java.util.*;

/** Minimal JSON parser/writer to keep this starter platform dependency-free. */
final class Json {
  private Json() {}
  @SuppressWarnings("unchecked") static Map<String,Object> object(String value) {
    Object parsed = new Parser(value).read(); if (!(parsed instanceof Map<?,?> map)) throw ApiException.validation("Request body must be a JSON object."); return (Map<String,Object>) map;
  }
  static String string(Map<String,Object> body, String key) { Object value = body.get(key); return value instanceof String text ? text : null; }
  static String write(Object value) {
    if (value == null) return "null"; if (value instanceof String s) return '"' + escape(s) + '"'; if (value instanceof Number || value instanceof Boolean) return value.toString();
    if (value instanceof Map<?,?> map) { List<String> values = new ArrayList<>(); for (var entry : map.entrySet()) values.add(write(String.valueOf(entry.getKey())) + ":" + write(entry.getValue())); return "{" + String.join(",", values) + "}"; }
    if (value instanceof Iterable<?> list) { List<String> values = new ArrayList<>(); for (Object item : list) values.add(write(item)); return "[" + String.join(",", values) + "]"; }
    throw new IllegalArgumentException("Cannot serialize JSON value");
  }
  private static String escape(String s) { return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\b", "\\b").replace("\f", "\\f").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t"); }
  private static final class Parser { final String s; int p; Parser(String s) { this.s = s == null ? "" : s; } Object read() { ws(); Object out = value(); ws(); if (p != s.length()) bad(); return out; }
    Object value() { ws(); if (p >= s.length()) bad(); char c=s.charAt(p); if(c=='{') return obj(); if(c=='[') return array(); if(c=='\"') return str(); if(c=='t'&&take("true")) return true; if(c=='f'&&take("false")) return false; if(c=='n'&&take("null")) return null; if(c=='-'||Character.isDigit(c)) return num(); bad(); return null; }
    Map<String,Object> obj() { Map<String,Object> m=new LinkedHashMap<>(); p++; ws(); if (eat('}')) return m; while(true) { ws(); if(p>=s.length()||s.charAt(p)!='\"') bad(); String key=str(); ws(); if(!eat(':')) bad(); m.put(key,value()); ws(); if(eat('}')) return m; if(!eat(',')) bad(); } }
    List<Object> array() { List<Object> a=new ArrayList<>(); p++; ws(); if(eat(']')) return a; while(true) { a.add(value()); ws(); if(eat(']')) return a; if(!eat(',')) bad(); } }
    String str() { StringBuilder b=new StringBuilder(); p++; while(p<s.length()) { char c=s.charAt(p++); if(c=='\"') return b.toString(); if(c=='\\') { if(p>=s.length()) bad(); char e=s.charAt(p++); switch(e) { case '\"','\\','/' -> b.append(e); case 'b' -> b.append('\b'); case 'f' -> b.append('\f'); case 'n' -> b.append('\n'); case 'r' -> b.append('\r'); case 't' -> b.append('\t'); case 'u' -> { if(p+4>s.length()) bad(); b.append((char)Integer.parseInt(s.substring(p,p+4),16)); p+=4; } default -> bad(); } } else b.append(c); } bad(); return null; }
    Number num() { int start=p; if(eat('-')){} while(p<s.length()&&Character.isDigit(s.charAt(p)))p++; if(p<s.length()&&s.charAt(p)=='.'){p++;while(p<s.length()&&Character.isDigit(s.charAt(p)))p++;} return Double.parseDouble(s.substring(start,p)); }
    boolean eat(char c){if(p<s.length()&&s.charAt(p)==c){p++;return true;}return false;} boolean take(String x){if(s.startsWith(x,p)){p+=x.length();return true;}return false;} void ws(){while(p<s.length()&&Character.isWhitespace(s.charAt(p)))p++;} void bad(){throw ApiException.validation("Malformed JSON request.");}
  }
}
