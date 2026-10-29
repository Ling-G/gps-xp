package io.github.gpsxp;

import android.app.Activity;
import android.os.Bundle;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.*;

public final class MainActivity extends Activity {
    private EditText longitude, latitude;
    private Switch enabled;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad * 2, pad, pad);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(box);
        setContentView(scroll);
        TextView title = new TextView(this);
        title.setText("GPS XP · 固定定位"); title.setTextSize(26); box.addView(title);
        TextView instructions = new TextView(this);
        instructions.setText("在 LSPosed 中启用模块并勾选目标应用。保存后，请彻底结束并重新打开目标应用。\n坐标按经度、纬度填写，直接使用输入值，不转换坐标系。\n");
        box.addView(instructions);
        enabled = new Switch(this); enabled.setText("启用模拟定位"); box.addView(enabled);
        longitude = field(box, "经度（-180～180）");
        latitude = field(box, "纬度（-90～90）");
        Coordinates c = SettingsProvider.read(this);
        enabled.setChecked(c.enabled);
        longitude.setText(Double.toString(c.longitude)); latitude.setText(Double.toString(c.latitude));
        Button save = new Button(this); save.setText("保存设置"); box.addView(save);
        save.setOnClickListener(v -> save());
        Button reset = new Button(this); reset.setText("恢复默认坐标并保存"); box.addView(reset);
        reset.setOnClickListener(v -> {
            longitude.setText(Double.toString(Coordinates.DEFAULT_LONGITUDE));
            latitude.setText(Double.toString(Coordinates.DEFAULT_LATITUDE)); save();
        });
        TextView note = new TextView(this);
        note.setText("\n默认经度：121.42404117992659\n默认纬度：29.66665962827546\n\n支持 Android LocationManager 常用接口。需授予目标应用定位权限。设置页可以打开，不代表模块已经激活。\n模拟生成的 Location 保留模拟位置标记。独立地图 SDK、基站、Wi-Fi 和 IP 定位不保证生效。");
        box.addView(note);
    }
    private EditText field(LinearLayout box, String label) {
        TextView text = new TextView(this); text.setText(label); box.addView(text);
        EditText edit = new EditText(this); edit.setSingleLine(true);
        edit.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        box.addView(edit, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return edit;
    }
    private void save() {
        try {
            Coordinates c = new Coordinates(enabled.isChecked(), Double.parseDouble(longitude.getText().toString().trim()),
                    Double.parseDouble(latitude.getText().toString().trim()));
            boolean saved = SettingsProvider.prefs(this).edit().putBoolean("enabled", c.enabled)
                    .putString("longitude", Double.toString(c.longitude)).putString("latitude", Double.toString(c.latitude)).commit();
            if (!saved) throw new IllegalStateException("设置保存失败，请重试");
            Toast.makeText(this, "已保存，请重新启动目标应用", Toast.LENGTH_LONG).show();
        } catch (IllegalArgumentException e) {
            Toast.makeText(this, "请输入有效坐标：经度 -180～180，纬度 -90～90", Toast.LENGTH_LONG).show();
        } catch (IllegalStateException e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
