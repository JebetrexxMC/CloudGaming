package com.limelight.cloudgaming;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.limelight.PcView;
import com.limelight.computers.ComputerManagerService;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.NvHTTP;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * CloudGaming home screen: one app for the whole stack.
 *  - live host status (from the repo's live-status branch)
 *  - start / stop the GPU host (dispatches the vm-control workflow)
 *  - add the host to Moonlight and open it (streaming is the stock Moonlight engine)
 *  - Wake-on-LAN for a home PC
 */
public class CloudGamingActivity extends Activity {
    private static final String PREFS = "cloudgaming";
    private static final int BG = Color.rgb(11, 14, 20);
    private static final int CARD = Color.rgb(20, 26, 38);
    private static final int ACCENT = Color.rgb(77, 225, 193);
    private static final int MUTED = Color.rgb(150, 160, 178);

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private SharedPreferences prefs;
    private TextView statusText;
    private TextView statusDetail;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        setContentView(buildUi());
        if (prefs.getString("repo", "").isEmpty()) {
            prefs.edit().putString("repo", "JebetrexxMC/CloudGaming").apply();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    @Override
    protected void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
    }

    // ---------- UI ----------

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(28), dp(20), dp(28));
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("CloudGaming");
        title.setTextColor(Color.WHITE);
        title.setTextSize(28);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("Genshin Impact on your own cloud PC. No queue, no timer.");
        sub.setTextColor(MUTED);
        sub.setPadding(0, dp(4), 0, dp(16));
        root.addView(sub);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD);
        bg.setCornerRadius(dp(14));
        card.setBackground(bg);
        statusText = new TextView(this);
        statusText.setTextColor(Color.WHITE);
        statusText.setTextSize(18);
        statusText.setTypeface(Typeface.DEFAULT_BOLD);
        statusText.setText("Checking host...");
        statusDetail = new TextView(this);
        statusDetail.setTextColor(MUTED);
        card.addView(statusText);
        card.addView(statusDetail);
        card.setOnClickListener(v -> refreshStatus());
        root.addView(card);

        root.addView(button("Play Genshin Impact", true, v -> play()));
        root.addView(button("Start host", false, v -> dispatch("start")));
        root.addView(button("Stop host", false, v -> dispatch("stop")));
        root.addView(button("All hosts (Moonlight)", false, v -> openMoonlight()));
        root.addView(button("Wake my PC", false, v -> wake()));
        root.addView(button("Private network (Tailscale)", false, v -> openTailscale()));
        root.addView(button("Setup", false, v -> setup()));
        root.addView(button("Website and console", false,
                v -> openUrl("https://" + owner().toLowerCase() + ".github.io/" + repoName() + "/")));

        TextView foot = new TextView(this);
        foot.setText("Streaming engine: Moonlight (GPL-3.0). The game runs on your cloud PC, so it needs no space on this phone.");
        foot.setTextColor(MUTED);
        foot.setTextSize(12);
        foot.setPadding(0, dp(18), 0, 0);
        root.addView(foot);
        return scroll;
    }

    private Button button(String label, boolean primary, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(16);
        b.setTextColor(primary ? BG : Color.WHITE);
        GradientDrawable d = new GradientDrawable();
        d.setColor(primary ? ACCENT : CARD);
        d.setCornerRadius(dp(12));
        b.setBackground(d);
        b.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52));
        lp.topMargin = dp(10);
        b.setLayoutParams(lp);
        b.setOnClickListener(l);
        return b;
    }

    private void toast(String s) {
        runOnUiThread(() -> Toast.makeText(this, s, Toast.LENGTH_LONG).show());
    }

    // ---------- settings ----------

    private String repo() { return prefs.getString("repo", "JebetrexxMC/CloudGaming").trim(); }
    private String owner() { String r = repo(); int i = r.indexOf('/'); return i > 0 ? r.substring(0, i) : r; }
    private String repoName() { String r = repo(); int i = r.indexOf('/'); return i > 0 ? r.substring(i + 1) : r; }

    private void setup() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        final EditText repo = field(box, "GitHub repo (owner/name)", "repo", false);
        final EditText token = field(box, "GitHub token (Actions: write) for start/stop", "token", true);
        final EditText host = field(box, "Host address (IP, Tailscale or DNS name)", "host", false);
        final EditText hours = field(box, "Auto-stop after hours (0 = never)", "hours", false);
        final EditText mac = field(box, "Wake-on-LAN MAC (home PC)", "mac", false);
        final EditText bcast = field(box, "Wake-on-LAN broadcast or host", "bcast", false);
        if (hours.getText().length() == 0) hours.setText("0");
        if (bcast.getText().length() == 0) bcast.setText("255.255.255.255");
        new AlertDialog.Builder(this)
                .setTitle("Setup")
                .setView(wrap(box))
                .setPositiveButton("Save", (d, w) -> {
                    prefs.edit()
                            .putString("repo", repo.getText().toString().trim())
                            .putString("token", token.getText().toString().trim())
                            .putString("host", host.getText().toString().trim())
                            .putString("hours", hours.getText().toString().trim())
                            .putString("mac", mac.getText().toString().trim())
                            .putString("bcast", bcast.getText().toString().trim())
                            .apply();
                    refreshStatus();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private View wrap(View v) {
        ScrollView s = new ScrollView(this);
        s.addView(v);
        return s;
    }

    private EditText field(LinearLayout parent, String hint, String key, boolean secret) {
        TextView label = new TextView(this);
        label.setText(hint);
        label.setPadding(0, dp(10), 0, 0);
        parent.addView(label);
        EditText e = new EditText(this);
        e.setSingleLine(true);
        e.setText(prefs.getString(key, key.equals("repo") ? "JebetrexxMC/CloudGaming" : ""));
        if (secret) e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        parent.addView(e);
        return e;
    }

    // ---------- status ----------

    private void refreshStatus() {
        statusText.setText("Checking host...");
        io.execute(() -> {
            try {
                String body = http("GET", "https://raw.githubusercontent.com/" + repo()
                        + "/live-status/status.json?t=" + System.currentTimeMillis(), null, null);
                JSONObject h = new JSONObject(body).optJSONObject("host");
                String head, detail;
                if (h == null || !h.optBoolean("configured")) {
                    head = "Host not set up yet";
                    detail = "Add the CG_HOST secret in the repo, or enter a host address in Setup.";
                } else if (h.optBoolean("online")) {
                    head = "Host online";
                    detail = h.isNull("ms") ? "Ready to play." : "Ready to play. Ping from GitHub: " + h.optInt("ms") + " ms";
                } else {
                    head = "Host offline";
                    detail = "Tap Start host, wait 1-2 minutes, then Play.";
                }
                long upd = new JSONObject(body).optLong("updated", 0);
                if (upd > 0) {
                    long mins = Math.max(0, (System.currentTimeMillis() / 1000 - upd) / 60);
                    detail += "\nChecked " + mins + " min ago. Tap to refresh.";
                }
                final String fh = head, fd = detail;
                runOnUiThread(() -> { statusText.setText(fh); statusDetail.setText(fd); });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    statusText.setText("Status unavailable");
                    statusDetail.setText(String.valueOf(e.getMessage()));
                });
            }
        });
    }

    // ---------- start / stop ----------

    private void dispatch(String action) {
        String token = prefs.getString("token", "");
        if (token.isEmpty()) {
            toast("Add a GitHub token in Setup first (fine-grained, this repo, Actions: read and write).");
            setup();
            return;
        }
        String hours = prefs.getString("hours", "0");
        if (hours.isEmpty()) hours = "0";
        final String json = "{\"ref\":\"main\",\"inputs\":{\"action\":\"" + action
                + "\",\"auto_stop_hours\":\"" + hours.replaceAll("[^0-9]", "") + "\"}}";
        toast(action.equals("start") ? "Starting host..." : "Stopping host...");
        io.execute(() -> {
            try {
                http("POST", "https://api.github.com/repos/" + repo()
                        + "/actions/workflows/vm-control.yml/dispatches", json, token);
                toast(action.equals("start")
                        ? "Host is starting. Give it 2-3 minutes, then tap Play."
                        : "Host is stopping. The disk and game files are kept.");
            } catch (Exception e) {
                toast("GitHub said: " + e.getMessage());
            }
        });
    }

    private String http(String method, String url, String body, String token) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(10000);
        c.setReadTimeout(15000);
        c.setRequestProperty("User-Agent", "CloudGaming-Android");
        if (token != null) {
            c.setRequestProperty("Authorization", "Bearer " + token);
            c.setRequestProperty("Accept", "application/vnd.github+json");
        }
        if (body != null) {
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            try (OutputStream o = c.getOutputStream()) {
                o.write(body.getBytes(StandardCharsets.UTF_8));
            }
        }
        int code = c.getResponseCode();
        InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
        String text = "";
        if (in != null) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            in.close();
            text = bo.toString("UTF-8");
        }
        c.disconnect();
        if (code >= 400) throw new Exception("HTTP " + code + " " + text);
        return text;
    }

    // ---------- play ----------

    private void play() {
        final String host = prefs.getString("host", "");
        if (host.isEmpty()) {
            toast("Enter your host address in Setup (the Elastic IP, or the Tailscale name).");
            setup();
            return;
        }
        toast("Connecting to " + host + "...");
        bindService(new Intent(this, ComputerManagerService.class), new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder binder) {
                final ServiceConnection self = this;
                final ComputerManagerService.ComputerManagerBinder b =
                        (ComputerManagerService.ComputerManagerBinder) binder;
                io.execute(() -> {
                    boolean ok = false;
                    try {
                        String h = host;
                        int port = NvHTTP.DEFAULT_HTTP_PORT;
                        int colon = h.lastIndexOf(':');
                        if (colon > 0 && h.indexOf(':') == colon) {
                            port = Integer.parseInt(h.substring(colon + 1));
                            h = h.substring(0, colon);
                        }
                        ComputerDetails d = new ComputerDetails();
                        d.manualAddress = new ComputerDetails.AddressTuple(h, port);
                        ok = b.addComputerBlocking(d);
                    } catch (Exception ignored) {
                    }
                    final boolean fok = ok;
                    runOnUiThread(() -> {
                        try { unbindService(self); } catch (Exception ignored) { }
                        if (!fok) {
                            toast("Host not reachable yet. Is it started, and is your VPN on? Opening Moonlight.");
                        } else {
                            toast("Host added. Tap it, pair with the PIN in the Apollo web UI, then pick Genshin Impact.");
                        }
                        openMoonlight();
                    });
                });
            }

            @Override
            public void onServiceDisconnected(ComponentName name) { }
        }, Context.BIND_AUTO_CREATE);
    }

    private void openMoonlight() {
        startActivity(new Intent(this, PcView.class));
    }

    // ---------- Wake-on-LAN ----------

    private void wake() {
        final String mac = prefs.getString("mac", "").replaceAll("[^0-9A-Fa-f]", "");
        if (mac.length() != 12) {
            toast("Enter your PC's MAC address in Setup, e.g. AA:BB:CC:DD:EE:FF.");
            setup();
            return;
        }
        final String target = prefs.getString("bcast", "255.255.255.255");
        io.execute(() -> {
            try {
                byte[] m = new byte[6];
                for (int i = 0; i < 6; i++) m[i] = (byte) Integer.parseInt(mac.substring(i * 2, i * 2 + 2), 16);
                byte[] pkt = new byte[6 + 16 * 6];
                for (int i = 0; i < 6; i++) pkt[i] = (byte) 0xff;
                for (int i = 6; i < pkt.length; i += 6) System.arraycopy(m, 0, pkt, i, 6);
                InetAddress addr = InetAddress.getByName(target.isEmpty() ? "255.255.255.255" : target);
                try (DatagramSocket s = new DatagramSocket()) {
                    s.setBroadcast(true);
                    for (int port : new int[]{9, 7}) s.send(new DatagramPacket(pkt, pkt.length, addr, port));
                }
                toast("Wake packet sent to " + target + ".");
            } catch (Exception e) {
                toast("Wake failed: " + e.getMessage());
            }
        });
    }

    // ---------- network / links ----------

    private void openTailscale() {
        Intent launch = getPackageManager().getLaunchIntentForPackage("com.tailscale.ipn");
        if (launch != null) {
            startActivity(launch);
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Private network")
                .setMessage("Only needed if your host is private (Tailscale, Headscale). "
                        + "If your host uses a public IP, skip this. Android allows one VPN app at a time, "
                        + "so Tailscale stays a separate app.")
                .setPositiveButton("Get Tailscale", (d, w) -> openUrl("market://details?id=com.tailscale.ipn"))
                .setNegativeButton("Close", null)
                .show();
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException e) {
            if (url.startsWith("market://")) {
                openUrl("https://play.google.com/store/apps/details?id=com.tailscale.ipn");
            } else {
                toast("No app can open " + url);
            }
        }
    }
}
