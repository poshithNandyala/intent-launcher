package app.revanced.android.youtube;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Test fixture (see AndroidManifest.xml): screen-high "videos" in a scroller named reel_recycler. */
public class Feed extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroller = new ScrollView(this);
        scroller.setId(R.id.reel_recycler);
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        int h = getResources().getDisplayMetrics().heightPixels;
        int[] colors = {0xFF1E3A5F, 0xFF5F1E3A, 0xFF3A5F1E, 0xFF5F4B1E};
        for (int i = 1; i <= 120; i++) {
            TextView t = new TextView(this);
            t.setText("Short " + i);
            t.setTextSize(40);
            t.setTextColor(Color.WHITE);
            t.setGravity(Gravity.CENTER);
            t.setBackgroundColor(colors[i % colors.length]);
            column.addView(t, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, h));
        }
        scroller.addView(column);
        setContentView(scroller);
    }
}
