// app/src/main/java/com/example/testmap/util/DateFmt.java
package com.example.driver_bus_info.util;

import android.text.TextUtils;

/** ISO-8601 "yyyy-MM-dd..." 문자열 기준으로 yy.MM.dd 출력 */
public class DateFmt {
    public static String toYyMd(String iso) {
        if (TextUtils.isEmpty(iso)) return "";
        if (iso.length() >= 10) {
            String y = iso.substring(2, 4);
            String m = iso.substring(5, 7);
            String d = iso.substring(8, 10);
            return y + "." + m + "." + d;
        }
        return iso;
    }
}
