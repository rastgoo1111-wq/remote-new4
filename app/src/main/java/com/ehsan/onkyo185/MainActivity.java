package com.ehsan.onkyo185;

import android.app.Activity;
import android.content.*;
import android.hardware.ConsumerIrManager;
import android.os.*;
import android.view.HapticFeedbackConstants;
import android.webkit.*;
import android.widget.Toast;
import android.content.ClipData;
import android.content.ClipboardManager;
import java.util.*;

public class MainActivity extends Activity {
    private ConsumerIrManager ir;
    private SharedPreferences prefs;
    private static final String PREF_DB = "ir_code_memory_v4";
    private static final int MAX_TOTAL_US = 1_800_000;
    private static final double MAX_CARRIER_DEVIATION = 0.05;
    private WebView web;
    private Handler main;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(0xFF080A0D);
        getWindow().setNavigationBarColor(0xFF080A0D);
        main = new Handler(Looper.getMainLooper());
        ir = (ConsumerIrManager) getSystemService(Context.CONSUMER_IR_SERVICE);
        prefs = getSharedPreferences(PREF_DB, MODE_PRIVATE);
        // One-time migration of the previous preference namespace. The JS layer will
        // still downgrade old Memory-3 records to non-active until they are re-tested.
        if (prefs.getString("db", null) == null) {
            SharedPreferences legacy = getSharedPreferences("ir_code_memory_v3", MODE_PRIVATE);
            String oldDb = legacy.getString("db", null);
            if (oldDb != null) prefs.edit().putString("db", oldDb).apply();
        }
        web = new WebView(this);
        web.setBackgroundColor(0xFF080A0D);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            s.setAllowFileAccessFromFileURLs(false);
            s.setAllowUniversalAccessFromFileURLs(false);
        }
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        web.setWebViewClient(new WebViewClient());
        web.addJavascriptInterface(new Bridge(), "AudioIR");
        web.loadUrl("file:///android_asset/index.html");
        setContentView(web);
    }

    private boolean has() { return ir != null && ir.hasIrEmitter(); }

    private int resolve(int wanted) {
        if (!has()) return 0;
        ConsumerIrManager.CarrierFrequencyRange[] rs = ir.getCarrierFrequencies();
        if (rs == null || rs.length == 0) return wanted;
        int best = 0; long diff = Long.MAX_VALUE;
        for (ConsumerIrManager.CarrierFrequencyRange r : rs) {
            int lo = r.getMinFrequency(), hi = r.getMaxFrequency();
            if (wanted >= lo && wanted <= hi) return wanted;
            int c = wanted < lo ? lo : hi;
            long d = Math.abs((long)c - wanted);
            if (d < diff) { diff = d; best = c; }
        }
        if (best <= 0) return 0;
        if (((double)Math.abs((long)best - wanted)) / Math.max(1, wanted) > MAX_CARRIER_DEVIATION) return 0;
        return best;
    }

    private void addByteLsb(ArrayList<Integer> p, int b, int mark, int zeroSpace, int oneSpace) {
        for (int i = 0; i < 8; i++) {
            p.add(mark);
            p.add(((b >> i) & 1) != 0 ? oneSpace : zeroSpace);
        }
    }

    /** NEC-family frame. Used for the project's known Onkyo mappings; not claimed as factory proof. */
    private int[] nec(int device, int subDevice, int command) {
        ArrayList<Integer> p = new ArrayList<>();
        p.add(9000); p.add(4500);
        addByteLsb(p, device & 0xFF, 560, 560, 1690);
        addByteLsb(p, subDevice & 0xFF, 560, 560, 1690);
        addByteLsb(p, command & 0xFF, 560, 560, 1690);
        addByteLsb(p, (~command) & 0xFF, 560, 560, 1690);
        p.add(560);
        return arr(p);
    }

    private int[] necRepeatFrame() {
        return new int[]{560, 2250, 560};
    }

    private int[] sony(int command, int device, int bits, int extended) {
        int n = bits == 12 ? 12 : bits == 20 ? 20 : 15;
        long word;
        if (n == 12) word = (command & 0x7F) | ((device & 0x1F) << 7);
        else if (n == 15) word = (command & 0x7F) | ((device & 0xFFL) << 7);
        else word = (command & 0x7F) | ((device & 0x1FL) << 7) | ((extended & 0xFFL) << 12);
        return sonyRaw(word, n);
    }

    /** Raw Sony SIRC word, LSB first. */
    private int[] sonyRaw(long word, int bits) {
        int n = bits == 12 ? 12 : bits == 20 ? 20 : 15;
        ArrayList<Integer> p = new ArrayList<>();
        p.add(2400); p.add(600);
        for (int i = 0; i < n; i++) {
            p.add(((word >> i) & 1L) != 0 ? 1200 : 600);
            p.add(600);
        }
        long frame = 0; for (int x : p) frame += x;
        p.add((int)Math.max(2500, 45000 - frame));
        return arr(p);
    }

    /** Canonical JVC 16-bit: 8-bit custom/address followed by 8-bit data/command, LSB first. */
    private int[] jvc(int custom, int data) {
        final int mark = 527, zero = 528, one = 1583;
        ArrayList<Integer> p = new ArrayList<>();
        p.add(8440); p.add(4220);
        addByteLsb(p, custom & 0xFF, mark, zero, one);
        addByteLsb(p, data & 0xFF, mark, zero, one);
        p.add(mark);
        long frame = 0; for (int x : p) frame += x;
        p.add((int)Math.max(1000, 46420 - frame));
        return arr(p);
    }

    /**
     * Pioneer 32-bit family. Pioneer is 40 kHz and uses D:8,S:8,F:8,~F:8,
     * with D normally 160..175 and no subdevice in the Pioneer family.
     * The second byte is therefore the complement of D, not an arbitrary subdevice.
     */
    private int[] pioneer32(int device, int command, boolean doubleTransmit) {
        final int mark = 564, zero = 564, one = 1692;
        ArrayList<Integer> p = new ArrayList<>();
        // Canonical Pioneer 32-bit frame. A second identical frame is a
        // repeat/double transmission, not a claim of a compound 64-bit command.
        addPioneerFrame(p, device, command, mark, zero, one, 43992);
        if (doubleTransmit) {
            addPioneerFrame(p, device, command, mark, zero, one, 43992);
        }
        return arr(p);
    }

    private void addPioneerFrame(ArrayList<Integer> p, int device, int command, int mark, int zero, int one, int trailingGap) {
        p.add(9024); p.add(4512);
        addByteLsb(p, device & 0xFF, mark, zero, one);
        addByteLsb(p, (~device) & 0xFF, mark, zero, one);
        addByteLsb(p, command & 0xFF, mark, zero, one);
        addByteLsb(p, (~command) & 0xFF, mark, zero, one);
        p.add(mark);
        p.add(trailingGap);
    }

    private int[] arr(ArrayList<Integer> p) {
        int[] a = new int[p.size()];
        for (int i = 0; i < a.length; i++) a[i] = p.get(i);
        return a;
    }

    private boolean valid(int[] p) {
        if (p == null || p.length == 0 || p.length > 5000) return false;
        long t = 0;
        for (int x : p) {
            if (x <= 0 || x > 1_800_000) return false;
            t += x;
            if (t > MAX_TOTAL_US) return false;
        }
        return true;
    }

    private int[] repeatPattern(int[] frame, int repeatCount, int separatorUs) {
        int n = Math.max(1, Math.min(3, repeatCount));
        if (n == 1) return frame;
        ArrayList<Integer> p = new ArrayList<>();
        for (int r = 0; r < n; r++) {
            if (r > 0) p.add(Math.max(1000, separatorUs));
            for (int x : frame) p.add(x);
        }
        return arr(p);
    }

    private String tx(String proto, int a, int bits, int c, int hz, int extended, int repeat) {
        if (!has()) return "NO_IR";
        int f = resolve(hz);
        if (f <= 0) return "FREQUENCY_UNSUPPORTED";
        int repeats = Math.max(1, Math.min(3, repeat));
        int[] p;
        try {
            if ("NEC".equals(proto) || "ONKYO".equals(proto)) {
                p = nec(a, bits, c);
                // Onkyo/NEC mappings use a complete frame for each requested repeat.
                if (repeats > 1) p = repeatPattern(p, repeats, 40000);
            } else if ("NEC1".equals(proto)) {
                p = nec(a, bits, c);
                // NEC1 hold/repeat uses the short repeat frame, not a second full frame.
                if (repeats > 1) {
                    ArrayList<Integer> q = new ArrayList<>();
                    for (int x : p) q.add(x);
                    int[] rr = necRepeatFrame();
                    for (int r = 1; r < repeats; r++) {
                        q.add(40000); q.add(rr[0]); q.add(rr[1]); q.add(rr[2]);
                    }
                    p = arr(q);
                }
            } else if ("NEC2".equals(proto)) {
                p = repeatPattern(nec(a, bits, c), repeats, 40000);
            } else if ("SIRC".equals(proto) || "SONY".equals(proto)) {
                p = repeatPattern(sony(c, a, bits, extended), repeats, 1000);
            } else if ("SONY_RAW".equals(proto)) {
                long raw = (((long)extended & 0xFFL) << 16) | (((long)a & 0xFFFFL));
                p = repeatPattern(sonyRaw(raw, bits), repeats, 1000);
            } else if ("JVC".equals(proto)) {
                p = repeatPattern(jvc(a, c), repeats, 1000);
            } else if ("PIONEER".equals(proto)) {
                // repeat=2 is a same-frame double transmission only.
                // A true Pioneer compound command may contain two DIFFERENT
                // Pioneer frames; this encoder intentionally does not fabricate
                // the second frame from the first.
                p = pioneer32(a, c, repeats > 1);
            } else {
                return "BAD_PROTOCOL";
            }
            if (!valid(p)) return "INVALID_PATTERN";
            ir.transmit(f, p);
            return "TRANSMITTED@" + f + (f == hz ? "" : " ADJUSTED_FROM_" + hz);
        } catch (Throwable e) {
            return "ERROR:" + e.getClass().getSimpleName();
        }
    }

    private String db() { return prefs.getString("db", "{}"); }
    private void setDb(String j) { prefs.edit().putString("db", j == null ? "{}" : j).apply(); }

    private void share(String text) {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_SUBJECT, "RASTA Universal Audio IR Database");
        i.putExtra(Intent.EXTRA_TEXT, text);
        startActivity(Intent.createChooser(i, "Export IR database"));
    }

    public class Bridge {
        @JavascriptInterface public boolean hasIR() { return has(); }
        @JavascriptInterface public int getCarrierHz() { return resolve(38000); }
        @JavascriptInterface public String[] getCarrierRanges() {
            if (!has()) return new String[0];
            ConsumerIrManager.CarrierFrequencyRange[] rs = ir.getCarrierFrequencies();
            if (rs == null) return new String[0];
            String[] out = new String[rs.length];
            for (int i = 0; i < rs.length; i++) out[i] = rs[i].getMinFrequency() + "-" + rs[i].getMaxFrequency();
            return out;
        }
        @JavascriptInterface public String send(String proto, int a, int bits, int c, int hz, int extended) {
            return tx(proto, a, bits, c, hz, extended, 1);
        }
        @JavascriptInterface public String sendEx(String proto, int a, int bits, int c, int hz, int extended, int repeat) {
            return tx(proto, a, bits, c, hz, extended, Math.max(1, Math.min(2, repeat)));
        }
        @JavascriptInterface public void haptic(int kind) {
            if (web == null) return;
            web.performHapticFeedback(kind == 2 ? HapticFeedbackConstants.LONG_PRESS : HapticFeedbackConstants.KEYBOARD_TAP,
                    HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
        }
        @JavascriptInterface public String loadDb() { return db(); }
        @JavascriptInterface public void saveDb(String json) { setDb(json); }
        @JavascriptInterface public void exportDb(String text) { share(text); }
        @JavascriptInterface public void copyDb(String text) {
            ClipboardManager cm = (ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("RASTA IR Database", text));
            Toast.makeText(MainActivity.this, "حافظه کپی شد", Toast.LENGTH_SHORT).show();
        }
        @JavascriptInterface public void clearDb() { setDb("{}"); }
    }

    @Override protected void onDestroy() {
        if (web != null) web.destroy();
        if (main != null) main.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
