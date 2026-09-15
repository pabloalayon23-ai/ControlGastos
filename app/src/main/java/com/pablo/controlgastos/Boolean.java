package com.pablo.controlgastos;
/** Internal compatibility shim to avoid the jxl.write.Boolean/java.lang.Boolean wildcard ambiguity. */
final class Boolean {
 private Boolean(){}
 static boolean parseBoolean(String value){return java.lang.Boolean.parseBoolean(value);}
}
