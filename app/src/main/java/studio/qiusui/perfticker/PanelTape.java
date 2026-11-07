package studio.qiusui.perfticker;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

/** 逐笔成交：告警 / 主频跳变 / 新高新低 / 运行统计的事件流水。 */
final class PanelTape {

    private static final int ROWS = 22;

    final LinearLayout root;
    private final ArrayList<TextView> times = new ArrayList<TextView>();
    private final ArrayList<TextView> texts = new ArrayList<TextView>();
    private final TextView countLabel;
    private final SimpleDateFormat fmt = new SimpleDateFormat("HH:mm:ss", Locale.US);

    PanelTape(Context c) {
        root = Ui.col(c);
        Ui.bg(root, Theme.panel, Theme.line, 5);

        LinearLayout head = Ui.row(c);
        head.setPadding(Ui.dp(10), Ui.dp(7), Ui.dp(10), Ui.dp(5));
        TextView t = Ui.text(c, "逐笔成交", 13, Theme.text, true, false);
        TextView en = Ui.text(c, "LIVE TAPE", 8.5f, Theme.textFaint, false, true);
        en.setPadding(Ui.dp(6), Ui.dp(2), 0, 0);
        countLabel = Ui.text(c, "0 笔", 9.5f, Theme.textDim, false, true);
        head.addView(t, Ui.lp(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        head.addView(en, Ui.lp(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        head.addView(new View(c), new LinearLayout.LayoutParams(0, 1, 1));
        head.addView(countLabel, Ui.lp(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(head, Ui.lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(Ui.hline(c, Theme.line));

        for (int i = 0; i < ROWS; i++) {
            LinearLayout row = Ui.row(c);
            row.setPadding(Ui.dp(10), 0, Ui.dp(10), 0);
            TextView time = Ui.text(c, "--:--:--", 9.5f, Theme.textFaint, false, true);
            time.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(52), LinearLayout.LayoutParams.WRAP_CONTENT));
            row.addView(time);
            TextView text = Ui.text(c, "", 10.5f, Theme.textDim, false, false);
            text.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            row.addView(text);
            times.add(time);
            texts.add(text);
            row.setVisibility(View.GONE);
            root.addView(row, Ui.lp(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(22)));
        }
    }

    void refresh(ArrayList<Trade> trades) {
        for (int i = 0; i < ROWS; i++) {
            LinearLayout row = (LinearLayout) texts.get(i).getParent();
            if (i < trades.size()) {
                Trade tr = trades.get(i);
                row.setVisibility(View.VISIBLE);
                times.get(i).setText(fmt.format(new Date(tr.ts)));
                texts.get(i).setText(tr.text);
                texts.get(i).setTextColor(tr.kind == Trade.UP ? Theme.UP : tr.kind == Trade.DOWN ? Theme.DN : Theme.textDim);
            } else {
                row.setVisibility(View.GONE);
            }
        }
        countLabel.setText(trades.size() + " 笔");
    }
}
