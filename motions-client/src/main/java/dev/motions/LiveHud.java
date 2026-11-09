package dev.motions;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;

/** The "LIVE: +87.9m" pill shown at the top of the screen while a session is running. Red with a "-" when you're losing. */
public final class LiveHud {
    private LiveHud() {}

    public static final int W = 150, H = 24;
    private static final int GREEN = 0xFF4ADE80, RED = 0xFFF87171, TXT = 0xFFF1F3F6;

    /** Bold text plus its width, rebuilt only when the string changes. */
    public static final class PillText {
        private String s = "";
        private Text t;
        private int w;

        public void set(String n) {
            if (t != null && n.equals(s)) return;
            s = n;
            t = Draw.boldText(n);
            w = Draw.tr().getWidth(t);
        }
    }

    private static final PillText TEXT = new PillText();
    private static final Anim in = new Anim(0);
    private static boolean shown;
    private static LiveSession.State cState;
    private static long cProfit = Long.MIN_VALUE;

    public static void render(DrawContext c, RenderTickCounter tc) {
        LiveSession.State s = LiveSession.state;
        if (!shown && s == LiveSession.State.IDLE) return; // idle fast path: no work at all
        if (s == LiveSession.State.IDLE) in.to(0f); else in.snap(1f);
        float vis = in.get();
        if (vis < 0.01f) { shown = false; return; }
        shown = true;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options.hudHidden || mc.currentScreen instanceof MotionsScreen) return;

        long now = System.currentTimeMillis();
        boolean waiting = s == LiveSession.State.WAITING;
        long p = waiting ? 0 : LiveSession.profit();
        if (s != cState || p != cProfit) {
            cState = s;
            cProfit = p;
            TEXT.set(waiting ? "LIVE: type /bal" : "LIVE: " + LiveSession.signed(p));
        }
        int col = waiting ? TXT : p < 0 ? RED : GREEN;
        long since = now - LiveSession.changedMs;
        float glow = waiting ? 0.3f + 0.3f * (float) Math.sin(now / 220.0) : since < 900 ? 1f - since / 900f : 0f;

        MotionsConfig cfg = MotionsConfig.get();
        float sc = cfg.liveScale;
        int sw = c.getScaledWindowWidth(), sh = c.getScaledWindowHeight();
        int x = Layout.posX(Layout.LIVE, sw), y0 = Layout.posY(Layout.LIVE, sh);
        boolean top = y0 + H * sc / 2f < sh / 2f;
        float off = (1f - vis) * (H * sc + 24f) * (top ? -1f : 1f);
        var m = c.getMatrices();
        m.pushMatrix();
        m.translate(x, y0 + off);
        m.scale(sc, sc);
        drawPill(c, 0, 0, TEXT, col, vis, cfg.accent, glow, (now % 1_000_000L) / 1000f);
        m.popMatrix();
    }

    /** Glass pill with a blinking dot and the text centred. time < 0 = no blinking (previews). */
    public static void drawPill(DrawContext c, int x, int y, PillText t, int col, float a, int ac, float glow, float time) {
        if (a <= 0.01f || t.t == null) return;
        if (glow > 0.01f) Draw.round(c, x - 2, y - 2, W + 4, H + 4, H / 2 + 2, Draw.a(col, 0.28f * glow * a));
        Draw.glass(c, x, y, W, H, H / 2, Draw.a(ac, 0.75f * a), Draw.a(MotionsConfig.get().glassTint(), 0.82f * a),
                Draw.a(Draw.WHITE, 0.25f * a));
        float blink = time >= 0 && MotionsConfig.get().animations ? 0.55f + 0.45f * (float) Math.sin(time * 5f) : 1f;
        Draw.round(c, x + 10, y + H / 2 - 2, 5, 5, 2, Draw.a(col, a * blink));
        Draw.bold(c, t.t, x + 18 + (W - 18 - t.w) / 2, y + (H - 8) / 2, Draw.a(col, a));
    }
}
