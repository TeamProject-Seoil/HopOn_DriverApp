package com.example.driver_bus_info.ui;

import android.app.Activity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

public final class Anim {
    private Anim() {}

    private static final long DURATION = 220L;
    private static final DecelerateInterpolator DECEL = new DecelerateInterpolator();
    private static final OvershootInterpolator OVER = new OvershootInterpolator(2f);

    /** 아래에서 위로, 페이드 인 */
    public static void slideFadeIn(View v, float fromY, long delay) {
        if (v == null) return;
        v.setAlpha(0f);
        v.setTranslationY(fromY);
        v.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(delay)
                .setDuration(DURATION)
                .setInterpolator(DECEL)
                .start();
    }

    /** 위에서 아래로, 페이드 인 */
    public static void slideDownIn(View v, float fromY, long delay) {
        if (v == null) return;
        v.setAlpha(0f);
        v.setTranslationY(-fromY);
        v.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(delay)
                .setDuration(DURATION)
                .setInterpolator(DECEL)
                .start();
    }

    /** 클릭 느낌: 살짝 눌렸다 복원 */
    public static void bump(View v) {
        if (v == null) return;
        v.animate().cancel();
        v.animate()
                .scaleX(0.96f).scaleY(0.96f)
                .setDuration(80)
                .withEndAction(() ->
                        v.animate()
                                .scaleX(1f).scaleY(1f)
                                .setDuration(120)
                                .setInterpolator(OVER)
                                .start()
                )
                .start();
    }

    /** 여러 뷰 순차 등장 (아래→위) */
    public static void intro(Activity a, int... ids) {
        long d = 0L;
        for (int id : ids) {
            View v = a.findViewById(id);
            slideFadeIn(v, 24f, d);
            d += 40; // 살짝 스태거
        }
    }

    /** 화면 전환 페이드 */
    public static void fadeTransition(Activity a) {
        a.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }
}
