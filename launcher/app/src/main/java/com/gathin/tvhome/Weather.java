package com.gathin.tvhome;

import android.app.Activity;
import android.content.res.Resources;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.function.BooleanSupplier;

/**
 * Personal build only: the weather now and the next six hours under the clock, from Open-Meteo (no key).
 * Off unless weather_latitude/weather_longitude are set in config.xml. Fetches only while Home is started:
 * every 30 minutes, or 5 minutes after a failure.
 */
final class Weather {
    static final int HOURS = 6;
    private static final long EVERY = 30 * 60_000L, RETRY = 5 * 60_000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = this::fetch;
    private final View block;
    private final TextView now;
    private final TextView[] temps = new TextView[HOURS], times = new TextView[HOURS];
    private final String url;
    private final BooleanSupplier is24;
    private long lastOk = -EVERY;
    private boolean running, inFlight;

    private Weather(View block, TextView now, LinearLayout hours, String url, BooleanSupplier is24) {
        this.block = block;
        this.now = now;
        this.url = url;
        this.is24 = is24;
        Resources r = block.getResources();
        float dp = r.getDisplayMetrics().density;
        for (int i = 0; i < HOURS; i++) {
            LinearLayout cell = new LinearLayout(block.getContext());
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            temps[i] = text(cell, 14, 0xE6FFFFFF, R.string.font_medium, "--°");
            FrameLayout mid = new FrameLayout(block.getContext());
            View line = new View(block.getContext());
            line.setBackgroundColor(0x40FFFFFF);
            // The line runs dot to dot: it starts at the first cell's centre and stops at the last one's.
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, Math.max(1, Math.round(dp)), Gravity.CENTER_VERTICAL);
            if (i == 0) lp.leftMargin = Math.round(36 * dp);
            if (i == HOURS - 1) lp.rightMargin = Math.round(36 * dp);
            mid.addView(line, lp);
            View dot = new View(block.getContext());
            GradientDrawable d = new GradientDrawable();
            d.setShape(GradientDrawable.OVAL);
            d.setColor(0xCCFFFFFF);
            dot.setBackground(d);
            int s = Math.round(6 * dp);
            mid.addView(dot, new FrameLayout.LayoutParams(s, s, Gravity.CENTER));
            cell.addView(mid, new LinearLayout.LayoutParams(-1, Math.round(10 * dp)));
            times[i] = text(cell, 12, 0x99FFFFFF, R.string.font_regular, "--");
            hours.addView(cell, new LinearLayout.LayoutParams(Math.round(72 * dp), -2));
        }
    }

    private static TextView text(LinearLayout parent, int sp, int color, int font, String placeholder) {
        TextView t = new TextView(parent.getContext());
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(UiFont.get(parent.getContext(), font));
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        t.setSingleLine(true);
        t.setText(placeholder);
        parent.addView(t);
        return t;
    }

    /** null when weather is off (no coordinates) or the layout has no weather block. */
    static Weather create(Activity a, BooleanSupplier is24) {
        Resources r = a.getResources();
        String lat = r.getString(R.string.weather_latitude).trim();
        String lon = r.getString(R.string.weather_longitude).trim();
        View block = a.findViewById(R.id.weather);
        if (lat.isEmpty() || lon.isEmpty() || block == null) return null;
        String url = "https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon
                + "&current=temperature_2m,weather_code,is_day&hourly=temperature_2m"
                + "&daily=temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=1"
                + "&forecast_hours=" + (HOURS + 1)
                + (r.getBoolean(R.bool.weather_fahrenheit) ? "&temperature_unit=fahrenheit" : "");
        // INVISIBLE, not GONE: the block keeps its height from the first layout, so the dock never jumps.
        block.setVisibility(View.INVISIBLE);
        return new Weather(block, a.findViewById(R.id.weatherNow), a.findViewById(R.id.weatherHours), url, is24);
    }

    void start() {
        running = true;
        handler.removeCallbacks(tick);
        if (inFlight) return; // its completion schedules the next one
        long age = SystemClock.elapsedRealtime() - lastOk;
        if (age >= EVERY) fetch();
        else handler.postDelayed(tick, EVERY - age);
    }

    void stop() {
        running = false;
        handler.removeCallbacks(tick);
    }

    private void fetch() {
        if (inFlight || !running) return;
        inFlight = true;
        new Thread(() -> {
            String body = get(url);
            handler.post(() -> {
                inFlight = false;
                boolean ok = body != null && show(body);
                if (ok) lastOk = SystemClock.elapsedRealtime();
                if (running) handler.postDelayed(tick, ok ? EVERY : RETRY);
            });
        }, "weather").start();
    }

    private static String get(String url) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(10_000);
            c.setReadTimeout(10_000);
            if (c.getResponseCode() != 200) return null;
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
                return out.toString("UTF-8");
            }
        } catch (Exception e) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private boolean show(String body) {
        try {
            JSONObject j = new JSONObject(body);
            JSONObject cur = j.getJSONObject("current");
            JSONObject daily = j.getJSONObject("daily");
            int code = cur.getInt("weather_code");
            now.setText(line(code, cur.getInt("is_day") == 1, cur.getDouble("temperature_2m"),
                    daily.getJSONArray("temperature_2m_max").getDouble(0),
                    daily.getJSONArray("temperature_2m_min").getDouble(0)));
            JSONObject hourly = j.getJSONObject("hourly");
            JSONArray t = hourly.getJSONArray("time"), temp = hourly.getJSONArray("temperature_2m");
            DateTimeFormatter f = DateTimeFormatter.ofPattern(is24.getAsBoolean() ? "HH:mm" : "h a", Locale.getDefault());
            // Index 0 is the hour already under way; show the six after it.
            for (int i = 0; i < HOURS && i + 1 < t.length(); i++) {
                temps[i].setText(Math.round(temp.getDouble(i + 1)) + "°");
                times[i].setText(LocalDateTime.parse(t.getString(i + 1)).format(f));
            }
            block.setVisibility(View.VISIBLE);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    static String line(int code, boolean day, double temp, double hi, double lo) {
        return symbol(code, day) + "  " + Math.round(temp) + "°  " + describe(code)
                + "     H:" + Math.round(hi) + "°  L:" + Math.round(lo) + "°";
    }

    /** WMO weather interpretation codes, as Open-Meteo documents them. */
    static String describe(int code) {
        switch (code) {
            case 0: return "Clear";
            case 1: return "Mostly Clear";
            case 2: return "Partly Cloudy";
            case 3: return "Cloudy";
            case 45: case 48: return "Fog";
            case 51: case 53: case 55: return "Drizzle";
            case 56: case 57: return "Freezing Drizzle";
            case 61: return "Light Rain";
            case 63: return "Rain";
            case 65: return "Heavy Rain";
            case 66: case 67: return "Freezing Rain";
            case 71: return "Light Snow";
            case 73: return "Snow";
            case 75: return "Heavy Snow";
            case 77: return "Snow Grains";
            case 80: case 81: return "Showers";
            case 82: return "Heavy Showers";
            case 85: case 86: return "Snow Showers";
            case 95: return "Thunderstorm";
            case 96: case 99: return "Thunderstorm with Hail";
            default: return "";
        }
    }

    /** The emoji form (U+FE0F): the TV's text fonts draw U+2601 as a blob, its emoji font draws a cloud. */
    static String symbol(int code, boolean day) {
        String s;
        if (code <= 1) s = day ? "\u2600" : "\u263E";
        else if (code <= 3) s = "\u2601";
        else if (code <= 48) s = "\u2248";
        else if (code <= 67 || code >= 80 && code <= 82) s = "\u2602";
        else if (code <= 86) s = "\u2744";
        else s = "\u26A1";
        return s + "\uFE0F";
    }
}
