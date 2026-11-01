package app.revanced.android.youtube;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Test fixture (see AndroidManifest.xml), shaped like current YouTube Shorts: screen-high "videos",
 * each in its own reel_player_page_container, inside a scroller whose name says nothing about Shorts.
 */
public class Feed extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        final int pageH = getResources().getDisplayMetrics().heightPixels;
        // Snaps one Short per swipe, like YouTube's pager.
        ScrollView scroller = new ScrollView(this) {
            private float downY;
            @Override public boolean onTouchEvent(MotionEvent e) {
                if (e.getActionMasked() == MotionEvent.ACTION_DOWN) downY = e.getY();
                if (e.getActionMasked() == MotionEvent.ACTION_UP) {
                    int page = Math.round((float) getScrollY() / pageH);
                    float dy = downY - e.getY();
                    int start = Math.round((float) (getScrollY() - (int) dy) / pageH);
                    int target = Math.abs(dy) > pageH / 10f ? start + (dy > 0 ? 1 : -1) : page;
                    smoothScrollTo(0, Math.max(0, target) * pageH);
                    return true;
                }
                return super.onTouchEvent(e);
            }
            @Override public void fling(int velocityY) { /* the snap above decides */ }
        };
        scroller.setId(R.id.watch_while_layout);
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        int h = pageH;
        int[] colors = {0xFF1E3A5F, 0xFF5F1E3A, 0xFF3A5F1E, 0xFF5F4B1E};
        for (int i = 1; i <= 120; i++) {
            TextView t = new TextView(this);
            t.setText("Short " + i);
            t.setTextSize(40);
            t.setTextColor(Color.WHITE);
            t.setGravity(Gravity.CENTER);
            t.setBackgroundColor(colors[i % colors.length]);
            FrameLayout page = new FrameLayout(this);
            page.setId(R.id.reel_player_page_container);
            page.addView(t, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            column.addView(page, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, h));
        }
        scroller.addView(column);
        setContentView(scroller);
    }
}
