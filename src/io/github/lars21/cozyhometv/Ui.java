package io.github.lars21.cozyhometv;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.view.View;

/** Small drawables and views (all sizes in device px). */
final class Ui {
    private Ui() {}

    /** Rounded rectangle with optional fill, vertical gradient and stroke. */
    static final class Box extends Drawable {
        final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        float radius, strokeW; int[] gradient; final RectF r = new RectF();
        Box(int color, float radius) { fill.setColor(color); this.radius = radius; stroke.setStyle(Paint.Style.STROKE); }
        Box stroke(int color, float w) { stroke.setColor(color); strokeW = w; stroke.setStrokeWidth(w); invalidateSelf(); return this; }
        Box gradient(int top, int bottom) { gradient = new int[]{top, bottom}; return this; }
        void set(int color, int strokeColor, float w) { fill.setColor(color); stroke.setColor(strokeColor); strokeW = w; stroke.setStrokeWidth(w); invalidateSelf(); }
        @Override protected void onBoundsChange(Rect b) { if (gradient != null) fill.setShader(new LinearGradient(0, b.top, 0, b.bottom, gradient[0], gradient[1], Shader.TileMode.CLAMP)); }
        @Override public void draw(Canvas c) {
            r.set(getBounds());
            if (Color.alpha(fill.getColor()) > 0 || fill.getShader() != null) c.drawRoundRect(r, radius, radius, fill);
            if (strokeW > 0 && Color.alpha(stroke.getColor()) > 0) { r.inset(strokeW / 2, strokeW / 2); c.drawRoundRect(r, radius, radius, stroke); }
        }
        @Override public void setAlpha(int a) {}
        @Override public void setColorFilter(ColorFilter f) {}
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    /** Default-mode background: deep vertical gradient with a soft glow, drawn once (no bitmap). */
    static final class Night extends Drawable {
        final Paint base = new Paint(), glow = new Paint(Paint.ANTI_ALIAS_FLAG);
        @Override protected void onBoundsChange(Rect b) {
            base.setShader(new LinearGradient(0, b.top, 0, b.bottom, 0xFF151A24, 0xFF0B0D12, Shader.TileMode.CLAMP));
            glow.setShader(new RadialGradient(b.width() * .18f, b.height() * .05f, b.width() * .6f, 0x332F4A7A, 0x002F4A7A, Shader.TileMode.CLAMP));
        }
        @Override public void draw(Canvas c) { c.drawRect(getBounds(), base); c.drawRect(getBounds(), glow); }
        @Override public void setAlpha(int a) {}
        @Override public void setColorFilter(ColorFilter f) {}
        @Override public int getOpacity() { return PixelFormat.OPAQUE; }
    }

    /** Frosted glass: the pre-blurred scene drawn aligned with the screen, plus a tint. */
    static final class Frost extends Drawable {
        Bitmap blurred; int screenW, screenH; final int[] origin = new int[2]; View owner; int tint; float radius;
        final Paint p = new Paint(Paint.FILTER_BITMAP_FLAG), t = new Paint(Paint.ANTI_ALIAS_FLAG); final RectF dst = new RectF(); final Path clip = new Path();
        Frost(View owner, int tint, float radius) { this.owner = owner; this.tint = tint; this.radius = radius; t.setColor(tint); }
        @Override public void draw(Canvas c) {
            Rect b = getBounds(); clip.reset(); clip.addRoundRect(new RectF(b), radius, radius, Path.Direction.CW);
            c.save(); c.clipPath(clip);
            if (blurred != null) { owner.getLocationOnScreen(origin); dst.set(-origin[0], -origin[1], screenW - origin[0], screenH - origin[1]); c.drawBitmap(blurred, null, dst, p); }
            c.drawRect(b, t); c.restore();
        }
        @Override public void setAlpha(int a) {}
        @Override public void setColorFilter(ColorFilter f) {}
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    /** Firefly: a warm dot with a glow. */
    static final class Dot extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); final float r;
        Dot(Context c, float r, float glow) { super(c); this.r = r; p.setColor(0xFFFFDF91); p.setShadowLayer(glow, 0, 0, 0xFFFFD57C); setLayerType(LAYER_TYPE_SOFTWARE, null); }
        @Override protected void onDraw(Canvas c) { c.drawCircle(getWidth() / 2f, getHeight() / 2f, r, p); }
    }

    /** Gear icon for the settings button. */
    static final class Gear extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); private final Path path = new Path();
        Gear(Context c, int color) { super(c); p.setColor(color); p.setStyle(Paint.Style.FILL); }
        @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
            path.reset(); float cx = w / 2f, cy = h / 2f, ro = Math.min(w, h) * .46f, ri = ro * .74f; int teeth = 8;
            for (int i = 0; i < teeth * 2; i++) { double a0 = Math.PI * 2 * i / (teeth * 2), a1 = Math.PI * 2 * (i + 1) / (teeth * 2); float r = i % 2 == 0 ? ro : ri;
                float x0 = cx + (float) Math.cos(a0) * r, y0 = cy + (float) Math.sin(a0) * r, x1 = cx + (float) Math.cos(a1) * r, y1 = cy + (float) Math.sin(a1) * r;
                if (i == 0) path.moveTo(x0, y0); else path.lineTo(x0, y0); path.lineTo(x1, y1); }
            path.close(); path.addCircle(cx, cy, ro * .38f, Path.Direction.CCW); path.setFillType(Path.FillType.EVEN_ODD);
        }
        void setColor(int color) { p.setColor(color); invalidate(); }
        @Override protected void onDraw(Canvas c) { c.drawPath(path, p); }
    }

    /** 3-pass box blur (close to gaussian) on a small software bitmap. */
    static Bitmap blur(Bitmap src, int r) {
        int w = src.getWidth(), h = src.getHeight(); int[] a = new int[w * h], b = new int[w * h];
        src.getPixels(a, 0, w, 0, 0, w, h);
        for (int pass = 0; pass < 3; pass++) { box(a, b, w, h, r, 1, w); box(b, a, h, w, r, w, 1); }
        return Bitmap.createBitmap(a, w, h, Bitmap.Config.ARGB_8888);
    }
    private static void box(int[] in, int[] out, int len, int lines, int r, int step, int lineStep) {
        int n = 2 * r + 1;
        for (int l = 0; l < lines; l++) {
            int o = l * lineStep; int rs = 0, gs = 0, bs = 0;
            for (int i = -r; i <= r; i++) { int c = in[o + Math.max(0, Math.min(len - 1, i)) * step]; rs += (c >> 16) & 255; gs += (c >> 8) & 255; bs += c & 255; }
            for (int i = 0; i < len; i++) {
                out[o + i * step] = 0xFF000000 | ((rs / n) << 16) | ((gs / n) << 8) | (bs / n);
                int ca = in[o + Math.min(len - 1, i + r + 1) * step], cb = in[o + Math.max(0, i - r) * step];
                rs += ((ca >> 16) & 255) - ((cb >> 16) & 255); gs += ((ca >> 8) & 255) - ((cb >> 8) & 255); bs += (ca & 255) - (cb & 255);
            }
        }
    }
}
