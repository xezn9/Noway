package com.icehome.up;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.view.ViewGroup;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * Ajustes de HyperSpring. Se guardan en la app (PrefsProvider) y se avisa al launcher y a la
 * interfaz del sistema con una difusión protegida: se aplican al momento y sin root.
 * <p>
 * Interfaz hecha en código, con colores del sistema (Monet): cuatro pestañas (Inicio, Escritorio,
 * Sistema y Acerca de) en una barra flotante de cristal líquido ({@link GlassBar}); cada pestaña
 * es una lista de grupos con icono y flecha que abren su página de ajustes (deslizadores e
 * interruptores propios, {@link Controls}), con su título grande y botón de volver.
 */
public class SettingsActivity extends Activity {

    private static final PathInterpolator EASE = new PathInterpolator(0.2f, 0f, 0f, 1f);

    // Colores (Monet en Android 12+; si no, unos fijos parecidos). Sin «private»: la presentación
    // ({@link Onboarding}) también los usa.
    int bg, card, pressed, text, text2, text3, accent, onAccent, accentSoft, track, warn;
    /** Acento intenso (interruptores, deslizadores, botón de la presentación) con texto blanco encima. */
    int accentStrong;
    /** Los cuatro tonos suaves de las manchas del fondo (coral, rosa, salmón, melocotón). */
    int[] vivid;

    private final List<Runnable> refreshers = new ArrayList<>();
    private float dp;
    private FrameLayout root, stack;
    /** La presentación de la primera vez, mientras está en pantalla. */
    private Onboarding ob;
    private GlassBar bar;
    private int insetTop, insetBottom, barSpace;
    /** La hoja de nueva versión o de novedades, mientras está abierta. */
    private UpdateSheet sheet;
    /** Columna de Inicio (para meter el aviso de versión nueva cuando llega la respuesta). */
    private LinearLayout homeCol;
    private View updateBanner;
    /** La próxima página que se cree es la de «Acerca de» (fondo más marcado). */
    private boolean glowNext;
    /** La cinta de texto de «Acerca de», para que su página la acelere al desplazar. */
    private Marquee glowMarquee;
    /** Desenfoque de debajo de la barra de estado: se redibuja al desplazar cualquier página. */
    private TopBlur topVeil;
    private int tab;
    /** El próximo cambio de pestaña va sin la onda de {@link TabReveal} (tras el gesto de atrás). */
    private boolean noWave;
    /** Páginas abiertas en la pestaña actual (la primera es la raíz). */
    private final List<ScrollView> pages = new ArrayList<>();

    private String[] tabs() {
        return new String[]{I18n.t("Inicio", "Home"), I18n.t("Escritorio", "Launcher"),
                I18n.t("Sistema", "System"), I18n.t("Acerca de", "About")};
    }
    private static final int[] TAB_ICONS = {Icons.HOME, Icons.GRID, Icons.PHONE, Icons.INFO};

    /** Una sección de un Tune.ITEMS: su nombre y sus filas. */
    private static final class Section {
        final String name;
        final List<Object[]> items = new ArrayList<>();

        Section(String name) {
            this.name = name;
        }
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        I18n.init(this);
        if (b == null && quickAction(getIntent().getStringExtra(SHORTCUT))) return;
        dp = getResources().getDisplayMetrics().density;
        palette();
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        if (Build.VERSION.SDK_INT >= 29) getWindow().setNavigationBarContrastEnforced(false);

        root = new FrameLayout(this);
        root.setBackgroundColor(bg);
        stack = new FrameLayout(this);
        root.addView(stack, new FrameLayout.LayoutParams(-1, -1));
        // Desenfoque progresivo bajo la barra de estado: lo que sube al desplazarse se desenfoca
        // poco a poco en vez de chocar con la hora.
        topVeil = new TopBlur(this, bg);
        topVeil.setSource(stack);
        final FrameLayout.LayoutParams vlp = new FrameLayout.LayoutParams(-1, px(40), Gravity.TOP);
        root.addView(topVeil, vlp);

        bar = new GlassBar(this, tabs(), TAB_ICONS, bg, text2, accent);
        final FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(-1, bar.heightFor(66), Gravity.BOTTOM);
        // La vista deja 12 dp por lado para que la barra pueda hincharse y seguir al dedo: la
        // cápsula queda a 22 dp de cada borde de la pantalla.
        blp.leftMargin = px(10);
        blp.rightMargin = px(10);
        root.addView(bar, blp);
        // La barra ve todo el contenedor de páginas (no solo la de arriba): en las transiciones,
        // con dos páginas a la vez, no se queda media barra vacía.
        bar.setSource(stack);
        bar.setOnSelect(new GlassBar.OnSelect() {
            @Override
            public void onSelect(int index) {
                showTab(index, true);
            }
        });
        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View v, WindowInsets in) {
                int top = in.getSystemWindowInsetTop(), bottom = in.getSystemWindowInsetBottom();
                insetBottom = bottom;
                if (sheet != null) sheet.setBottomInset(bottom);
                blp.bottomMargin = bottom + px(2);
                bar.setLayoutParams(blp);
                vlp.height = top + px(44);
                topVeil.setLayoutParams(vlp);
                if (top != insetTop || barSpace == 0) {
                    insetTop = top;
                    barSpace = bottom + px(2) + bar.heightFor(66);
                    for (ScrollView p : pages) pad(p);
                }
                return in;
            }
        });
        setContentView(root);
        // La primera vez (o tras «Ver la presentación»): presentación y configuración guiada.
        // Si no, la pestaña de Inicio entra con sus tarjetas una a una.
        boolean first = b == null && !getSharedPreferences("app", 0).getBoolean("onboarded", false);
        // Al abrir la app en frío: la entrada «de goma» (AppEntrance). Al girar la pantalla o volver
        // de otra app, nada: la página ya está.
        boolean entrance = !first && b == null;
        showTab(0, false);
        bar.select(0, false);
        if (entrance) AppEntrance.play(root, pages.get(0), bar, dp);
        if (first) showOnboarding();
        if (b == null) startupUpdates(first);
        setupBack();
        publishShortcuts();
        if (b == null && "health".equals(getIntent().getStringExtra(SHORTCUT))) showHealth();
    }

    // ---------------------------------------------------------------- atajos del icono
    /** Extra de los atajos que salen al mantener pulsado el icono de la app. */
    private static final String SHORTCUT = "hs_shortcut";

    /**
     * Atajos del icono: Pausar o reanudar, Reiniciar launcher y «¿Cómo va mi móvil?». Con
     * HyperSpring activo, el propio menú sale con su muelle y su borde de cristal.
     */
    private void publishShortcuts() {
        try {
            android.content.pm.ShortcutManager sm = getSystemService(android.content.pm.ShortcutManager.class);
            android.graphics.drawable.Icon icon = android.graphics.drawable.Icon.createWithResource(this,
                    getResources().getIdentifier("ic_tile", "drawable", getPackageName()));
            String[][] all = {
                    {"pause", I18n.t("Pausar o reanudar", "Pause or resume")},
                    {"restart", I18n.t("Reiniciar launcher", "Restart launcher")},
                    {"health", I18n.t("¿Cómo va mi móvil?", "How is my phone doing?")},
            };
            List<android.content.pm.ShortcutInfo> list = new ArrayList<>();
            for (String[] s : all) {
                android.content.Intent i = new android.content.Intent(android.content.Intent.ACTION_VIEW).setClass(this, SettingsActivity.class).putExtra(SHORTCUT, s[0]);
                list.add(new android.content.pm.ShortcutInfo.Builder(this, s[0])
                        .setShortLabel(s[1]).setIcon(icon).setIntent(i).build());
            }
            sm.setDynamicShortcuts(list);              // se rehacen al abrir la app: siguen el idioma
        } catch (Throwable ignored) {
            // un launcher sin atajos no es motivo para fallar
        }
    }

    /** Pausar y reiniciar se hacen sin abrir la app. Devuelve true si ya está hecho (y se cierra). */
    private boolean quickAction(String what) {
        if ("pause".equals(what)) {
            boolean now = !PauseTile.paused(this);
            PauseTile.setPaused(this, now);
            toast(now ? I18n.t("HyperSpring en pausa", "HyperSpring paused") : I18n.t("HyperSpring reanudado", "HyperSpring resumed"));
            finish();
            return true;
        }
        if ("restart".equals(what)) {
            restart(false);
            finish();
            return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- gesto de atrás

    /**
     * El gesto de atrás «predictivo» (Android 14 o más). En vez de esperar a que sueltes, el sistema
     * nos avisa de cada movimiento del dedo y la página lo sigue (ver {@link PredictiveBack}).
     * <p>
     * Solo nos apuntamos cuando hay algo que deshacer dentro de la app (una subpágina, otra pestaña,
     * la hoja de actualización o la presentación). En la raíz nos quitamos, y así el sistema hace su
     * propia animación de salir al escritorio. Por eso {@link #syncBack} se llama tras cada cambio.
     * <p>
     * Para que el sistema use esto hace falta {@code android:enableOnBackInvokedCallback="true"} en
     * el manifiesto. Con eso, el gesto ya no llama a onBackPressed (el botón de la flecha sí).
     */
    private Object backCb;                  // un OnBackAnimationCallback; Object para no romper en Android 11-13
    private boolean backOn;
    private PredictiveBack backAnim;
    private boolean sheetDrag;

    private void setupBack() {
        if (Build.VERSION.SDK_INT < 34) return;
        backCb = new android.window.OnBackAnimationCallback() {
            @Override
            public void onBackStarted(android.window.BackEvent e) {
                startBack(e.getSwipeEdge() == android.window.BackEvent.EDGE_LEFT);
            }

            @Override
            public void onBackProgressed(android.window.BackEvent e) {
                if (backAnim != null) backAnim.update(e.getProgress(), e.getTouchY());
                else if (sheetDrag && sheet != null) sheet.dragBack(e.getProgress());
            }

            @Override
            public void onBackInvoked() {
                finishBack();
            }

            @Override
            public void onBackCancelled() {
                if (backAnim != null) backAnim.cancel();
                else if (sheetDrag && sheet != null) sheet.cancelDrag();
                backAnim = null;
                sheetDrag = false;
            }
        };
        syncBack();
    }

    /** Se apunta o se quita del gesto según haya algo que deshacer dentro de la app. */
    private void syncBack() {
        if (Build.VERSION.SDK_INT < 34 || backCb == null) return;
        boolean need = menuClose != null || sheet != null || ob != null || pages.size() > 1 || tab != 0;
        if (need == backOn) return;
        backOn = need;
        android.window.OnBackInvokedDispatcher d = getOnBackInvokedDispatcher();
        android.window.OnBackInvokedCallback cb = (android.window.OnBackInvokedCallback) backCb;
        if (need) d.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, cb);
        else d.unregisterOnBackInvokedCallback(cb);
    }

    /** Empieza el gesto: decide qué se va a mover. */
    private void startBack(boolean fromLeft) {
        PushMorph.finish();
        TabReveal.finish();
        backAnim = null;
        sheetDrag = false;
        if (menuClose != null) {
            // Con el menú desplegable abierto, «atrás» solo lo cierra (al soltar).
        } else if (sheet != null) {
            sheetDrag = true;                       // la hoja baja un poco con el dedo
        } else if (ob != null) {
            // La presentación tiene sus propias transiciones: se cambia de paso al soltar.
        } else if (pages.size() > 1) {
            backAnim = new PredictiveBack(pages.get(pages.size() - 1), pages.get(pages.size() - 2), fromLeft, dp);
        } else if (tab != 0) {
            // En otra pestaña, «atrás» lleva a Inicio: la página se aparta y entra Inicio.
            backAnim = new PredictiveBack(pages.get(0), null, fromLeft, dp);
        }
    }

    /** Has soltado: se completa lo que el gesto estaba enseñando. */
    private void finishBack() {
        final PredictiveBack anim = backAnim;
        boolean drag = sheetDrag;
        backAnim = null;
        sheetDrag = false;
        if (anim == null) {
            // Sin animación preparada (botones de navegación, la hoja o la presentación).
            if (drag && sheet != null) sheet.dismiss();
            else if (ob != null) {
                if (!ob.back()) finish();
            } else onBackPressed();
            return;
        }
        if (pages.size() > 1) {
            final ScrollView top = pages.remove(pages.size() - 1);
            if (top == logPage) logText = null;
            anim.commit(new PredictiveBack.Done() {
                @Override
                public void run() {
                    stack.removeView(top);
                }
            });
        } else {
            bar.select(0, true);
            anim.commit(new PredictiveBack.Done() {
                @Override
                public void run() {
                    noWave = true;                  // el gesto ya ha hecho su propia animación
                    showTab(0, true);
                }
            });
            tab = 0;                               // para que syncBack ya sepa que vamos a Inicio
        }
        syncBack();
    }

    // ---------------------------------------------------------------- actualizaciones

    /**
     * Al abrir la app: si se acaba de actualizar, las novedades de esta versión; si no, y toca
     * (cada 6 horas como mucho), mira en GitHub si hay una versión nueva. Durante la presentación
     * de la primera vez, nada: ya hay bastante en pantalla.
     */
    private void startupUpdates(boolean first) {
        // Para probar las pantallas sin publicar nada: am start ... --ez debug_update true
        // (la última release cuenta como nueva) o --ez debug_whatsnew true.
        boolean fakeUpdate = getIntent().getBooleanExtra("debug_update", false);
        boolean fakeNews = getIntent().getBooleanExtra("debug_whatsnew", false);
        android.content.SharedPreferences p = Updates.prefs(this);
        int seen = p.getInt("seen_code", 0);
        p.edit().putInt("seen_code", Version.CODE).apply();
        if (first) return;
        if (fakeNews || (seen > 0 && seen < Version.CODE)) {
            Updates.forVersion(this, Version.NAME, new Updates.Callback() {
                @Override
                public void done(Updates.Release r, String error) {
                    if (r != null && !isFinishing()) showUpdate(r, UpdateSheet.WHATS_NEW);
                }
            });
            return;
        }
        if (fakeUpdate) {
            Updates.pretendNewer = true;
            p.edit().remove("upd_shown").apply();
        }
        if (fakeUpdate || Updates.due(this)) checkUpdates(null);
    }

    /**
     * Pregunta a GitHub por la última versión. Si es nueva, pone el aviso en Inicio y abre la hoja
     * (sola, solo la primera vez que se ve esa versión; a mano, siempre).
     *
     * @param status texto de la fila «Buscar actualizaciones» si se pidió a mano; null al abrir la app
     */
    private void checkUpdates(final TextView status) {
        if (status != null) status.setText(I18n.t("Buscando…", "Checking…"));
        Updates.latest(this, new Updates.Callback() {
            @Override
            public void done(Updates.Release r, String error) {
                if (isFinishing() || isDestroyed()) return;
                if (r == null) {
                    if (status != null) {
                        Motion.scramble(status, I18n.t("No se pudo conectar con GitHub", "Couldn't reach GitHub"));
                        toast(I18n.t("Sin conexión: ", "No connection: ") + error);
                    }
                    return;
                }
                if (r.newer()) {
                    if (status != null) Motion.scramble(status, I18n.t("Versión " + r.version + " disponible", "Version " + r.version + " available"));
                    addUpdateBanner(r);
                    android.content.SharedPreferences p = Updates.prefs(SettingsActivity.this);
                    if (status != null || !r.version.equals(p.getString("upd_shown", null))) {
                        p.edit().putString("upd_shown", r.version).apply();
                        showUpdate(r, UpdateSheet.UPDATE);
                    }
                } else if (status != null) {
                    Motion.scramble(status, I18n.t("Estás al día · ", "Up to date · ") + Version.NAME);
                }
            }
        });
    }

    /** Abre la hoja de nueva versión o de novedades encima de todo. */
    void showUpdate(Updates.Release r, int mode) {
        if (sheet != null || ob != null) return;
        sheet = new UpdateSheet(this, r, mode);
        sheet.setBottomInset(insetBottom);
        sheet.setOnClose(new Runnable() {
            @Override
            public void run() {
                sheet = null;
                syncBack();
            }
        });
        root.addView(sheet, new FrameLayout.LayoutParams(-1, -1));
        sheet.show();
        syncBack();
    }

    /** Aviso arriba de Inicio: «Versión X disponible», con un brillo que lo cruza de vez en cuando. */
    private View updateBannerView(final Updates.Release r) {
        LinearLayout c = card();
        c.setTag(null);
        GradientDrawable g = round(Controls.blend(card, accentStrong, 0.16f), px(18));
        g.setStroke(Math.max(1, px(1)), (accent & 0x00FFFFFF) | 0x8C000000);
        c.setBackground(g);
        c.setForeground(new UpdateSheet.Shimmer(px(18)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = px(14);
        c.setLayoutParams(lp);
        c.addView(navRow(Icons.DOWNLOAD, I18n.t("Versión " + r.version + " disponible", "Version " + r.version + " available"),
                I18n.t("Toca para ver qué hay de nuevo", "Tap to see what's new"), true, new Runnable() {
                    @Override
                    public void run() {
                        showUpdate(r, UpdateSheet.UPDATE);
                    }
                }));
        return c;
    }

    private void addUpdateBanner(Updates.Release r) {
        if (tab != 0 || homeCol == null || updateBanner != null) return;
        updateBanner = updateBannerView(r);
        // Debajo del título y la explicación de Inicio.
        homeCol.addView(updateBanner, 0);
        Motion.reveal(updateBanner, 0, px(16));
    }

    // ---------------------------------------------------------------- presentación

    /** Pone la presentación encima de todo y esconde la app (no se dibuja por debajo). */
    void showOnboarding() {
        if (ob != null) return;
        ob = new Onboarding(this);
        root.addView(ob, new FrameLayout.LayoutParams(-1, -1));
        stack.setVisibility(View.INVISIBLE);
        bar.setVisibility(View.INVISIBLE);
        ob.start();
        syncBack();
        root.requestApplyInsets();
    }

    /**
     * Cierra la presentación: se aleja y se desvanece mientras la app aparece detrás acercándose,
     * y la barra sube con un muelle. Con {@code restart}, reinicia el launcher y la interfaz.
     */
    void closeOnboarding(final boolean restart) {
        if (ob == null) return;
        getSharedPreferences("app", 0).edit().putBoolean("onboarded", true).apply();
        final Onboarding o = ob;
        ob = null;
        o.setEnabled(false);
        showTab(0, false);
        bar.select(0, false);
        stack.setVisibility(View.VISIBLE);
        bar.setVisibility(View.VISIBLE);
        stack.setAlpha(0f);
        stack.setScaleX(0.92f);
        stack.setScaleY(0.92f);
        stack.animate().alpha(1f).scaleX(1f).scaleY(1f).setStartDelay(90).setDuration(800)
                .setInterpolator(Motion.EXPO_OUT).start();
        if (pages.size() > 0) Motion.cascade((ViewGroup) pages.get(0).getChildAt(0), 160, px(24));
        bar.setTranslationY(px(140));
        ValueAnimator up = ValueAnimator.ofFloat(px(140), 0f);
        Spring.apply(up, px(140), 0f, 0f, 0.6f, 0.72f);
        up.setStartDelay(260);
        up.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                bar.setTranslationY((Float) a.getAnimatedValue());
                bar.invalidate();                    // que el cristal no se quede con lo de debajo de antes
            }
        });
        up.start();
        o.animate().alpha(0f).scaleX(1.08f).scaleY(1.08f).setDuration(460).setInterpolator(Motion.EASE)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        root.removeView(o);
                    }
                }).start();
        if (restart) {
            // Un poco después, para que se vea la animación antes de que parpadee la pantalla.
            root.postDelayed(new Runnable() {
                @Override
                public void run() {
                    restart(false);
                    restart(true);
                }
            }, 900);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshLog();
        for (Runnable r : refreshers) r.run();
    }

    @Override
    public void onBackPressed() {
        if (menuClose != null) {
            menuClose.run();
            return;
        }
        if (sheet != null) {
            sheet.dismiss();
            return;
        }
        if (ob != null) {
            if (!ob.back()) super.onBackPressed();
            return;
        }
        if (pages.size() > 1) pop();
        else if (tab != 0) {
            bar.select(0, true);
            showTab(0, true);
        } else super.onBackPressed();
    }

    // ---------------------------------------------------------------- colores
    /**
     * Tema fijo de HyperSpring: negro puro (en pantallas OLED los píxeles negros van apagados) con
     * un solo color de acento, el coral del icono, siempre liso. El acento solo aparece en lo que
     * está encendido y en la acción principal; todo lo demás es blanco y grises. No depende del
     * fondo de pantalla (Monet): la app se ve igual en todos los móviles, como una marca.
     */
    private void palette() {
        bg = 0xFF000000;
        card = 0xFF0E0E10;                      // apenas más claro que el fondo: se distingue por el filo
        pressed = 0x1FFFFFFF;
        text = 0xFFF5F5F7;
        text2 = 0xFF8E8E96;
        text3 = 0xFF5C5C66;
        accent = Motion.ACCENT;
        accentStrong = Motion.ACCENT;           // el mismo: un solo acento en toda la app
        onAccent = 0xFF000000;                  // texto negro sobre el coral, como en las webs actuales
        accentSoft = (Motion.ACCENT & 0x00FFFFFF) | 0x1F000000;
        track = 0xFF26262B;
        warn = 0xFF0E0E10;
        // Antes, los colores de las manchas del fondo. Ya no hay manchas: queda el acento.
        vivid = new int[]{Motion.ACCENT, Motion.ACCENT, Motion.ACCENT, Motion.ACCENT};
    }

    /** Filo de las tarjetas: una línea de luz finísima, en vez de sombras o colores. */
    private static final int HAIRLINE = 0x17FFFFFF;

    private static int mix(int a, int b, float t) {
        return Color.rgb((int) (Color.red(a) + (Color.red(b) - Color.red(a)) * t),
                (int) (Color.green(a) + (Color.green(b) - Color.green(a)) * t),
                (int) (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t));
    }

    // ---------------------------------------------------------------- navegación
    private void showTab(int index, boolean animate) {
        TabReveal.finish();                     // un cambio anterior a medias se termina ya
        tab = index;
        refreshers.clear();
        logText = null;
        final List<ScrollView> old = new ArrayList<>(pages);
        pages.clear();
        ScrollView p;
        switch (index) {
            case 1:
                p = desktopTab();
                break;
            case 2:
                p = systemTab();
                break;
            case 3:
                p = aboutTab();
                break;
            default:
                p = homeTab();
                break;
        }
        pages.add(p);
        stack.addView(p);
        // Cambio de pestaña: la nueva se abre en círculo desde su botón de la barra (TabReveal).
        boolean wave = animate && !noWave && !old.isEmpty() && stack.getWidth() > 0 && bar.getWidth() > 0;
        noWave = false;
        // Entrada escalonada: título, explicación y cada tarjeta (y sus filas) una detrás de otra.
        if (animate) Motion.cascade((ViewGroup) p.getChildAt(0), wave ? 90 : 0, px(22));
        if (wave) {
            float seg = bar.getWidth() / (float) TAB_ICONS.length;
            float cx = bar.getX() + seg * (index + 0.5f), cy = bar.getY() + bar.getHeight() / 2f;
            TabReveal.play(root, stack, old, p, cx, cy, new Runnable() {
                @Override
                public void run() {
                    for (ScrollView o : old) stack.removeView(o);
                }
            });
        } else {
            for (ScrollView o : old) stack.removeView(o);
        }
        syncBack();
    }

    private void push(ScrollView p) {
        final ScrollView under = pages.get(pages.size() - 1);
        pages.add(p);
        stack.addView(p);
        // Como en el asistente de HyperOS: lo de antes se disuelve desenfocándose y lo nuevo aparece
        // enfocándose, elemento a elemento (PushMorph). Solo con Android 12+, que es quien sabe
        // desenfocar vistas; antes, el deslizamiento lateral de siempre.
        if (Build.VERSION.SDK_INT >= 31) {
            under.animate().cancel();
            // Todo sale de lo que acabas de tocar (si fue hace poco); si no, del centro.
            float ox = stack.getWidth() / 2f, oy = stack.getHeight() * 0.45f;
            android.graphics.RectF from = null;
            View src = lastTouch;
            lastTouch = null;
            if (src != null && src.isAttachedToWindow() && SystemClock.uptimeMillis() - lastTouchAt < 1500) {
                int[] a = new int[2], s = new int[2];
                src.getLocationInWindow(a);
                stack.getLocationInWindow(s);
                ox = a[0] - s[0] + src.getWidth() / 2f;
                oy = a[1] - s[1] + src.getHeight() / 2f;
                // La fila tocada (si es una fila, no algo enorme) crece hasta ser la página nueva.
                if (src.getHeight() < stack.getHeight() / 3) {
                    from = new android.graphics.RectF(a[0] - s[0], a[1] - s[1],
                            a[0] - s[0] + src.getWidth(), a[1] - s[1] + src.getHeight());
                }
            }
            PushMorph.play(under, (ViewGroup) p, from, ox, oy, accentStrong, card, new Runnable() {
                @Override
                public void run() {
                    bar.invalidate();
                }
            });
            syncBack();
            return;
        }
        p.setTranslationX(stack.getWidth());
        p.animate().translationX(0f).setDuration(380).setInterpolator(Motion.EXPO_OUT).setUpdateListener(barTick()).start();
        Motion.cascade((ViewGroup) p.getChildAt(0), 110, px(18));
        // La de debajo se aleja un poco (profundidad), además de irse a un lado.
        under.animate().translationX(-stack.getWidth() * 0.25f).alpha(0f).scaleX(0.94f).scaleY(0.94f)
                .setDuration(380).setInterpolator(EASE).start();
        syncBack();
    }

    private void pop() {
        PushMorph.finish();
        final ScrollView top = pages.remove(pages.size() - 1);
        ScrollView under = pages.get(pages.size() - 1);
        under.animate().translationX(0f).alpha(1f).scaleX(1f).scaleY(1f).setDuration(320).setInterpolator(EASE).setUpdateListener(barTick()).start();
        top.animate().translationX(stack.getWidth()).setDuration(320).setInterpolator(EASE).withEndAction(new Runnable() {
            @Override
            public void run() {
                stack.removeView(top);
            }
        }).start();
        if (top == logPage) logText = null;
        syncBack();
    }

    private ValueAnimator.AnimatorUpdateListener barTick() {
        return new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                bar.invalidate();
                topVeil.invalidate();
            }
        };
    }

    /** Una página: título grande (y botón de volver si no es la raíz) y su contenido. */
    private ScrollView page(String title, String subtitle, boolean back, LinearLayout content) {
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        // Que nada se corte en el borde: la cinta de «Acerca de» va de lado a lado de la pantalla y
        // las tarjetas inclinadas del scroll asoman un poco fuera de su sitio.
        list.setClipToPadding(false);
        list.setClipChildren(false);

        // Barra de arriba (botón de volver y título pequeño). Va «clavada»: al desplazar la página se
        // queda arriba (se mueve tanto como el scroll) y, cuando el título grande se ha ido, en ella
        // aparece el nombre de la página en pequeño sobre un fondo negro. Ocupa todo el ancho.
        final LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setMinimumHeight(px(52));
        top.setPadding(px(16), 0, px(16), 0);
        top.setTag(Motion.PINNED);
        top.setTranslationZ(px(1));                     // por encima de lo que pasa por debajo
        top.setOutlineProvider(null);                   // sin sombra
        // Fondo negro opaco con un filo de luz debajo (aparece con el título pequeño).
        GradientDrawable topLine = new GradientDrawable();
        topLine.setColor(HAIRLINE);
        final android.graphics.drawable.LayerDrawable topBg = new android.graphics.drawable.LayerDrawable(
                new Drawable[]{new android.graphics.drawable.ColorDrawable(bg), topLine});
        topBg.setLayerGravity(1, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL);
        topBg.setLayerHeight(1, Math.max(1, px(0.8f)));
        topBg.setAlpha(0);
        top.setBackground(topBg);
        if (back) {
            ImageView b = new ImageView(this);
            b.setImageDrawable(new Icons(Icons.BACK, text));
            b.setPadding(px(10), px(10), px(10), px(10));
            b.setBackground(new RippleDrawable(ColorStateList.valueOf(pressed), round(card, px(22)), null));
            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onBackPressed();
                }
            });
            top.addView(b, new LinearLayout.LayoutParams(px(44), px(44)));
        }
        final TextView mini = title != null ? text(title, 17, text) : null;
        if (mini != null) {
            mini.setTypeface(Typeface.create(Typeface.DEFAULT, 700, false));
            mini.setSingleLine(true);
            mini.setEllipsize(TextUtils.TruncateAt.END);
            mini.setPadding(px(back ? 4 : 8), 0, px(8), 0);
            mini.setAlpha(0f);
            top.addView(mini, new LinearLayout.LayoutParams(0, -2, 1f));
        }
        if (back || title != null) {
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-1, -2);
            tlp.leftMargin = tlp.rightMargin = -px(16);
            list.addView(top, tlp);
        }

        // Título enorme que entra letra a letra (SplitTitle): el titular de las webs virales.
        View bigTitle = null;
        if (title != null) {
            bigTitle = new SplitTitle(this, title, 40, text, 700);
            bigTitle.setPadding(px(6), px(8), px(6), 0);
            list.addView(bigTitle, new LinearLayout.LayoutParams(-1, -2));
        }
        if (subtitle != null) {
            TextView s = text(subtitle, 14, text2);
            s.setLineSpacing(0, 1.18f);
            s.setPadding(px(8), px(8), px(8), 0);
            list.addView(s);
        }
        list.addView(content);

        ScrollView sv = LiveBackground.scrollView(this);
        sv.setClipToPadding(false);
        sv.setVerticalScrollBarEnabled(false);
        sv.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        // Negro con rejilla de puntos; en «Acerca de», los puntos algo más marcados.
        // Las cuatro pestañas llevan además las luces que recorren la rejilla (LiveBackground.ambient);
        // las subpáginas, solo los puntos, para no distraer mientras ajustas.
        (!back ? LiveBackground.ambient(bg, dp, glowNext)
                : glowNext ? LiveBackground.hero(bg, dp) : LiveBackground.dots(bg, dp)).attachTo(sv);
        final Marquee band = glowMarquee;
        glowNext = false;
        glowMarquee = null;
        sv.addView(list);
        final View parallax = bigTitle;
        final float fade = px(130);
        final ScrollView pageView = sv;
        sv.setOnScrollChangeListener(new View.OnScrollChangeListener() {
            @Override
            public void onScrollChange(View v, int x, int y, int ox, int oy) {
                // El título grande va más despacio que el contenido, se encoge un poco y se apaga
                // al subir (paralaje con «scrub»: lo mueve el dedo, no el tiempo).
                if (parallax != null) {
                    float f = Math.min(1f, y / fade);
                    parallax.setTranslationY(y * 0.45f);
                    parallax.setAlpha(1f - f);
                    parallax.setPivotX(0f);
                    parallax.setScaleX(1f - 0.12f * f);
                    parallax.setScaleY(1f - 0.12f * f);
                }
                // La barra de arriba se queda clavada; el título pequeño sale cuando el grande se va.
                top.setTranslationY(y);
                float g = Motion.clamp01((y - fade * 0.55f) / (fade * 0.35f));
                topBg.setAlpha(Math.round(255 * g));
                if (mini != null) {
                    mini.setAlpha(g);
                    mini.setTranslationY(px(8) * (1f - g));
                }
                // La cinta de «Acerca de» acelera con el dedo (y cambia de sentido si subes).
                if (band != null) band.push(y - oy);
                Motion.scrub(pageView, list, insetTop + px(8), dp);
                // Al bajar por la página la barra se encoge; al subir (o arriba del todo) vuelve.
                bar.onPageScroll(y, y - oy);
                bar.invalidate();
                // El desenfoque de arriba no sabe cuándo se mueve lo de debajo: sin esto se quedaba
                // con la imagen vieja y el texto pasaba nítido por encima de la hora.
                topVeil.invalidate();
            }
        });
        // Y una vez al colocarse la página: lo que empieza fuera de pantalla ya viene inclinado.
        list.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
                Motion.scrub(pageView, list, insetTop + px(8), dp);
            }
        });
        pad(sv);
        return sv;
    }

    private void pad(ScrollView sv) {
        View list = sv.getChildAt(0);
        if (list != null) list.setPadding(px(16), insetTop + px(8), px(16), barSpace + px(16));
    }

    private LinearLayout column() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    // ---------------------------------------------------------------- pestañas
    private ScrollView homeTab() {
        LinearLayout c = column();
        homeCol = c;
        c.addView(statusPill());
        updateBanner = null;
        // Si la última búsqueda encontró una versión nueva, el aviso sigue ahí aunque no haya red.
        Updates.Release cached = Updates.cached(this);
        if (cached != null && cached.newer()) {
            updateBanner = updateBannerView(cached);
            c.addView(updateBanner);
        }
        StringBuilder old = new StringBuilder();
        if (installed(MainHook.OLD_TELON)) old.append("Telón");
        if (installed(MainHook.OLD_KASHIPLUS)) old.append(old.length() > 0 ? I18n.t(" y KashiPlus", " and KashiPlus") : "KashiPlus");
        if (old.length() > 0) {
            c.addView(notice(I18n.t("Sigue instalado " + old + ". HyperSpring ya lo incluye: desinstálalo (y quítalo de "
                            + "LSPosed) y reinicia. Mientras tanto, esa parte no se carga.",
                    old + " is still installed. HyperSpring already includes it: uninstall it (and remove it from "
                            + "LSPosed) and reboot. Until then, that part is not loaded."), null, null));
        }
        if (com.icehome.up.telon.SafeMode.active(this)) {
            c.addView(notice(I18n.t("Modo seguro: la interfaz del sistema se reinició varias veces seguidas y el telón y el "
                            + "centro de control se han desactivado solos. Si no fue por ellos, reactívalos y reinicia la interfaz.",
                    "Safe mode: System UI restarted several times in a row, so the curtain and the control center were "
                            + "turned off automatically. If they weren't the cause, turn them back on and restart System UI."),
                    I18n.t("Reactivar", "Turn back on"), new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            send(Remote.RESET_SAFE);
                            toast(I18n.t("Reactivado: reinicia la interfaz", "Turned on: restart System UI"));
                        }
                    }));
        }

        if ("1".equals(state("launcher_safe"))) {
            c.addView(notice(I18n.t("Modo seguro del escritorio: el launcher se cerró varias veces seguidas y HyperSpring "
                            + "ha dejado de cargar sus animaciones ahí (el launcher funciona normal). Si no fue por ellas, "
                            + "reactívalas y reinicia el launcher.",
                    "Launcher safe mode: the launcher crashed several times in a row, so HyperSpring stopped loading its "
                            + "animations there (the launcher works normally). If they weren't the cause, turn them back on "
                            + "and restart the launcher."),
                    I18n.t("Reactivar", "Turn back on"), new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            send(Remote.RESET_SAFE);
                            toast(I18n.t("Reactivado: reinicia el launcher", "Turned on: restart the launcher"));
                        }
                    }));
        }
        if (PauseTile.paused(this)) {
            c.addView(notice(I18n.t("HyperSpring está en pausa: las apps se abren con la animación de HyperOS, sin telón "
                            + "y sin recientes que se doblan. El cristal sigue igual.",
                    "HyperSpring is paused: apps open with the HyperOS animation, with no curtain and no bending "
                            + "recents. The glass stays as it is."),
                    I18n.t("Reanudar", "Resume"), new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            PauseTile.setPaused(SettingsActivity.this, false);
                            toast(I18n.t("HyperSpring reanudado", "HyperSpring resumed"));
                            recreate();
                        }
                    }));
        }
        String stale = staleParts();
        if (!xiaomiOs()) {
            // Mucha gente lo instala en realme, OPPO, Samsung… y abre issues: decirlo claro y lo primero.
            c.addView(notice(I18n.t("Este móvil no tiene HyperOS ni MIUI. HyperSpring solo funciona en Xiaomi, Redmi y POCO "
                            + "con HyperOS o MIUI: engancha su launcher y su interfaz, que no existen en otras marcas.",
                    "This phone doesn't run HyperOS or MIUI. HyperSpring only works on Xiaomi, Redmi and POCO "
                            + "with HyperOS or MIUI: it hooks their launcher and System UI, which other brands don't have."),
                    null, null));
        } else if (stale != null) {
            c.addView(notice(I18n.t("Has actualizado HyperSpring, pero " + stale + " sigue con la versión anterior. "
                            + "Reinícialo (o reinicia el móvil) para cargar la nueva.",
                    "You updated HyperSpring, but " + stale + " is still running the previous version. "
                            + "Restart it (or reboot the phone) to load the new one."), null, null));
        } else if (state("launcher_seen") == null && state("systemui_seen") == null) {
            c.addView(notice(I18n.t("Todavía no se ha detectado HyperSpring funcionando. Si acabas de instalarlo o actualizarlo, "
                            + "reinicia el launcher y la interfaz (abajo) o el móvil. Si sigue igual, revisa en LSPosed que el "
                            + "módulo y sus ámbitos (Launcher del sistema e Interfaz del sistema) estén activos.",
                    "HyperSpring hasn't been detected running yet. If you just installed or updated it, restart the "
                            + "launcher and System UI (below) or reboot. If nothing changes, check in LSPosed that the module "
                            + "and its scopes (System launcher and System UI) are enabled."),
                    null, null));
        } else if (shotMissing()) {
            // Al actualizar, LSPosed no añade solo los ámbitos nuevos: es fácil que falte este.
            c.addView(notice(I18n.t("Falta un ámbito: en LSPosed, abre HyperSpring y marca «Captura de pantalla» "
                            + "(com.miui.screenshot). Sin él, la vista previa de la captura tapa el marco y la pantalla "
                            + "se encoge tarde. Después haz una captura y este aviso desaparece.",
                    "A scope is missing: in LSPosed, open HyperSpring and tick “Screenshot” "
                            + "(com.miui.screenshot). Without it, the screenshot preview covers the frame and the screen "
                            + "shrinks late. Then take a screenshot and this notice goes away."),
                    null, null));
        }

        c.addView(label(I18n.t("Funciones", "Features")));
        c.addView(featuresWheel());

        // Una sola tarjeta de perfiles: primero los modos de sistema (animaciones y cristal a la
        // vez) y debajo los de solo animación. Las filas de la segunda tarjeta pasan a la primera.
        c.addView(label(I18n.t("Perfiles", "Presets")));
        LinearLayout presets = presetCard(Presets.MODES, Icons.PALETTE);
        LinearLayout anim = presetCard(Presets.ALL, Icons.SLIDERS);
        while (anim.getChildCount() > 0) {
            View row = anim.getChildAt(0);
            anim.removeViewAt(0);
            presets.addView(row);
        }
        c.addView(presets);

        c.addView(label(I18n.t("Acciones", "Actions")));
        LinearLayout a = card();
        a.setTag(Motion.CASCADE);
        a.addView(navRow(Icons.INFO, I18n.t("¿Cómo va mi móvil?", "How is my phone doing?"),
                I18n.t("Mira si las animaciones van fluidas en tu móvil", "See whether the animations run smoothly on your phone"),
                true, new Runnable() {
                    @Override
                    public void run() {
                        showHealth();
                    }
                }));
        // Reiniciar se hace manteniendo pulsado: un toque sin querer al hacer scroll no cierra nada.
        a.addView(holdRow(Icons.RESTART, I18n.t("Reiniciar launcher", "Restart launcher"),
                I18n.t("Aplica los cambios de funciones del escritorio", "Applies launcher feature changes"), new Runnable() {
            @Override
            public void run() {
                restart(false);
            }
        }));
        a.addView(holdRow(Icons.RESTART, I18n.t("Reiniciar interfaz del sistema", "Restart System UI"),
                I18n.t("Telón y centro de control", "Curtain and control center"), new Runnable() {
            @Override
            public void run() {
                restart(true);
            }
        }));
        if (!getSharedPreferences("app", 0).getBoolean("tile_added", false)) a.addView(navRow(Icons.TOGGLES, I18n.t("Tile de ajustes rápidos", "Quick settings tile"),
                I18n.t("Pausa HyperSpring con un toque desde el panel, sin reiniciar nada", "Pause HyperSpring with one tap from the panel, without restarting anything"),
                false, new Runnable() {
            @Override
            public void run() {
                addTile();
            }
        }));
        a.addView(navRow(Icons.LIST, I18n.t("Registro", "Log"),
                I18n.t("Lo que anotan el launcher y la interfaz", "What the launcher and System UI write down"), true, new Runnable() {
            @Override
            public void run() {
                push(logPage());
            }
        }));
        c.addView(a);
        if (!getSharedPreferences("app", 0).getBoolean("root_ok", false)) {
            c.addView(warning(I18n.t("Los botones de reiniciar necesitan que concedas root a HyperSpring (Magisk, KernelSU o "
                            + "APatch). Sin root, se intenta a través de la interfaz del sistema, pero puede no funcionar en "
                            + "todos los móviles; en ese caso, reinicia el móvil.",
                    "The restart buttons need root access granted to HyperSpring (Magisk, KernelSU or APatch). Without "
                            + "root, HyperSpring tries through System UI, but that may not work on every phone; if it doesn't, "
                            + "reboot the phone.")));
        }
        return page("HyperSpring", I18n.t("Animaciones y cristal líquido para HyperOS. Toca una función para encenderla; mantenla pulsada para ver qué hace.",
                "Animations and liquid glass for HyperOS. Tap a feature to turn it on; hold it to see what it does."), false, c);
    }

    /**
     * Funciones de Inicio en rejilla compacta de dos columnas: cada baldosa es una pastilla baja
     * con el icono a la izquierda y el nombre y el estado al lado. La principal (el genio) ocupa
     * todo el ancho. Al tocarla se enciende o se apaga; si la mantienes pulsada, debajo de la
     * rejilla se despliega qué hace esa función.
     */
    private LinearLayout featuresGrid() {
        Object[][] f = {
                {Icons.SPARKLE, item(Tune.ITEMS, "icehome_genie_on"), I18n.t("Abrir y cerrar apps", "Opening and closing apps")},
                {Icons.LAYERS, item(Tune.ITEMS, "icehome_curtain_on"), I18n.t("Recientes que se doblan", "Bending recents")},
                {Icons.WIDGETS, item(Tune.ITEMS, "icehome_stack_on"), I18n.t("Pila de widgets", "Widget stack")},
                {Icons.FOLDER, item(Tune.ITEMS, "icehome_folder_morph_on"), I18n.t("Carpetas con muelle", "Springy folders")},
                {Icons.DROP, item(Tune.ITEMS, "icehome_folder_glass_on"), I18n.t("Carpetas de cristal", "Glass folders")},
                {Icons.LIST, item(Tune.ITEMS, "icehome_menu_on"), I18n.t("Menú de atajos", "Shortcut menu")},
                {Icons.CURTAIN, item(com.icehome.up.telon.Tune.ITEMS, "telon_on"), I18n.t("Telón", "Curtain")},
                {Icons.PHONE, item(com.icehome.up.telon.Tune.ITEMS, "telon_squeeze_on"), I18n.t("Captura que encoge", "Squeezing screenshot")},
                {Icons.SLIDERS, item(com.icehome.up.cc.Tune.ITEMS, "kashiplus_cc_on"), I18n.t("Centro de control", "Control center")},
                {Icons.DROP, item(Tune.ITEMS, "icehome_dock_glass_on"), I18n.t("Dock de cristal", "Glass dock")},
        };
        featureTiles.clear();
        featureSpecs = f;
        LinearLayout grid = column();
        grid.setTag(Motion.CASCADE);                     // las filas entran una a una
        grid.setClipChildren(false);
        grid.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        // La explicación de la función que mantengas pulsada (una a la vez), debajo de la rejilla.
        final TextView info = text("", 13, text2);
        info.setLineSpacing(0, 1.18f);
        info.setPadding(px(14), px(12), px(14), px(12));
        GradientDrawable ib = round(card, px(16));
        ib.setStroke(Math.max(1, px(1)), (accent & 0x00FFFFFF) | 0x55000000);
        info.setBackground(ib);
        info.setVisibility(View.GONE);
        grid.addView(featureTile(f[0], info), new LinearLayout.LayoutParams(-1, -2));
        for (int i = 1; i < f.length; i += 2) {
            LinearLayout row = new LinearLayout(this);
            row.setClipChildren(false);
            row.setTag(AppEntrance.TILE_ROW);           // en la entrada, cada baldosa rebota por su cuenta
            LinearLayout.LayoutParams a = new LinearLayout.LayoutParams(0, -1, 1f);
            a.rightMargin = px(4);
            row.addView(featureTile(f[i], info), a);
            LinearLayout.LayoutParams b = new LinearLayout.LayoutParams(0, -1, 1f);
            b.leftMargin = px(4);
            if (i + 1 < f.length) row.addView(featureTile(f[i + 1], info), b);
            else row.addView(new View(this), b);
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2);
            rlp.topMargin = px(8);
            grid.addView(row, rlp);
        }
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(-1, -2);
        ilp.topMargin = px(8);
        grid.addView(info, ilp);
        return grid;
    }

    /**
     * Las funciones en una rueda giratoria ({@link FeatureWheel}) en vez de la rejilla.
     * Las fichas son las mismas (encender, mantener pulsado, la onda); debajo se lee qué hace la
     * que está en el centro.
     */
    private LinearLayout featuresWheel() {
        LinearLayout grid = featuresGrid();             // crea las fichas y el cuadro de explicación
        final TextView info = (TextView) grid.getChildAt(grid.getChildCount() - 1);
        grid.removeView(info);
        final List<View> tiles = new ArrayList<>(featureTiles);
        final List<String[]> texts = new ArrayList<>();
        for (Object[] spec : featureSpecs) {
            Object[] it = (Object[]) spec[1];
            texts.add(new String[]{(String) spec[2], I18n.desc(it)});
        }
        LinearLayout box = column();
        box.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        FeatureWheel wheel = new FeatureWheel(this, tiles, 60, bg, accent);
        box.addView(wheel, new LinearLayout.LayoutParams(-1, -2));
        // Qué hace la del centro, siempre a la vista: cambia con un fundido corto al girar.
        info.setVisibility(View.VISIBLE);
        info.setMinLines(3);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(-1, -2);
        ilp.topMargin = px(6);
        box.addView(info, ilp);
        wheel.setOnCenter(new FeatureWheel.OnCenter() {
            @Override
            public void center(int index) {
                if (index < 0 || index >= texts.size()) return;
                String title = texts.get(index)[0], about = texts.get(index)[1];
                if (about == null) return;
                // Como la explicación de siempre: el nombre en color y negrita, y lo que hace.
                final android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder(title);
                sb.setSpan(new android.text.style.ForegroundColorSpan(accent), 0, title.length(), 0);
                sb.setSpan(new android.text.style.StyleSpan(Typeface.BOLD), 0, title.length(), 0);
                sb.append("  ").append(about);
                info.setTag(null);                      // mantener pulsado la vuelve a «abrir»
                info.animate().cancel();
                info.setVisibility(View.VISIBLE);
                info.setTranslationY(0f);
                if (!ValueAnimator.areAnimatorsEnabled() || info.getText().length() == 0) {
                    info.setText(sb);
                    info.setAlpha(1f);
                    return;
                }
                info.animate().alpha(0f).setDuration(90).withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        info.setText(sb);
                        info.animate().alpha(1f).setDuration(160).start();
                    }
                }).start();
            }
        });
        return box;
    }

    /** Las funciones de la rejilla (las usa también la rueda de prueba). */
    private Object[][] featureSpecs = new Object[0][];

    /**
     * Una baldosa de la rejilla: pastilla baja con icono, nombre y estado. {@code info} es el
     * cuadro de debajo de la rejilla donde se explica la función al mantenerla pulsada.
     */
    private View featureTile(Object[] spec, final TextView info) {
        final Object[] it = (Object[]) spec[1];
        final String key = (String) it[0];
        final String title = (String) spec[2];
        final String about = I18n.desc(it);
        final LinearLayout tile = new LinearLayout(this);
        tile.setGravity(Gravity.CENTER_VERTICAL);
        tile.setPadding(px(10), px(9), px(10), px(9));
        tile.setMinimumHeight(px(54));

        // Fondo en tres capas: la tarjeta negra con su filo, el círculo de color que se abre al
        // encenderla (Spot) y un borde del color de acento. Las dos últimas, solo encendida.
        int r = px(16);
        GradientDrawable base = round(card, r);
        base.setStroke(Math.max(1, px(0.8f)), HAIRLINE);
        // Tinte muy leve: con todas encendidas, la rejilla sigue viéndose negra (no marrón).
        final Spot spot = new Spot((accent & 0x00FFFFFF) | 0x12000000, r);
        GradientDrawable edge = round(0, r);
        edge.setStroke(Math.max(1, px(1)), (accent & 0x00FFFFFF) | 0x66000000);
        tile.setBackground(new android.graphics.drawable.LayerDrawable(new Drawable[]{base, spot, edge}));

        final ImageView ic = icon((Integer) spec[0], text);
        ((LinearLayout.LayoutParams) ic.getLayoutParams()).rightMargin = px(10);
        tile.addView(ic);
        LinearLayout tc = column();
        TextView t = text(title, 13f, text);
        t.setTypeface(Typeface.create(Typeface.DEFAULT, 600, false));
        t.setMaxLines(2);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setLineSpacing(0, 1.05f);
        tc.addView(t);
        final TextView st = text("", 9.5f, text3);
        st.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        st.setLetterSpacing(0.1f);
        st.setAllCaps(true);
        st.setPadding(0, px(2), 0, 0);
        // El estado no se escribe: lo dicen el icono (de color si está encendida) y el borde. Sigue
        // existiendo para TalkBack (stateDescription) y para paintTile.
        st.setVisibility(View.GONE);
        tc.addView(st);
        tile.addView(tc, new LinearLayout.LayoutParams(0, -2, 1f));

        final boolean[] on = {false};
        final Runnable refresh = new Runnable() {
            @Override
            public void run() {
                on[0] = stored(it) >= 0.5f;
                paintTile(tile, ic, spot, edge, st, on[0], false);
            }
        };
        refresh.run();
        refreshers.add(refresh);
        tile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                on[0] = !on[0];
                v.performHapticFeedback(Build.VERSION.SDK_INT >= 34
                        ? (on[0] ? HapticFeedbackConstants.TOGGLE_ON : HapticFeedbackConstants.TOGGLE_OFF)
                        : HapticFeedbackConstants.KEYBOARD_TAP);
                // El círculo sale del punto donde has tocado (o del centro, con TalkBack).
                boolean touched = lastTouch == tile && SystemClock.uptimeMillis() - lastTouchAt < 1500;
                spot.at(touched ? lastTouchX : -1f, touched ? lastTouchY : -1f);
                paintTile(tile, ic, spot, edge, st, on[0], true);
                ripple(tile);
                saveToggle(key, on[0]);
            }
        });
        if (about != null) {
            tile.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    showInfo(info, key, title, about);
                    return true;
                }
            });
        }
        tile.setContentDescription(title);
        // Para TalkBack es un interruptor (encendido/apagado), no un botón cualquiera; y la
        // pulsación larga se anuncia como «ver qué hace».
        tile.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override
            public void onInitializeAccessibilityNodeInfo(View host, android.view.accessibility.AccessibilityNodeInfo n) {
                super.onInitializeAccessibilityNodeInfo(host, n);
                n.setClassName(android.widget.Switch.class.getName());
                n.setCheckable(true);
                n.setChecked(on[0]);
                if (about != null) {
                    n.addAction(new android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction(
                            android.view.accessibility.AccessibilityNodeInfo.ACTION_LONG_CLICK,
                            I18n.t("ver qué hace", "see what it does")));
                }
            }
        });
        sink(tile);
        featureTiles.add(tile);
        return tile;
    }

    /** Las baldosas de Inicio (para la onda de {@link #ripple}). */
    private final List<View> featureTiles = new ArrayList<>();

    /**
     * Onda por la rejilla: al tocar una baldosa, las demás se hunden un poquito y vuelven, una
     * detrás de otra según lo lejos que estén (el «stagger desde el elemento tocado» de GSAP). Solo
     * cambia la escala, así que no cuesta nada.
     */
    private void ripple(View from) {
        if (!ValueAnimator.areAnimatorsEnabled()) return;
        int[] a = new int[2], b = new int[2];
        from.getLocationInWindow(a);
        float fx = a[0] + from.getWidth() / 2f, fy = a[1] + from.getHeight() / 2f;
        for (final View t : featureTiles) {
            if (t == from || !t.isAttachedToWindow()) continue;
            t.getLocationInWindow(b);
            float d = (float) Math.hypot(b[0] + t.getWidth() / 2f - fx, b[1] + t.getHeight() / 2f - fy) / dp;
            // Más lejos, más tarde y más suave.
            final float k = 1f - 0.035f * Math.max(0.35f, 1f - d / 420f);
            t.animate().scaleX(k).scaleY(k).setStartDelay(Math.round(d * 0.9f)).setDuration(130)
                    .setInterpolator(EASE).withEndAction(new Runnable() {
                @Override
                public void run() {
                    t.animate().scaleX(1f).scaleY(1f).setStartDelay(0).setDuration(460)
                            .setInterpolator(Motion.BACK_OUT).start();
                }
            }).start();
        }
    }

    /**
     * Enseña (o esconde, si ya era esa) la explicación de una función en el cuadro de debajo de
     * la rejilla. Entra con el texto «descifrándose» para que se note que ha cambiado.
     */
    private void showInfo(final TextView info, String key, String title, String about) {
        if (info.getVisibility() == View.VISIBLE && key.equals(info.getTag())) {
            info.setTag(null);
            info.animate().alpha(0f).translationY(-px(6)).setDuration(180).withEndAction(new Runnable() {
                @Override
                public void run() {
                    info.setVisibility(View.GONE);
                }
            }).start();
            return;
        }
        info.setTag(key);
        android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder(title);
        sb.setSpan(new android.text.style.ForegroundColorSpan(accent), 0, title.length(), 0);
        sb.setSpan(new android.text.style.StyleSpan(Typeface.BOLD), 0, title.length(), 0);
        sb.append("  ").append(about);
        info.setText(sb);
        info.animate().cancel();
        if (info.getVisibility() != View.VISIBLE) {
            info.setVisibility(View.VISIBLE);
            info.setAlpha(0f);
            info.setTranslationY(-px(8));
        }
        info.animate().alpha(1f).translationY(0f).setDuration(420).setInterpolator(Motion.POWER4_OUT).withEndAction(null).start();
    }

    /**
     * Pinta una baldosa encendida o apagada. Con {@code animate}, el color entra como un círculo
     * que se abre desde el dedo hasta cubrirla (y al apagar se cierra hacia él), como los
     * «clip-path» circulares que animan las webs con GSAP; el borde de color aparece detrás.
     */
    private void paintTile(View tile, ImageView ic, final Spot spot, final Drawable edge, TextView st, boolean on,
                           boolean animate) {
        lightIcon(ic, on, animate);
        st.setText(on ? I18n.t("● Activado", "● On") : I18n.t("Apagado", "Off"));
        st.setTextColor(on ? accent : text3);
        tile.setStateDescription(on ? I18n.t("Activado", "On") : I18n.t("Apagado", "Off"));
        final float to = on ? 1f : 0f;
        if (spot.anim != null) spot.anim.cancel();
        if (!animate) {
            spot.set(to);
            edge.setAlpha(Math.round(255 * to));
            return;
        }
        ValueAnimator va = ValueAnimator.ofFloat(spot.t, to);
        va.setDuration(on ? 620 : 420);
        va.setInterpolator(on ? Motion.POWER4_OUT : Motion.POWER3_OUT);
        va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                float v = (Float) a.getAnimatedValue();
                spot.set(v);
                // El borde llega cuando el círculo ya casi ha llenado la baldosa.
                edge.setAlpha(Math.round(255 * Motion.clamp01((v - 0.55f) / 0.45f)));
            }
        });
        spot.anim = va;
        va.start();
    }

    /**
     * El color de una baldosa encendida: un círculo que crece desde un punto hasta cubrir toda la
     * baldosa (recortado por sus esquinas redondeadas). {@link #t} va de 0 (nada) a 1 (llena).
     */
    private static final class Spot extends Drawable {
        private final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.Path clip = new android.graphics.Path();
        private final float radius;
        private float cx = -1f, cy = -1f;
        float t;
        ValueAnimator anim;

        Spot(int color, float radius) {
            paint.setColor(color);
            this.radius = radius;
        }

        /** De dónde sale el círculo (coordenadas de la baldosa); negativo = del centro. */
        void at(float x, float y) {
            cx = x;
            cy = y;
        }

        void set(float v) {
            t = v;
            invalidateSelf();
        }

        @Override
        protected void onBoundsChange(android.graphics.Rect b) {
            clip.reset();
            clip.addRoundRect(new android.graphics.RectF(b), radius, radius, android.graphics.Path.Direction.CW);
        }

        @Override
        public void draw(android.graphics.Canvas c) {
            if (t <= 0f) return;
            android.graphics.Rect b = getBounds();
            float x = cx < 0 ? b.exactCenterX() : cx, y = cy < 0 ? b.exactCenterY() : cy;
            // Radio para llegar a la esquina más lejana: con t = 1 la baldosa queda llena.
            float far = (float) Math.hypot(Math.max(x - b.left, b.right - x), Math.max(y - b.top, b.bottom - y));
            c.save();
            c.clipPath(clip);
            c.drawCircle(x, y, far * t, paint);
            c.restore();
        }

        @Override
        public void setAlpha(int a) {
        }

        @Override
        public void setColorFilter(android.graphics.ColorFilter f) {
        }

        @Override
        public int getOpacity() {
            return android.graphics.PixelFormat.TRANSLUCENT;
        }
    }

    /** Tarjeta con los interruptores de cada función (Inicio y la presentación). */
    LinearLayout featuresCard() {
        LinearLayout f = card();
        f.setTag(Motion.CASCADE);
        f.addView(toggleRow(Icons.SPARKLE, item(Tune.ITEMS, "icehome_genie_on"), I18n.t("Abrir y cerrar apps", "Opening and closing apps")));
        f.addView(toggleRow(Icons.LAYERS, item(Tune.ITEMS, "icehome_curtain_on"), I18n.t("Recientes que se doblan al deslizarlas", "Recents that bend as you swipe")));
        f.addView(toggleRow(Icons.WIDGETS, item(Tune.ITEMS, "icehome_stack_on"), I18n.t("Pila de widgets con baraja", "Shuffling widget stack")));
        f.addView(toggleRow(Icons.FOLDER, item(Tune.ITEMS, "icehome_folder_morph_on"), I18n.t("Carpetas con muelle", "Springy folders")));
        f.addView(toggleRow(Icons.DROP, item(Tune.ITEMS, "icehome_folder_glass_on"), I18n.t("Cristal en las carpetas", "Glass folders")));
        f.addView(toggleRow(Icons.LIST, item(Tune.ITEMS, "icehome_menu_on"), I18n.t("Menú de atajos con muelle", "Springy shortcut menu")));
        f.addView(toggleRow(Icons.DROP, item(Tune.ITEMS, "icehome_dock_glass_on"), I18n.t("Dock de cristal", "Glass dock")));
        f.addView(toggleRow(Icons.CURTAIN, item(com.icehome.up.telon.Tune.ITEMS, "telon_on"), I18n.t("Telón al cambiar de pantalla", "Curtain when switching screens")));
        f.addView(toggleRow(Icons.PHONE, item(com.icehome.up.telon.Tune.ITEMS, "telon_squeeze_on"), I18n.t("Pantalla que se encoge al hacer una captura", "Screen that squeezes on screenshot")));
        f.addView(toggleRow(Icons.SLIDERS, item(com.icehome.up.cc.Tune.ITEMS, "kashiplus_cc_on"), I18n.t("Cristal en el centro de control", "Glass control center")));
        return f;
    }

    private ScrollView desktopTab() {
        LinearLayout c = column();
        c.addView(searchRow());
        c.addView(label(I18n.t("Animaciones", "Animations")));
        LinearLayout g = card();
        for (Section s : sections(Tune.ITEMS)) {
            if (s.name.startsWith("Funciones")) continue;            // están en Inicio
            if (s.name.startsWith("Pausa")) continue;                // es de todo el módulo: va en Sistema
            g.addView(sectionRow(s));
        }
        c.addView(g);
        // La pila de widgets no tiene ajustes propios (solo se enciende o se apaga), así que no
        // tiene página: va aquí como interruptor, para que el launcher tenga todas sus funciones.
        c.addView(label(I18n.t("Widgets", "Widgets")));
        LinearLayout wg = card();
        wg.addView(toggleRow(Icons.WIDGETS, item(Tune.ITEMS, "icehome_stack_on"), I18n.t("Pila de widgets", "Widget stack")));
        c.addView(wg);
        return page(I18n.t("Escritorio", "Launcher"), I18n.t("Lo que pasa en la pantalla de inicio: abrir apps, recientes, carpetas y widgets.",
                "What happens on your home screen: opening apps, recents, folders and widgets."), false, c);
    }

    private ScrollView systemTab() {
        LinearLayout c = column();
        c.addView(searchRow());
        c.addView(label("Android"));
        LinearLayout an = card();
        an.addView(systemSpeedRow());
        c.addView(an);
        c.addView(label("HyperSpring"));
        LinearLayout ap = card();
        for (Section s : sections(Tune.ITEMS)) {
            if (s.name.startsWith("Pausa")) ap.addView(sectionRow(s));
        }
        c.addView(ap);
        c.addView(label(I18n.t("Transiciones", "Transitions")));
        LinearLayout t = card();
        for (Section s : sections(com.icehome.up.telon.Tune.ITEMS)) {
            if (s.name.startsWith("Función")) continue;
            t.addView(sectionRow(s));
        }
        c.addView(t);
        c.addView(label(I18n.t("Estilos de cristal", "Glass styles")));
        c.addView(presetCard(Presets.GLASS, Icons.DROP));
        c.addView(label(I18n.t("Centro de control", "Control center")));
        LinearLayout cc = card();
        for (Section s : sections(com.icehome.up.cc.Tune.ITEMS)) {
            if (s.name.equals("General")) continue;
            cc.addView(sectionRow(s));
        }
        c.addView(cc);
        return page(I18n.t("Sistema", "System"), I18n.t("El telón al cambiar de pantalla, la captura y el cristal del centro de control.",
                "The curtain between screens, the screenshot and the control center glass."), false, c);
    }

    private ScrollView aboutTab() {
        LinearLayout c = column();
        c.addView(hero());

        c.addView(warning(I18n.t("HyperSpring solo se ha probado en un Redmi 14C con HyperOS 3 (Android 16). No puedo "
                        + "asegurar que funcione en otros dispositivos. Si algo falla, abre un issue en GitHub con tu móvil, "
                        + "la versión de HyperOS y del launcher, y el registro (botón de abajo).",
                "HyperSpring has only been tested on a Redmi 14C with HyperOS 3 (Android 16). I can't guarantee it "
                        + "works on other devices. If something breaks, open an issue on GitHub with your phone, HyperOS and "
                        + "launcher versions, and the log (button below).")));
        LinearLayout lb = new LinearLayout(this);
        lb.setPadding(0, px(12), 0, 0);
        lb.addView(button(I18n.t("Informar de un error", "Report a bug"), true, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                reportBug();
            }
        }), new LinearLayout.LayoutParams(-1, -2));
        c.addView(lb);

        c.addView(label(I18n.t("Compatibilidad", "Compatibility")));
        LinearLayout k = glassCard();
        k.addView(infoRow(Icons.PHONE, I18n.t("Este móvil", "This phone"), Build.MANUFACTURER + " " + Build.MODEL + " · " + systemVersion()));
        k.addView(statusRow(Icons.HOME, I18n.t("Escritorio", "Launcher"), appVersion(Launchers.current(this)), "launcher_seen"));
        String fx = launcherFx();
        if (fx != null) k.addView(infoRow(Icons.SPARKLE, I18n.t("Efectos del escritorio", "Launcher effects"), fx));
        k.addView(statusRow(Icons.CURTAIN, I18n.t("Interfaz del sistema", "System UI"), appVersion("com.android.systemui"), "systemui_seen"));
        k.addView(statusRow(Icons.PHONE, I18n.t("App de capturas", "Screenshot app"), appVersion("com.miui.screenshot"), "shot_seen"));
        String cc = state("cc_seen");
        String ccKind = cc == null ? null : cc.endsWith("|ios") ? I18n.t("mod iOS Control Center", "iOS Control Center mod")
                : I18n.t("centro de control de serie", "stock control center");
        String plugin = appVersion("miui.systemui.plugin");
        k.addView(statusRow(Icons.TOGGLES, I18n.t("Centro de control", "Control center"),
                ccKind != null ? ccKind + (plugin != null ? " · " + plugin : "") : plugin, "cc_seen"));
        c.addView(k);
        TextView hint = text(I18n.t("«Activo»: HyperSpring se ha cargado ahí desde el último reinicio. Si acabas de instalarlo "
                        + "o actualizarlo, reinicia el launcher y la interfaz desde Inicio (o el móvil).",
                "\u201cActive\u201d: HyperSpring has loaded there since the last reboot. If you just installed or updated "
                        + "it, restart the launcher and System UI from Home (or reboot)."), 12.5f, text3);
        hint.setPadding(px(8), px(10), px(8), 0);
        c.addView(hint);

        c.addView(label(I18n.t("Requisitos", "Requirements")));
        LinearLayout q = glassCard();
        q.addView(infoRow(Icons.SHIELD, I18n.t("Root y LSPosed", "Root and LSPosed"),
                I18n.t("Magisk, KernelSU o APatch con LSPosed (o un fork actual como JingMatrix/Vector).",
                        "Magisk, KernelSU or APatch with LSPosed (or a current fork such as JingMatrix/Vector).")));
        q.addView(infoRow(Icons.PHONE, I18n.t("HyperOS 2 o 3", "HyperOS 2 or 3"),
                I18n.t("Android 13 o superior. El cristal y la refracción necesitan Android 13+.",
                        "Android 13 or newer. The glass and refraction need Android 13+.")));
        q.addView(infoRow(Icons.LIST, I18n.t("Ámbitos en LSPosed", "LSPosed scopes"),
                I18n.t("Launcher del sistema (com.miui.home) o POCO Launcher (com.mi.android.globallauncher) e Interfaz del sistema (com.android.systemui). "
                                + "El centro de control (miui.systemui.plugin) se engancha desde la interfaz.",
                        "System launcher (com.miui.home) or POCO Launcher (com.mi.android.globallauncher) and System UI (com.android.systemui). "
                                + "The control center (miui.systemui.plugin) is hooked from System UI.")));
        q.addView(infoRow(Icons.PHONE, I18n.t("Probado con", "Tested with"),
                I18n.t("Redmi 14C (Helio G81 Ultra) con HyperOS 3 y los mods de Kashi de Mods Center: HyperOS Launcher "
                                + "Mod 7.5 e iOS Control Center. También funciona con el launcher y el centro de control de serie.",
                        "Redmi 14C (Helio G81 Ultra) on HyperOS 3 with Kashi's Mods Center mods: HyperOS Launcher Mod 7.5 "
                                + "and iOS Control Center. Also works with the stock launcher and control center.")));
        c.addView(q);

        c.addView(label(I18n.t("Créditos", "Credits")));
        LinearLayout cr = glassCard();
        cr.addView(infoRow(Icons.PALETTE, I18n.t("Refracción del borde", "Edge refraction"),
                I18n.t("Lente de Kyant0/AndroidLiquidGlass (Apache-2.0), portada a los sombreadores del cristal y de esta barra.",
                        "Lens from Kyant0/AndroidLiquidGlass (Apache-2.0), ported to the glass shaders and this bar.")));
        cr.addView(infoRow(Icons.CODE, "Mods Center", I18n.t("Los mods de launcher y centro de control sobre los que se ha probado son de Kashi.",
                "The launcher and control center mods it was tested with are by Kashi.")));
        c.addView(cr);

        c.addView(label(I18n.t("Ajustes", "Settings")));
        LinearLayout r = glassCard();
        final String mode = I18n.mode(this);
        String langNow = "es".equals(mode) ? "Español" : "en".equals(mode) ? "English" : I18n.t("Automático (idioma del sistema)", "Automatic (system language)");
        r.addView(navRow(Icons.GLOBE, I18n.t("Idioma", "Language"), langNow, false, new Runnable() {
            @Override
            public void run() {
                // automático → español → inglés → automático
                I18n.setMode(SettingsActivity.this, "auto".equals(mode) ? "es" : "es".equals(mode) ? "en" : "auto");
                recreate();
            }
        }));
        r.addView(navRow(Icons.PLAY, I18n.t("Ver la presentación", "Replay the tour"),
                I18n.t("La bienvenida animada y la configuración guiada", "The animated welcome and the guided setup"), false, new Runnable() {
            @Override
            public void run() {
                showOnboarding();
            }
        }));
        final View check = navRow(Icons.DOWNLOAD, I18n.t("Buscar actualizaciones", "Check for updates"),
                I18n.t("Tienes la " + Version.NAME, "You have " + Version.NAME), false, null);
        // El subtítulo de la fila hace de estado: «Buscando…», «Estás al día»…
        final TextView checkStatus = (TextView) ((LinearLayout) ((LinearLayout) check).getChildAt(1)).getChildAt(1);
        check.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                checkUpdates(checkStatus);
            }
        });
        r.addView(check);
        r.addView(prefToggleRow(Icons.RESTART, I18n.t("Buscar al abrir la app", "Check when opening the app"),
                I18n.t("Como mucho cada 6 horas. Solo pregunta a GitHub por la última versión.",
                        "At most every 6 hours. It only asks GitHub for the latest version."), "upd_auto", true));
        // Antes eran cuatro filas (exportar, importar, compartir, pegar): ahora una para sacar los
        // ajustes y otra para traerlos, que mira sola si hay un código HS1: copiado.
        r.addView(navRow(Icons.LIST, I18n.t("Guardar o compartir ajustes", "Save or share settings"),
                I18n.t("En un archivo (adjúntalo si reportas un error) o en un código corto para Telegram, Reddit…",
                        "To a file (attach it when you report a bug) or as a short code for Telegram, Reddit…"), false, new Runnable() {
            @Override
            public void run() {
                new android.app.AlertDialog.Builder(SettingsActivity.this)
                        .setTitle(I18n.t("Guardar o compartir", "Save or share"))
                        .setItems(new String[]{
                                I18n.t("Guardar en un archivo", "Save to a file"),
                                I18n.t("Compartir un código (sin datos del móvil ni de tus apps)", "Share a code (no phone data, no app list)")
                        }, new android.content.DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(android.content.DialogInterface d, int which) {
                                if (which == 0) pickFile(true);
                                else shareSettings();
                            }
                        })
                        .show();
            }
        }));
        r.addView(navRow(Icons.DOWNLOAD, I18n.t("Cargar ajustes", "Load settings"),
                I18n.t("Si has copiado un código HS1:, lo usa; si no, eliges un archivo guardado",
                        "Uses a copied HS1: code if there is one; otherwise you pick a saved file"), false, new Runnable() {
            @Override
            public void run() {
                if (Backup.fromCode(clipText()) != null) pasteSettings();
                else pickFile(false);
            }
        }));
        r.addView(navRow(Icons.RESTART, I18n.t("Restablecer todo", "Reset everything"),
                I18n.t("Vuelve todos los ajustes a sus valores por defecto", "Puts every setting back to its default"), false, new Runnable() {
            @Override
            public void run() {
                getSharedPreferences(PrefsProvider.PREFS, 0).edit().clear().commit();
                send(Remote.TUNE);
                for (Runnable rf : refreshers) rf.run();
                toast(I18n.t("Valores por defecto. Reinicia el launcher y la interfaz para las funciones.",
                        "Defaults restored. Restart the launcher and System UI for the features."));
            }
        }));
        c.addView(r);
        glowNext = true;
        return page(null, null, false, c);
    }

    /**
     * Cabecera de «Acerca de», como la portada de una web: el logo, el nombre enorme entrando letra
     * a letra, la versión en una pastilla y, debajo, una cinta de texto que no para de pasar
     * ({@link Marquee}) y que se acelera al desplazar la página.
     */
    private View hero() {
        LinearLayout col = column();
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(0, px(56), 0, px(20));
        col.setClipToPadding(false);            // el logo flota y gira por encima del margen sin cortarse
        col.setClipChildren(false);
        final ImageView logo = new ImageView(this);
        logo.setImageDrawable(new LogoDrawable(LiveBackground.CORAL, 0xFF));
        col.addView(logo, new LinearLayout.LayoutParams(px(84), px(84)));
        floatAndSpin(logo);

        SplitTitle name = new SplitTitle(this, "HyperSpring", 50, text, 800);
        name.setCentered(true);
        name.setPadding(0, px(18), 0, 0);
        col.addView(name, new LinearLayout.LayoutParams(-1, -2));
        // El nombre de «Acerca de» no está en la columna de la página, así que la entrada
        // escalonada no lo ve: arranca él solo.
        name.play(220);

        TextView sub = text(I18n.t("Animaciones y cristal líquido para HyperOS", "Animations and liquid glass for HyperOS"), 14, text2);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(px(24), px(6), px(24), 0);
        col.addView(sub, new LinearLayout.LayoutParams(-1, -2));

        TextView ver = text("v" + version(), 12, accent);
        ver.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        ver.setPadding(px(10), px(4), px(10), px(4));
        GradientDrawable vg = round(accentSoft, px(100));
        vg.setStroke(Math.max(1, px(0.8f)), (accent & 0x00FFFFFF) | 0x59000000);
        ver.setBackground(vg);
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(-2, -2);
        vlp.topMargin = px(14);
        col.addView(ver, vlp);

        // La cinta ocupa todo el ancho de la pantalla, saliéndose del margen de la página.
        // Sin «HyperSpring»: justo debajo del nombre grande, sus trozos cortados por el borde
        // («perSpring») parecían el título repetido y roto.
        Marquee band = new Marquee(this, new String[]{I18n.t("Animaciones", "Animations"),
                I18n.t("Cristal líquido", "Liquid glass"), I18n.t("Muelles", "Springs"), "HyperOS"}, accent);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, -2);
        blp.topMargin = px(34);
        blp.leftMargin = -px(16);
        blp.rightMargin = -px(16);
        col.addView(band, blp);
        glowMarquee = band;
        return col;
    }

    /**
     * El logo de «Acerca de» flota despacio arriba y abajo; al tocarlo da una vuelta con un muelle.
     * El vaivén solo corre mientras la página está en pantalla.
     */
    private void floatAndSpin(final View logo) {
        final android.animation.ObjectAnimator fl = android.animation.ObjectAnimator.ofFloat(logo, View.TRANSLATION_Y, -px(5), px(5));
        fl.setDuration(2600);
        fl.setRepeatMode(ValueAnimator.REVERSE);
        fl.setRepeatCount(ValueAnimator.INFINITE);
        fl.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
        logo.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                fl.start();
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                fl.cancel();
            }
        });
        sink(logo);
        logo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
                float from = v.getRotation() % 360f;
                ValueAnimator spin = ValueAnimator.ofFloat(from, 360f);
                Spring.apply(spin, from, 360f, 0f, 0.7f, 0.6f);
                spin.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator a) {
                        logo.setRotation((Float) a.getAnimatedValue());
                    }
                });
                spin.start();
            }
        });
    }

    /** Tarjeta de «Acerca de»: la misma que el resto (antes era de cristal sobre el fondo de colores). */
    private LinearLayout glassCard() {
        return card();
    }

    /** Fila de compatibilidad: versión de la app enganchada y si HyperSpring está activo ahí. */
    private View statusRow(int iconKind, String title, String version, String stateKey) {
        LinearLayout r = rowBase();
        r.setBackground(null);
        r.addView(icon(iconKind, text));
        String st = state(stateKey);
        long when = 0;
        String ver = null, engine = null;
        if (st != null) {
            String[] parts = st.split("\\|");
            try {
                when = Long.parseLong(parts[0]);
            } catch (Throwable ignored) {
            }
            if (parts.length > 1) ver = parts[1];
            // Tercer dato (desde la 2.0): con qué API de Xposed se cargó el módulo ahí.
            if (parts.length > 2 && !"cc_seen".equals(stateKey) && when >= bootTime()) engine = parts[2];
        }
        LinearLayout tc = titles(title, version != null ? version : I18n.t("No encontrado", "Not found"));
        if (tc.getChildCount() > 1) {
            TextView v = (TextView) tc.getChildAt(1);
            v.setMaxLines(1);
            v.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        }
        if (engine != null) {
            TextView e = text(engine, 12, text2);
            e.setMaxLines(1);
            e.setEllipsize(TextUtils.TruncateAt.END);
            tc.addView(e);
        }
        r.addView(tc);
        boolean active = when >= bootTime();
        boolean old = active && !"cc_seen".equals(stateKey) && ver != null && !ver.equals(Version.NAME);
        String label = !active ? (when > 0 ? I18n.t("Sin cargar", "Not loaded") : I18n.t("No detectado", "Not detected"))
                : old ? I18n.t("Reiniciar", "Restart") : I18n.t("Activo", "Active");
        // Estado, no botón: un punto de color y el texto, como la píldora de Inicio. Antes era una
        // pastilla coral rellena y parecía que se podía tocar (igual que «Copiar registro»).
        int dotColor = active && !old ? 0xFF6FD99A : active ? 0xFFFFB547 : text3;
        LinearLayout pill = new LinearLayout(this);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.addView(new PulseDot(this, dotColor, dp), new LinearLayout.LayoutParams(px(16), px(16)));
        TextView pt = text(label, 13, active && !old ? text : text2);
        pt.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        pt.setMaxLines(1);
        LinearLayout.LayoutParams ptl = new LinearLayout.LayoutParams(-2, -2);
        ptl.leftMargin = px(4);
        pill.addView(pt, ptl);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.leftMargin = px(12);
        r.addView(pill, lp);
        return r;
    }

    /** Página de una sección: sus ajustes y «Restablecer esta sección». */
    private ScrollView sectionPage(final Section s) {
        LinearLayout c = column();
        c.addView(space(16));
        // En «Abrir y cerrar apps», la gráfica del muelle va encima de los ajustes.
        springGraph = null;
        if (s.items.contains(item(Tune.ITEMS, "icehome_anim_speed"))) {
            LinearLayout g = card();
            g.setPadding(px(16), px(14), px(16), px(14));
            TextView gt = text(I18n.t("Así se mueve la ventana", "How the window moves"), 13, accent);
            gt.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            gt.setPadding(0, 0, 0, px(8));
            g.addView(gt);
            springGraph = new SpringGraph(this, accent, text2, text3);
            graphSpeed = stored(item(Tune.ITEMS, "icehome_anim_speed"));
            graphBounce = stored(item(Tune.ITEMS, "icehome_anim_bounce"));
            springGraph.set(graphSpeed, graphBounce, true);
            g.addView(springGraph, new LinearLayout.LayoutParams(-1, -2));
            TextView gh = text(I18n.t("El pico por encima de la línea de puntos es el rebote.",
                    "The peak above the dotted line is the bounce."), 12.5f, text3);
            gh.setPadding(0, px(8), 0, px(4));
            g.addView(gh);
            // Velocidad y Rebote van aquí, pegados a la gráfica: así ves la curva mientras los mueves.
            // Las filas traen su propio margen lateral; se lo quitamos al relleno de la tarjeta.
            g.setPadding(px(16), px(14), px(16), px(4));
            g.setClipToPadding(false);              // si no, el margen negativo de las filas corta su número
            for (String key : new String[]{"icehome_anim_speed", "icehome_anim_bounce"}) {
                View row = settingRow(item(Tune.ITEMS, key));
                LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, -2);
                rl.leftMargin = -px(16);
                rl.rightMargin = -px(16);
                g.addView(row, rl);
            }
            c.addView(g);
            c.addView(space(14));
            // Apps que se abren con la animación normal de HyperOS.
            LinearLayout ex = card();
            final TextView[] sub = new TextView[1];
            View exRow = navRow(Icons.TOGGLES, I18n.t("Apps sin genio", "Apps without genie"), excludeSummary(), true,
                    new Runnable() {
                        @Override
                        public void run() {
                            push(excludePage(sub[0]));
                        }
                    });
            // El subtítulo de la fila, para actualizarlo al volver de la lista.
            sub[0] = (TextView) ((ViewGroup) ((ViewGroup) exRow).getChildAt(1)).getChildAt(1);
            ex.addView(exRow);
            c.addView(ex);
            c.addView(space(14));
        }
        // Captura: sin HyperSpring en la app de capturas, la vista previa de MIUI tapa el marco y
        // la pantalla se encoge tarde (plan B). Se avisa si no se ha cargado ahí desde el reinicio.
        if (s.items.contains(item(com.icehome.up.telon.Tune.ITEMS, "telon_squeeze_size")) && shotMissing()) {
            c.addView(warning(I18n.t("HyperSpring no se ha cargado en la app de capturas desde el último reinicio. Sin ella, "
                            + "la vista previa de MIUI tapa el marco y la pantalla se encoge tarde. En LSPosed, marca "
                            + "«Captura de pantalla» (com.miui.screenshot) en el ámbito de HyperSpring y haz una captura. "
                            + "Si ya está marcado, este aviso se va tras la primera captura.",
                    "HyperSpring hasn't loaded in the screenshot app since the last reboot. Without it, MIUI's preview "
                            + "covers the frame and the screen shrinks late. In LSPosed, tick “Screenshot” "
                            + "(com.miui.screenshot) in HyperSpring's scope and take a screenshot. If it is already "
                            + "ticked, this notice goes away after the first screenshot.")));
            c.addView(space(14));
        }
        LinearLayout k = card();
        for (Object[] it : s.items) {
            String key = (String) it[0];
            // Si ya están en la tarjeta de la gráfica, no se repiten.
            if (springGraph != null && ("icehome_anim_speed".equals(key) || "icehome_anim_bounce".equals(key))) continue;
            final View row = settingRow(it);
            k.addView(row);
            // Solo lo que sirve para el estilo elegido: Estrechamiento y Desenfoque de movimiento son de
            // Profundidad, e Intensidad (cuánto se deforma) no se usa en Profundidad.
            final boolean depthOnly = key.startsWith("icehome_depth_");
            if (depthOnly || "icehome_genie_strength".equals(key)) {
                Runnable vis = new Runnable() {
                    @Override
                    public void run() {
                        boolean depth = Math.round(stored(item(Tune.ITEMS, "icehome_genie_style"))) == Genie.STYLE_DEPTH;
                        row.setVisibility(depth == depthOnly ? View.VISIBLE : View.GONE);
                    }
                };
                vis.run();
                refreshers.add(vis);
            }
        }
        c.addView(k);
        TextView hint = text(I18n.t("Toca una fila para leer qué hace. Mantén pulsada para volver a su valor por defecto.",
                "Tap a row to read what it does. Long-press it to reset it to its default."), 13, text3);
        hint.setPadding(px(10), px(12), px(10), 0);
        c.addView(hint);
        LinearLayout b = new LinearLayout(this);
        b.setPadding(0, px(18), 0, 0);
        b.addView(button(I18n.t("Restablecer esta sección", "Reset this section"), false, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                for (Object[] it : s.items) write((String) it[0], null);
                for (Runnable r : refreshers) r.run();
                toast(I18n.t("Valores por defecto", "Defaults restored"));
            }
        }), new LinearLayout.LayoutParams(-1, -2));
        c.addView(b);
        return page(shortName(I18n.section(s.name)), null, true, c);
    }

    // ---------------------------------------------------------------- buscador
    /** Fila que abre el buscador; va arriba de Escritorio y de Sistema. */
    private View searchRow() {
        LinearLayout s = card();
        s.addView(navRow(Icons.SLIDERS, I18n.t("Buscar un ajuste", "Find a setting"),
                I18n.t("Todos los ajustes del launcher y del sistema en un sitio", "Every launcher and system setting in one place"),
                true, new Runnable() {
                    @Override
                    public void run() {
                        push(searchPage());
                    }
                }));
        return s;
    }

    /**
     * Todas las filas de las tres tablas de ajustes en una lista que se filtra mientras escribes
     * (por nombre, explicación o sección). Las filas son las de siempre: se cambian aquí mismo.
     */
    private ScrollView searchPage() {
        LinearLayout c = column();
        c.addView(space(16));
        final android.widget.EditText search = new android.widget.EditText(this);
        search.setHint(I18n.t("Genio, rebote, cristal…", "Genie, bounce, glass…"));
        search.setHintTextColor(text3);
        search.setTextColor(text);
        search.setTextSize(15);
        search.setSingleLine(true);
        search.setBackground(round(card, px(18)));
        search.setPadding(px(18), px(12), px(18), px(12));
        c.addView(search, new LinearLayout.LayoutParams(-1, -2));
        c.addView(space(12));

        final LinearLayout list = card();
        final TextView none = text(I18n.t("Escribe para buscar entre todos los ajustes.", "Type to search every setting."), 14, text3);
        none.setPadding(px(18), px(18), px(18), px(18));
        list.addView(none);
        final List<View> rows = new ArrayList<>();
        final List<String> words = new ArrayList<>();      // lo que se busca de cada fila, en minúsculas
        for (Object[][] table : new Object[][][]{Tune.ITEMS, com.icehome.up.telon.Tune.ITEMS, com.icehome.up.cc.Tune.ITEMS}) {
            for (Section s : sections(table)) {
                String sec = I18n.section(s.name);
                for (Object[] it : s.items) {
                    View row = settingRow(it);
                    row.setVisibility(View.GONE);
                    list.addView(row);
                    rows.add(row);
                    String d = I18n.desc(it);
                    words.add((I18n.title(it) + " " + (d == null ? "" : d) + " " + sec).toLowerCase(java.util.Locale.ROOT));
                }
            }
        }
        c.addView(list);
        search.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int n) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int n) {
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
                String q = s.toString().trim().toLowerCase(java.util.Locale.ROOT);
                int shown = 0;
                for (int i = 0; i < rows.size(); i++) {
                    boolean on = !q.isEmpty() && words.get(i).contains(q);
                    rows.get(i).setVisibility(on ? View.VISIBLE : View.GONE);
                    if (on) shown++;
                }
                none.setText(q.isEmpty() ? I18n.t("Escribe para buscar entre todos los ajustes.", "Type to search every setting.")
                        : I18n.t("Nada con «" + q + "».", "Nothing matches “" + q + "”."));
                none.setVisibility(shown == 0 ? View.VISIBLE : View.GONE);
            }
        });
        search.requestFocus();
        return page(I18n.t("Buscar", "Search"), null, true, c);
    }

    // ---------------------------------------------------------------- registro
    private TextView logText;
    private ScrollView logPage;

    private ScrollView logPage() {
        LinearLayout c = column();
        c.addView(space(16));
        LinearLayout k = card();
        logText = text("", 11.5f, text2);
        logText.setTypeface(Typeface.MONOSPACE);
        logText.setTextIsSelectable(true);
        logText.setLineSpacing(0, 1.2f);
        logText.setPadding(px(18), px(16), px(18), px(16));
        k.addView(logText);
        c.addView(k);
        LinearLayout b = new LinearLayout(this);
        b.setPadding(0, px(16), 0, 0);
        b.addView(button(I18n.t("Borrar", "Clear"), false, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getContentResolver().delete(PrefsProvider.LOG, null, null);
                refreshLog();
            }
        }), weight(px(10)));
        b.addView(button(I18n.t("Copiar", "Copy"), true, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                copyLog();
            }
        }), weight(0));
        c.addView(b);
        refreshLog();
        logPage = page(I18n.t("Registro", "Log"), null, true, c);
        return logPage;
    }

    private void refreshLog() {
        if (logText == null) return;
        String l = readLog();
        logText.setText(l.isEmpty() ? I18n.t("Vacío. Abre y cierra alguna app (o cambia de pantalla dentro de una) y vuelve aquí.",
                "Empty. Open and close an app (or switch screens inside one) and come back here.") : l);
        bar.invalidate();
    }

    /** Copia el registro con los datos del móvil delante (lo que hace falta en un issue). */
    private void copyLog() {
        String head = "HyperSpring " + version() + " · " + Build.MANUFACTURER + " " + Build.MODEL + " · " + systemVersion()
                + " · launcher " + appVersion(Launchers.current(this)) + "\n";
        android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        cm.setPrimaryClip(android.content.ClipData.newPlainText("HyperSpring", head + readLog()));
        toast(I18n.t("Registro copiado", "Log copied"));
    }

    /**
     * Informe de error en un toque: copia el registro (no cabe en un enlace) y abre un issue nuevo
     * en GitHub con el móvil, las versiones y los efectos activos ya escritos. El usuario lo revisa
     * y lo envía él; la app no manda nada por su cuenta.
     */
    private void reportBug() {
        // Filtros: los issues llegaban vacíos o de móviles donde el módulo no puede funcionar.
        if (!xiaomiOs()) {
            info(I18n.t("Móvil no compatible", "Phone not supported"),
                    I18n.t("HyperSpring solo funciona en Xiaomi, Redmi y POCO con HyperOS o MIUI. En este móvil no va a "
                                    + "funcionar, así que no hace falta abrir un informe.",
                            "HyperSpring only works on Xiaomi, Redmi and POCO with HyperOS or MIUI. It can't work on this "
                                    + "phone, so there's no need to open a report."));
            return;
        }
        if (state("launcher_seen") == null && state("systemui_seen") == null) {
            info(I18n.t("El módulo no está activo", "The module isn't active"),
                    I18n.t("HyperSpring todavía no se ha cargado en ningún sitio. Usa Vector 2.2 (o un LSPosed con la API "
                                    + "101/102), activa el módulo y sus ámbitos (launcher e Interfaz del sistema) y reinicia el móvil.",
                            "HyperSpring hasn't loaded anywhere yet. Use Vector 2.2 (or an LSPosed with API 101/102), "
                                    + "enable the module and its scopes (launcher and System UI) and reboot."));
            return;
        }
        final android.widget.EditText what = new android.widget.EditText(this);
        what.setHint(I18n.t("Qué pasa y cómo hacer que pase", "What happens and how to make it happen"));
        what.setMinLines(3);
        android.widget.FrameLayout box = new android.widget.FrameLayout(this);
        box.setPadding(px(20), px(8), px(20), 0);
        box.addView(what);
        final android.app.AlertDialog d = new android.app.AlertDialog.Builder(this)
                .setTitle(I18n.t("Informar de un error", "Report a bug"))
                .setView(box)
                .setNegativeButton(I18n.t("Cancelar", "Cancel"), null)
                .setPositiveButton(I18n.t("Abrir GitHub", "Open GitHub"), new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface di, int w) {
                        openIssue(what.getText().toString().trim());
                    }
                }).show();
        final android.widget.Button ok = d.getButton(android.app.AlertDialog.BUTTON_POSITIVE);
        ok.setEnabled(false);                       // sin texto no hay informe útil
        what.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int n) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int n) {
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
                ok.setEnabled(s.toString().trim().length() >= 15);
            }
        });
    }

    private void info(String title, String msg) {
        new android.app.AlertDialog.Builder(this).setTitle(title).setMessage(msg).setPositiveButton("OK", null).show();
    }

    /** El final del registro, cortado en un salto de línea, para que el enlace no pase del límite de GitHub. */
    static String logTail(String log, int max) {
        if (log.length() <= max) return log;
        String t = log.substring(log.length() - max);
        int nl = t.indexOf('\n');
        return nl >= 0 ? t.substring(nl + 1) : t;
    }

    /** Abre el issue con lo que escribió el usuario, el móvil y el final del registro ya puestos. */
    private void openIssue(String what) {
        copyLog();
        String fx = launcherFx();
        String title = what.length() > 70 ? what.substring(0, 70) + "…" : what;
        String body = "**" + I18n.t("Qué pasa", "What happens") + ":**\n" + what + "\n\n"
                + "---\n"
                + "- HyperSpring " + version() + "\n"
                + "- " + Build.MANUFACTURER + " " + Build.MODEL + " · " + systemVersion() + "\n"
                + "- Launcher " + appVersion(Launchers.current(this)) + " · System UI " + appVersion("com.android.systemui") + "\n"
                + (fx != null ? "- " + I18n.t("Efectos del escritorio", "Launcher effects") + ": " + fx + "\n" : "")
                + "\n**" + I18n.t("Registro (final; el entero está copiado)", "Log (end; the full one is copied)") + "**:\n\n```\n"
                + logTail(readLog(), 2500) + "\n```\n";
        try {
            startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(
                    "https://github.com/olilio3/HyperSpring/issues/new?title=" + android.net.Uri.encode(title)
                            + "&body=" + android.net.Uri.encode(body))));
        } catch (Throwable t) {
            toast(I18n.t("No hay navegador para abrir GitHub", "No browser to open GitHub"));
        }
    }

    /** «6 de 7 activos (fallan: …)» con lo que contó el launcher al arrancar, o null si aún no hay dato. */
    private String launcherFx() {
        String v = state("launcher_fx");
        if (v == null) return null;
        int bar = v.indexOf('|');
        String[] n = (bar < 0 ? v : v.substring(0, bar)).split("/");
        if (n.length != 2) return null;
        String out = I18n.t(n[0] + " de " + n[1] + " activos", n[0] + " of " + n[1] + " active");
        if (bar >= 0 && bar + 1 < v.length()) {
            StringBuilder names = new StringBuilder();
            for (String key : v.substring(bar + 1).split(", ")) {
                String title = key;
                for (Object[] it : Tune.ITEMS) if (it[0].equals(key)) title = I18n.title(it);
                names.append(names.length() > 0 ? ", " : "").append(title);
            }
            out += I18n.t(" (fallan: ", " (failing: ") + names + ")";
        }
        return out;
    }

    private String readLog() {
        StringBuilder sb = new StringBuilder();
        try (android.database.Cursor cu = getContentResolver().query(PrefsProvider.LOG, null, null, null, null)) {
            while (cu != null && cu.moveToNext()) sb.append(cu.getString(0)).append('\n');
        } catch (Throwable ignored) {
        }
        return sb.toString().trim();
    }

    // ---------------------------------------------------------------- secciones
    private static List<Section> sections(Object[][] items) {
        List<Section> out = new ArrayList<>();
        Section cur = null;
        for (Object[] it : items) {
            if ("#".equals(it[0])) {
                cur = new Section((String) it[1]);
                out.add(cur);
            } else {
                if (cur == null) {
                    cur = new Section("Ajustes");
                    out.add(cur);
                }
                cur.items.add(it);
            }
        }
        return out;
    }

    private static Object[] item(Object[][] items, String key) {
        for (Object[] it : items) if (it[0].equals(key)) return it;
        throw new IllegalArgumentException(key);
    }

    private static String shortName(String s) {
        int p = s.indexOf(" (");
        return p > 0 ? s.substring(0, p) : s;
    }

    private static int iconFor(String name) {
        String n = name.toLowerCase(java.util.Locale.ROOT);
        if (n.contains("abrir")) return Icons.SPARKLE;
        if (n.contains("recientes")) return Icons.LAYERS;
        if (n.contains("cristal de las carpetas")) return Icons.DROP;
        if (n.contains("carpeta")) return Icons.FOLDER;
        if (n.contains("menú")) return Icons.LIST;
        if (n.contains("telón")) return Icons.CURTAIN;
        if (n.contains("captura")) return Icons.PHONE;
        if (n.contains("gemini")) return Icons.LIGHT;
        if (n.contains("notificaciones")) return Icons.LIGHT;
        if (n.contains("pausa")) return Icons.TOGGLES;
        if (n.contains("interior")) return Icons.DROP;
        if (n.contains("refracción")) return Icons.PALETTE;
        if (n.contains("canto") || n.contains("luz")) return Icons.LIGHT;
        if (n.contains("fondo")) return Icons.GRID;
        return Icons.SLIDERS;
    }

    private View sectionRow(final Section s) {
        String sum = s.items.size() == 1 ? I18n.t("1 ajuste", "1 setting") : s.items.size() + I18n.t(" ajustes", " settings");
        return navRow(iconFor(s.name), shortName(I18n.section(s.name)), sum, true, new Runnable() {
            @Override
            public void run() {
                push(sectionPage(s));
            }
        });
    }

    // ---------------------------------------------------------------- filas
    private LinearLayout rowBase() {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        // Filas compactas: caben más ajustes por pantalla sin que se peguen al dedo.
        r.setPadding(px(14), px(10), px(14), px(10));
        r.setMinimumHeight(px(52));
        r.setBackground(new RippleDrawable(ColorStateList.valueOf(pressed), null, new ColorDrawable(Color.WHITE)));
        // Sin esto, el icono se corta en el borde del margen cuando crece o rebota.
        r.setClipToPadding(false);
        return r;
    }

    /**
     * Icono de una fila: blanco sobre una baldosa gris muy oscura. Todos iguales y sin color: el
     * acento se guarda para lo que está encendido (ver {@link #lightIcon}). El color que se pasa se
     * ignora.
     */
    private ImageView icon(int kind, int color) {
        ImageView iv = new ImageView(this);
        iv.setImageDrawable(new Icons(kind, ICON_OFF));
        iv.setPadding(px(7), px(7), px(7), px(7));
        GradientDrawable chip = round(0x12FFFFFF, px(9));
        chip.setStroke(Math.max(1, px(0.7f)), HAIRLINE);
        iv.setBackground(chip);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px(32), px(32));
        lp.rightMargin = px(13);
        iv.setLayoutParams(lp);
        return iv;
    }

    /** Color del dibujo de los iconos apagados. */
    private static final int ICON_OFF = 0xFFD8D8DE;

    /**
     * Enciende o apaga la baldosa del icono de una función. Encendida, se llena del color de acento
     * (liso) y el dibujo pasa a negro; al cambiar, da un saltito con muelle. Así se ve de un
     * vistazo qué está activo, sin leer los interruptores.
     */
    private void lightIcon(final ImageView iv, boolean on, boolean animate) {
        android.graphics.drawable.LayerDrawable layers;
        if (iv.getBackground() instanceof android.graphics.drawable.LayerDrawable) {
            layers = (android.graphics.drawable.LayerDrawable) iv.getBackground();
        } else {
            // Primera vez: debajo, la baldosa gris de siempre; encima, la encendida (invisible).
            GradientDrawable lit = round(accent, px(9));
            lit.setAlpha(0);
            layers = new android.graphics.drawable.LayerDrawable(new Drawable[]{iv.getBackground(), lit});
            iv.setBackground(layers);
        }
        final Drawable lit = layers.getDrawable(1);
        final Icons glyph = (Icons) iv.getDrawable();
        final float to = on ? 1f : 0f;
        if (iv.getTag() instanceof ValueAnimator) ((ValueAnimator) iv.getTag()).cancel();
        if (!animate) {
            lit.setAlpha(Math.round(255 * to));
            glyph.setColor(on ? Color.BLACK : ICON_OFF);
            return;
        }
        ValueAnimator va = ValueAnimator.ofFloat(lit.getAlpha() / 255f, to);
        va.setDuration(260);
        va.setInterpolator(EASE);
        va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                float v = (Float) a.getAnimatedValue();
                lit.setAlpha(Math.round(255 * v));
                glyph.setColor(Controls.blend(ICON_OFF, Color.BLACK, v));
            }
        });
        iv.setTag(va);
        va.start();
        // Al encender, el dibujo hace su gesto (la gota cae, la carpeta rebota...): ver Icons.play.
        if (on) glyph.play();
        // Saltito de la baldosa, pequeño: al encender ya se mueve el dibujo (su gesto), y con los
        // dos rebotando fuerte a la vez no se entendía ninguno. Al apagar se hunde un poco.
        final float peak = on ? 1.07f : 0.9f;
        iv.animate().scaleX(peak).scaleY(peak).setDuration(on ? 90 : 110).setInterpolator(EASE).withEndAction(new Runnable() {
            @Override
            public void run() {
                iv.animate().scaleX(1f).scaleY(1f).setDuration(380)
                        .setInterpolator(new android.view.animation.OvershootInterpolator(1.6f)).start();
            }
        }).start();
    }

    /**
     * Fila de acción que hay que mantener pulsada (reiniciar). Se llena de izquierda a derecha
     * mientras aprietas; al completarse vibra y actúa. Si sueltas antes, se vacía. Un toque corto
     * solo explica qué hacer, con un "no" de lado a lado: así no se reinicia nada sin querer.
     */
    private View holdRow(int iconKind, String title, final String sub, final Runnable action) {
        final LinearLayout r = rowBase();
        r.addView(icon(iconKind, text));
        LinearLayout tc = titles(title, sub);
        r.addView(tc);
        final TextView subView = (TextView) tc.getChildAt(1);
        // Relleno liso del acento, algo transparente para que se siga leyendo la fila.
        GradientDrawable shape = new GradientDrawable();
        shape.setColor((accent & 0x00FFFFFF) | 0x40000000);
        // ClipDrawable dibuja solo una parte de la forma: el "nivel" (0 a 10000) es cuánto se ve.
        final android.graphics.drawable.ClipDrawable fill = new android.graphics.drawable.ClipDrawable(
                shape, Gravity.LEFT, android.graphics.drawable.ClipDrawable.HORIZONTAL);
        fill.setLevel(0);
        r.setForeground(fill);

        final ValueAnimator.AnimatorUpdateListener level = new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                fill.setLevel((Integer) a.getAnimatedValue());
            }
        };
        final ValueAnimator[] running = {null};
        final long[] downAt = {0};
        final Runnable restoreSub = new Runnable() {
            @Override
            public void run() {
                subView.setText(sub);
                subView.setTextColor(text2);
            }
        };
        r.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(final View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN: {
                        downAt[0] = SystemClock.uptimeMillis();
                        v.animate().scaleX(0.97f).scaleY(0.97f).setDuration(140).setInterpolator(EASE).start();
                        v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                        if (running[0] != null) running[0].cancel();
                        int from = fill.getLevel();
                        ValueAnimator va = ValueAnimator.ofInt(from, 10000);
                        va.setDuration(Math.max(1, Math.round(900 * (10000 - from) / 10000f)));
                        va.setInterpolator(new android.view.animation.LinearInterpolator());
                        va.addUpdateListener(level);
                        va.addListener(new android.animation.AnimatorListenerAdapter() {
                            boolean cancelled;

                            @Override
                            public void onAnimationCancel(android.animation.Animator a) {
                                cancelled = true;
                            }

                            @Override
                            public void onAnimationEnd(android.animation.Animator a) {
                                if (cancelled) return;
                                v.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
                                v.animate().scaleX(1f).scaleY(1f).setStartDelay(0).setDuration(380)
                                        .setInterpolator(new android.view.animation.OvershootInterpolator(2.2f)).start();
                                action.run();
                                // Lleno un momento (se ve que ha ido) y luego se vacía despacio.
                                ValueAnimator out = ValueAnimator.ofInt(10000, 0);
                                out.setStartDelay(350);
                                out.setDuration(500);
                                out.setInterpolator(EASE);
                                out.addUpdateListener(level);
                                running[0] = out;
                                out.start();
                            }
                        });
                        running[0] = va;
                        va.start();
                        return true;
                    }
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL: {
                        v.animate().scaleX(1f).scaleY(1f).setStartDelay(0).setDuration(380)
                                .setInterpolator(new android.view.animation.OvershootInterpolator(2.2f)).start();
                        ValueAnimator cur = running[0];
                        if (cur == null || !cur.isRunning() || cur.getStartDelay() > 0) return true;
                        // Soltó antes de llenarse: se vacía con suavidad.
                        cur.cancel();
                        ValueAnimator back = ValueAnimator.ofInt(fill.getLevel(), 0);
                        back.setDuration(280);
                        back.setInterpolator(new android.view.animation.DecelerateInterpolator());
                        back.addUpdateListener(level);
                        running[0] = back;
                        back.start();
                        if (e.getActionMasked() == MotionEvent.ACTION_UP && SystemClock.uptimeMillis() - downAt[0] < 300) {
                            v.performHapticFeedback(HapticFeedbackConstants.REJECT);
                            subView.setText(I18n.t("Mantén pulsado para reiniciar", "Press and hold to restart"));
                            subView.setTextColor(accent);
                            subView.removeCallbacks(restoreSub);
                            subView.postDelayed(restoreSub, 2200);
                            float s = px(5);
                            android.animation.ObjectAnimator.ofFloat(v, View.TRANSLATION_X, 0, -s, s, -s / 2, s / 2, 0)
                                    .setDuration(320).start();
                        }
                        return true;
                    }
                    default:
                        return true;
                }
            }
        });
        // Con TalkBack no hay "mantener": el doble toque de accesibilidad actúa directamente.
        r.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                action.run();
            }
        });
        return r;
    }

    /** ¿Ese proceso ha cargado HyperSpring desde que se encendió el móvil? */
    private boolean seenSinceBoot(String key) {
        String st = state(key);
        if (st == null) return false;
        try {
            return Long.parseLong(st.split("\\|")[0]) >= bootTime();
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Píldora de cristal en Inicio: dice si HyperSpring está funcionando, con un punto que late.
     * Verde menta si está en el launcher y en la interfaz; ámbar si falta alguno.
     */
    private View statusPill() {
        boolean l = seenSinceBoot("launcher_seen"), s = seenSinceBoot("systemui_seen");
        int color = l && s ? 0xFF6FD99A : 0xFFFFB547;
        // Textos cortos: la píldora tiene que caber en una línea incluso con letra grande.
        String msg = l && s ? I18n.t("Activo · launcher e interfaz", "Active · launcher & System UI")
                : l ? I18n.t("Activo solo en el launcher", "Active in launcher only")
                : s ? I18n.t("Activo solo en la interfaz", "Active in System UI only")
                : I18n.t("Esperando a LSPosed", "Waiting for LSPosed");
        LinearLayout p = new LinearLayout(this);
        p.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable g = round(card, px(100));
        g.setStroke(Math.max(1, px(0.8f)), HAIRLINE);
        p.setBackground(g);
        p.setPadding(px(8), px(5), px(12), px(5));
        p.addView(new PulseDot(this, color, dp), new LinearLayout.LayoutParams(px(18), px(18)));
        TextView t = text(msg, 12, text2);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setLetterSpacing(0.02f);
        t.setMaxLines(1);
        t.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-2, -2);
        tlp.leftMargin = px(6);
        p.addView(t, tlp);

        LinearLayout wrap = new LinearLayout(this);            // para que la píldora no ocupe todo el ancho
        wrap.setPadding(px(8), px(14), px(8), px(2));
        wrap.addView(p, new LinearLayout.LayoutParams(-2, -2));
        return wrap;
    }

    /** Punto de estado con una onda que se expande y se apaga, sin parar, mientras se ve. */
    private static final class PulseDot extends View {
        private final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final int color;
        private final float dp;
        private final ValueAnimator loop = ValueAnimator.ofFloat(0f, 1f);
        private float t;

        PulseDot(android.content.Context c, int color, float dp) {
            super(c);
            this.color = color;
            this.dp = dp;
            loop.setDuration(1600);
            loop.setRepeatCount(ValueAnimator.INFINITE);
            loop.setInterpolator(new android.view.animation.DecelerateInterpolator());
            loop.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator a) {
                    t = (Float) a.getAnimatedValue();
                    invalidate();
                }
            });
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            loop.start();
        }

        @Override
        protected void onDetachedFromWindow() {
            loop.cancel();                                      // fuera de pantalla no gasta nada
            super.onDetachedFromWindow();
        }

        @Override
        protected void onDraw(android.graphics.Canvas c) {
            float cx = getWidth() / 2f, cy = getHeight() / 2f, r = 4f * dp;
            paint.setColor(color);
            paint.setAlpha(Math.round(130 * (1f - t)));
            c.drawCircle(cx, cy, r * (1f + 1.4f * t), paint);
            paint.setAlpha(255);
            c.drawCircle(cx, cy, r, paint);
        }
    }

    private LinearLayout titles(String title, String sub) {
        LinearLayout t = column();
        TextView a = text(title, 15.5f, text);
        a.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.addView(a);
        if (sub != null) {
            TextView s = text(sub, 12.5f, text2);
            s.setPadding(0, px(1), 0, 0);
            s.setLineSpacing(0, 1.1f);
            t.addView(s);
        }
        t.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        return t;
    }

    private View navRow(int iconKind, String title, String sub, boolean chevron, final Runnable action) {
        LinearLayout r = rowBase();
        r.addView(icon(iconKind, text));
        r.addView(titles(title, sub));
        if (chevron) {
            ImageView ch = new ImageView(this);
            ch.setImageDrawable(new Icons(Icons.CHEVRON, text3));
            r.addView(ch, new LinearLayout.LayoutParams(px(20), px(20)));
        }
        if (action != null) {
            r.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    action.run();
                }
            });
        }
        sink(r);
        return r;
    }

    /** Interruptor de una preferencia de la propia app (no de las animaciones). */
    private View prefToggleRow(int iconKind, String title, String sub, final String key, boolean def) {
        LinearLayout r = rowBase();
        r.addView(icon(iconKind, text));
        r.addView(titles(title, sub));
        final Controls.Toggle t = new Controls.Toggle(this, accentStrong, track);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.leftMargin = px(12);
        r.addView(t, lp);
        t.setChecked(getSharedPreferences("app", 0).getBoolean(key, def), false);
        t.setOnChange(new Controls.Toggle.OnChange() {
            @Override
            public void onChange(boolean on) {
                getSharedPreferences("app", 0).edit().putBoolean(key, on).apply();
            }
        });
        r.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                t.toggle(true);
            }
        });
        sink(r);
        return r;
    }

    private View infoRow(int iconKind, String title, String value) {
        LinearLayout r = rowBase();
        r.setBackground(null);
        r.addView(icon(iconKind, text));
        r.addView(titles(title, value));
        return r;
    }

    /** Guarda un interruptor de función y, si hace falta, avisa de que se aplica al reiniciar. */
    private void saveToggle(String key, boolean on) {
        write(key, on ? "1" : "0");
        // Las funciones se enganchan al arrancar el proceso: ofrecer el reinicio.
        if (key.startsWith("icehome_") && key.endsWith("_on") && !key.equals("icehome_press_on")) {
            pendingRestart(false);
        } else if (key.equals("telon_on")) {
            pendingRestart(true);
        }
    }

    private View toggleRow(int iconKind, final Object[] it, String title) {
        LinearLayout r = rowBase();
        final ImageView ic = iconKind >= 0 ? icon(iconKind, text) : null;
        if (ic != null) r.addView(ic);
        LinearLayout tc = titles(title, null);
        final TextView d = desc(it);
        if (d != null) tc.addView(d);
        r.addView(tc);
        final Controls.Toggle t = new Controls.Toggle(this, accentStrong, track);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.leftMargin = px(12);
        r.addView(t, lp);
        final String key = (String) it[0];
        final Runnable refresh = new Runnable() {
            @Override
            public void run() {
                boolean on = stored(it) >= 0.5f;
                t.setChecked(on, false);
                if (ic != null) lightIcon(ic, on, false);
            }
        };
        refresh.run();
        refreshers.add(refresh);
        t.setOnChange(new Controls.Toggle.OnChange() {
            @Override
            public void onChange(boolean on) {
                if (ic != null) lightIcon(ic, on, true);
                saveToggle(key, on);
            }
        });
        r.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (d != null) expand(d);                  // la fila explica; el interruptor cambia
                else t.toggle(true);
            }
        });
        sink(r);
        resetOnLongPress(r, it, refresh);
        return r;
    }

    // ---------------------------------------------------------------- apps sin genio

    private java.util.Set<String> excludedNow() {
        return Exclude.parse(getSharedPreferences(PrefsProvider.PREFS, 0).getString(Exclude.KEY, null));
    }

    private String excludeSummary() {
        int n = excludedNow().size();
        if (n == 0) return I18n.t("Todas las apps usan el genio", "Every app uses the genie");
        return n == 1 ? I18n.t("1 app con la animación de HyperOS", "1 app with the HyperOS animation")
                : I18n.t(n + " apps con la animación de HyperOS", n + " apps with the HyperOS animation");
    }

    /**
     * Lista de apps con un interruptor cada una: encendido = esa app se abre y se cierra con la
     * animación normal de HyperOS. Se aplica al momento, sin reiniciar el launcher (el ajuste le
     * llega como los deslizadores).
     */
    private ScrollView excludePage(final TextView summary) {
        final LinearLayout c = column();
        TextView intro = text(I18n.t("Útil para juegos, la cámara o apps de vídeo: se abren al instante, sin el efecto. Se aplica al momento.",
                "Handy for games, the camera or video apps: they open straight away, without the effect. Applies right away."), 14, text2);
        intro.setLineSpacing(0, 1.15f);
        intro.setPadding(px(8), px(4), px(8), px(14));          // alineado con el título
        c.addView(intro);

        // Buscador: filtra por nombre o por paquete mientras escribes.
        final android.widget.EditText search = new android.widget.EditText(this);
        search.setHint(I18n.t("Buscar app", "Search apps"));
        search.setHintTextColor(text3);
        search.setTextColor(text);
        search.setTextSize(15);
        search.setSingleLine(true);
        search.setBackground(round(card, px(18)));
        search.setPadding(px(18), px(12), px(18), px(12));
        c.addView(search, new LinearLayout.LayoutParams(-1, -2));
        c.addView(space(12));

        final LinearLayout list = card();
        final TextView loading = text(I18n.t("Cargando apps…", "Loading apps…"), 14, text3);
        loading.setPadding(px(18), px(18), px(18), px(18));
        list.addView(loading);
        c.addView(list);

        final java.util.Set<String> chosen = excludedNow();
        final List<View> rows = new ArrayList<>();
        final List<String> keys = new ArrayList<>();      // «nombre paquete» en minúsculas, para buscar
        search.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int n) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int n) {
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
                String q = s.toString().trim().toLowerCase(java.util.Locale.ROOT);
                for (int i = 0; i < rows.size(); i++) {
                    rows.get(i).setVisibility(q.isEmpty() || keys.get(i).contains(q) ? View.VISIBLE : View.GONE);
                }
            }
        });

        // Las apps y sus iconos se leen en otro hilo: con cien apps, en el principal se notaría.
        final android.os.Handler main = new android.os.Handler(getMainLooper());
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<Object[]> apps = new ArrayList<>();   // {paquete, nombre, ResolveInfo}
                final android.content.pm.PackageManager pm = getPackageManager();
                try {
                    android.content.Intent i = new android.content.Intent(android.content.Intent.ACTION_MAIN);
                    i.addCategory(android.content.Intent.CATEGORY_LAUNCHER);
                    java.util.Set<String> seen = new java.util.HashSet<>();
                    for (android.content.pm.ResolveInfo ri : pm.queryIntentActivities(i, 0)) {
                        String pkg = ri.activityInfo.packageName;
                        if (pkg.equals(getPackageName()) || !seen.add(pkg)) continue;
                        apps.add(new Object[]{pkg, String.valueOf(ri.loadLabel(pm)), ri});
                    }
                } catch (Throwable ignored) {
                }
                // Primero las excluidas; luego por orden alfabético.
                java.util.Collections.sort(apps, new java.util.Comparator<Object[]>() {
                    @Override
                    public int compare(Object[] a, Object[] b) {
                        boolean ea = chosen.contains((String) a[0]), eb = chosen.contains((String) b[0]);
                        if (ea != eb) return ea ? -1 : 1;
                        return ((String) a[1]).compareToIgnoreCase((String) b[1]);
                    }
                });
                // Primero los nombres (rápido) y la lista ya se puede usar; los iconos, que son lo
                // lento, van llegando después de arriba abajo.
                final List<ImageView> icons = new ArrayList<>();
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        list.removeView(loading);
                        for (Object[] app : apps) {
                            View r = appRow((String) app[0], (String) app[1], chosen, summary);
                            rows.add(r);
                            icons.add((ImageView) ((ViewGroup) r).getChildAt(0));
                            keys.add((app[1] + " " + app[0]).toLowerCase(java.util.Locale.ROOT));
                            list.addView(r);
                        }
                        Motion.cascade(list, 0, px(14));
                    }
                });
                for (int k = 0; k < apps.size(); k++) {
                    final int idx = k;
                    final Drawable d;
                    try {
                        d = ((android.content.pm.ResolveInfo) apps.get(k)[2]).loadIcon(pm);
                    } catch (Throwable t) {
                        continue;
                    }
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            if (idx >= icons.size()) return;
                            ImageView iv = icons.get(idx);
                            iv.setImageDrawable(d);
                            iv.setBackground(null);
                            iv.setAlpha(0f);
                            iv.animate().alpha(1f).setDuration(220).start();   // aparece suave, sin parpadeo
                        }
                    });
                }
            }
        }, "HyperSpring-apps").start();

        return page(I18n.t("Apps sin genio", "Apps without genie"), null, true, c);
    }

    private View appRow(final String pkg, String label, final java.util.Set<String> chosen,
                        final TextView summary) {
        LinearLayout r = rowBase();
        ImageView iv = new ImageView(this);                  // el icono llega después
        iv.setBackground(round(0x14FFFFFF, px(10)));         // hueco gris mientras tanto
        LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(px(36), px(36));
        il.rightMargin = px(14);
        r.addView(iv, il);
        LinearLayout tc = titles(label, pkg);
        // El paquete en una sola línea: los largos se cortan con «…».
        TextView p = (TextView) tc.getChildAt(1);
        p.setSingleLine(true);
        p.setEllipsize(TextUtils.TruncateAt.END);
        r.addView(tc);
        final Controls.Toggle t = new Controls.Toggle(this, accentStrong, track);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.leftMargin = px(12);
        r.addView(t, lp);
        t.setChecked(chosen.contains(pkg), false);
        t.setOnChange(new Controls.Toggle.OnChange() {
            @Override
            public void onChange(boolean on) {
                if (on) chosen.add(pkg);
                else chosen.remove(pkg);
                write(Exclude.KEY, chosen.isEmpty() ? null : Exclude.join(chosen));
                if (summary != null) summary.setText(excludeSummary());
            }
        });
        r.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                t.toggle(true);
            }
        });
        sink(r);
        return r;
    }

    /** La gráfica del muelle de la página abierta (null si la página no la tiene). */
    private SpringGraph springGraph;
    private float graphSpeed = 1f, graphBounce = 0f;

    /** Pasa a la gráfica el valor de Velocidad o Rebote mientras se mueve su deslizador. */
    private void graph(String key, float v) {
        if (springGraph == null) return;
        if ("icehome_anim_speed".equals(key)) graphSpeed = v;
        else if ("icehome_anim_bounce".equals(key)) graphBounce = v;
        else return;
        springGraph.set(graphSpeed, graphBounce, false);
    }

    private View settingRow(final Object[] it) {
        final int decimals = (Integer) it[5];
        if (decimals < 0) return toggleRow(-1, it, I18n.title(it));
        final String key = (String) it[0];
        if ("icehome_genie_style".equals(key)) return styleRow(it);
        if ("icehome_genie_fps".equals(key)) return fpsRow(it);
        final float min = (Float) it[2], max = (Float) it[3];
        LinearLayout r = rowBase();
        r.setOrientation(LinearLayout.VERTICAL);
        r.setGravity(Gravity.NO_GRAVITY);
        r.setPadding(px(14), px(11), px(14), px(4));
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout tc = titles(I18n.title(it), null);
        final TextView d = desc(it);
        if (d != null) tc.addView(d);
        head.addView(tc);
        // El número, del color de acento y en cifras que ruedan como un cuentakilómetros (Odometer).
        final Odometer value = new Odometer(this, 14, accent);
        value.setPadding(px(9), px(3), px(9), px(3));
        value.setBackground(round(accentSoft, px(8)));
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(-2, -2);
        vlp.leftMargin = px(12);
        head.addView(value, vlp);
        r.addView(head);
        final Controls.Slider sl = new Controls.Slider(this, track, accentStrong);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, -2);
        slp.topMargin = px(2);
        slp.leftMargin = -px(18);
        slp.rightMargin = -px(18);
        r.addView(sl, slp);
        final Runnable refresh = new Runnable() {
            @Override
            public void run() {
                float v = stored(it);
                sl.setFraction((v - min) / (max - min));
                value.setText(format(v, decimals));
                graph(key, v);
            }
        };
        refresh.run();
        refreshers.add(refresh);
        // Al abrir la página, el número cuenta desde el mínimo y la barra se llena con él, como los
        // contadores de las webs. Solo la primera vez que la fila sale en pantalla.
        r.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                v.removeOnAttachStateChangeListener(this);
                fillUp(sl, value, min, max, stored(it), decimals);
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
            }
        });
        sl.setOnChange(new Controls.Slider.OnChange() {
            @Override
            public void onChange(float f, boolean done) {
                float v = min + (max - min) * f;
                value.setText(format(v, decimals));
                graph(key, v);                              // la curva sigue al dedo, sin esperar a soltar
                if (done) write(key, format(v, Math.max(decimals, 0)).replace(',', '.'));
            }
        });
        if (d != null) {
            r.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    expand(d);
                }
            });
        }
        resetOnLongPress(r, it, refresh);
        return r;
    }

    /**
     * Llena un deslizador y cuenta su número desde el mínimo hasta el valor guardado. Si el dedo
     * toca el deslizador a medias, se para (manda lo que hagas tú).
     */
    private void fillUp(final Controls.Slider sl, final Odometer value, final float min, final float max,
                        final float to, final int decimals) {
        if (!ValueAnimator.areAnimatorsEnabled() || max <= min) return;
        final ValueAnimator a = ValueAnimator.ofFloat(min, to);
        a.setStartDelay(260);
        a.setDuration(1100);
        a.setInterpolator(Motion.POWER4_OUT);
        a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator va) {
                float v = (Float) va.getAnimatedValue();
                sl.setFraction((v - min) / (max - min));
                value.setText(format(v, decimals));
            }
        });
        sl.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                if (a.isStarted()) a.end();                     // salta al valor real y te deja moverlo
                v.setOnTouchListener(null);
                return false;
            }
        });
        a.start();
    }

    // ---------------------------------------------------------------- menú desplegable

    private String[] styleNames() {
        return new String[]{I18n.t("Genio clásico", "Classic genie"), I18n.t("Copa", "Cup"),
                I18n.t("Profundidad", "Depth")};
    }

    private String[] styleHints() {
        return new String[]{
                I18n.t("Sale del icono con cola, como en un Mac", "Comes out of the icon with a tail, like on a Mac"),
                I18n.t("Arriba sube primero, como Samsung", "The top rises first, like Samsung"),
                I18n.t("Sale del icono inclinada y se endereza, con estela", "Leaves the icon tilted and straightens, with a trail")};
    }

    /** Color {@code top} (con su transparencia) pintado encima de {@code under} (opaco). */
    private static int over(int top, int under) {
        float a = (top >>> 24) / 255f;
        int r = Math.round(((top >> 16) & 255) * a + ((under >> 16) & 255) * (1f - a));
        int g = Math.round(((top >> 8) & 255) * a + ((under >> 8) & 255) * (1f - a));
        int b = Math.round((top & 255) * a + (under & 255) * (1f - a));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /** Lo que hace el menú al elegir una opción. */
    private interface MenuPick {
        void pick(int index);
    }

    /** Cierra el menú desplegable abierto (null si no hay ninguno). */
    private Runnable menuClose;

    /**
     * Fila de un ajuste con pocas opciones (el estilo del genio): en vez de un deslizador de
     * números, el nombre de la opción en una pastilla. Al tocarla se despliega un menú encima,
     * como en HyperOS 4 o iOS.
     */
    /** Valor guardado de cada estilo del selector (el 2, la copa «Ice», ya no existe). */
    private static final int[] STYLE_VALUES = {0, 1, 3};

    /** Selector de estilo del genio: cada opción guarda su número de siempre, no su posición. */
    private View styleRow(final Object[] it) {
        final String key = (String) it[0];
        final Runnable[] refresh = new Runnable[1];
        LinearLayout r = choiceRow(I18n.title(it), styleNames(), styleHints(), new Chosen() {
            @Override
            public int now() {
                int v = Math.round(stored(it));
                if (v == 2) v = 1;                       // Ice guardado: se muestra (y funciona) como Copa
                for (int i = 0; i < STYLE_VALUES.length; i++) if (STYLE_VALUES[i] == v) return i;
                return 0;
            }
        }, new MenuPick() {
            @Override
            public void pick(int index) {
                write(key, String.valueOf(STYLE_VALUES[Math.max(0, Math.min(STYLE_VALUES.length - 1, index))]));
                for (Runnable rf : refreshers) rf.run();       // enseña u oculta los ajustes de cada estilo
            }
        }, refresh);
        resetOnLongPress(r, it, refresh[0]);
        return r;
    }

    /** «Fotogramas por segundo»: Auto y los topes de Tune.FPS_CHOICES. */
    private View fpsRow(Object[] it) {
        int[] v = Tune.FPS_CHOICES;
        String[] names = new String[v.length], hints = new String[v.length];
        names[0] = "Auto";
        hints[0] = I18n.t("Lo que da la pantalla, hasta 90", "What the screen does, up to 90");
        for (int i = 1; i < v.length; i++) {
            names[i] = String.valueOf(v[i]);
            hints[i] = v[i] <= 60 ? I18n.t("Gasta menos GPU", "Uses less GPU") : I18n.t("Más fluido si la pantalla llega", "Smoother if the screen goes that high");
        }
        return pickerRow(it, names, hints);
    }

    private View pickerRow(final Object[] it, final String[] names, final String[] hints) {
        final String key = (String) it[0];
        final int max = Math.round((Float) it[3]);
        final Runnable[] refresh = new Runnable[1];
        LinearLayout r = choiceRow(I18n.title(it), names, hints, new Chosen() {
            @Override
            public int now() {
                return Math.max(0, Math.min(max, Math.round(stored(it))));
            }
        }, new MenuPick() {
            @Override
            public void pick(int index) {
                write(key, String.valueOf(index));
            }
        }, refresh);
        resetOnLongPress(r, it, refresh[0]);
        return r;
    }

    /** Escalas de duración de las animaciones de Android que se ofrecen (1 = normal). */
    private static final float[] ANIM_SCALES = {0.5f, 0.75f, 1f, 1.25f, 1.5f};
    /** La escala recién elegida, mientras se guarda con root (NaN = leer la del sistema). */
    private float sysScale = Float.NaN;

    /**
     * Velocidad de todas las animaciones de Android (lo mismo que las tres «escalas de animación»
     * de las opciones de desarrollador), sin tener que activarlas. Mucha gente las pone en 0,5×
     * para que el móvil parezca más rápido. Afecta también al telón de HyperSpring, que sigue la
     * animación del sistema.
     */
    private View systemSpeedRow() {
        String[] names = {I18n.t("0,5×", "0.5×"), I18n.t("0,75×", "0.75×"), "1×", I18n.t("1,25×", "1.25×"), I18n.t("1,5×", "1.5×")};
        String[] hints = {
                I18n.t("Animaciones al doble de rápido", "Animations twice as fast"),
                I18n.t("Animaciones algo más rápidas", "Slightly faster animations"),
                I18n.t("Como viene el móvil", "As the phone comes"),
                I18n.t("Animaciones algo más lentas", "Slightly slower animations"),
                I18n.t("Animaciones más lentas", "Slower animations")};
        final Runnable[] refresh = new Runnable[1];
        LinearLayout r = choiceRow(I18n.t("Velocidad de las animaciones", "Animation speed"), names, hints, new Chosen() {
            @Override
            public int now() {
                float v = sysScale;
                if (Float.isNaN(v)) v = android.provider.Settings.Global.getFloat(getContentResolver(),
                        android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
                int best = 2;
                for (int i = 0; i < ANIM_SCALES.length; i++) {
                    if (Math.abs(ANIM_SCALES[i] - v) < Math.abs(ANIM_SCALES[best] - v)) best = i;
                }
                return best;
            }
        }, new MenuPick() {
            @Override
            public void pick(int index) {
                setSystemScale(ANIM_SCALES[index]);
            }
        }, refresh);
        r.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                setSystemScale(1f);
                refresh[0].run();
                toast(I18n.t("Por defecto: 1×", "Default: 1×"));
                return true;
            }
        });
        return r;
    }

    /**
     * Cambia las tres escalas de animación de Android. Escribir en Settings.Global necesita un
     * permiso que solo tiene el sistema (WRITE_SECURE_SETTINGS), así que se hace con root, en un
     * hilo aparte para no congelar la app mientras Magisk contesta.
     */
    private void setSystemScale(final float scale) {
        sysScale = scale;
        final String v = String.format(java.util.Locale.ROOT, "%.2f", scale);
        new Thread(new Runnable() {
            @Override
            public void run() {
                final boolean ok = su("settings put global window_animation_scale " + v
                        + "; settings put global transition_animation_scale " + v
                        + "; settings put global animator_duration_scale " + v + "; echo hecho");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        sysScale = Float.NaN;           // a partir de ahora, lo que diga el sistema
                        for (Runnable rf : refreshers) rf.run();
                        if (!ok) toast(I18n.t("Hace falta root para cambiar la velocidad de Android",
                                "Root is needed to change the Android animation speed"));
                    }
                });
            }
        }, "escala").start();
    }

    /**
     * Ejecuta una orden con root y dice si ha ido bien (la orden tiene que terminar con
     * «echo hecho»). Bloquea: llámalo siempre desde un hilo aparte.
     */
    private static boolean su(String cmd) {
        try {
            Process p = new ProcessBuilder("su", "-c", cmd).redirectErrorStream(true).start();
            java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()));
            StringBuilder sb = new StringBuilder();
            for (String l; (l = r.readLine()) != null; ) sb.append(l).append(' ');
            return p.waitFor() == 0 && sb.toString().contains("hecho");
        } catch (Throwable t) {
            return false;
        }
    }

    /** La opción elegida ahora en una fila de opciones. */
    private interface Chosen {
        int now();
    }

    /**
     * Fila con una pastilla que abre un menú de opciones: el título a la izquierda, la opción
     * elegida en la pastilla y su explicación debajo del título. {@code refreshOut[0]} recibe
     * la función que la repinta (para «mantener pulsado = valor por defecto»).
     */
    private LinearLayout choiceRow(String title, final String[] names, final String[] hints, final Chosen chosen,
                                   final MenuPick pick, Runnable[] refreshOut) {
        LinearLayout r = rowBase();
        final LinearLayout tc = titles(title, "");
        final TextView sub = (TextView) tc.getChildAt(1);
        r.addView(tc);
        // Pastilla: nombre de la opción y una flecha hacia abajo (el chevron girado).
        final LinearLayout pill = new LinearLayout(this);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setPadding(px(12), px(6), px(8), px(6));
        pill.setBackground(round(accentSoft, px(14)));
        final TextView value = text("", 15, accent);
        value.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        pill.addView(value);
        ImageView arrow = new ImageView(this);
        arrow.setImageDrawable(new Icons(Icons.CHEVRON, accent));
        arrow.setRotation(90f);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(px(16), px(16));
        alp.leftMargin = px(4);
        pill.addView(arrow, alp);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(-2, -2);
        plp.leftMargin = px(12);
        r.addView(pill, plp);
        final Runnable refresh = new Runnable() {
            @Override
            public void run() {
                int v = Math.max(0, Math.min(names.length - 1, chosen.now()));
                value.setText(names[v]);
                sub.setText(hints[v]);
            }
        };
        refresh.run();
        refreshers.add(refresh);
        if (refreshOut != null) refreshOut[0] = refresh;
        r.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                showMenu(pill, names, hints, chosen.now(), new MenuPick() {
                    @Override
                    public void pick(int index) {
                        pick.pick(index);
                        refresh.run();
                    }
                });
            }
        });
        sink(r);
        return r;
    }

    /**
     * Menú desplegable: una tarjeta flotante que nace de {@code anchor} con un muelle (crece
     * desde su esquina, como en iOS), con las opciones apareciendo una tras otra y una marca en la
     * elegida. Se cierra al elegir, al tocar fuera o con «atrás».
     */
    private void showMenu(final View anchor, String[] names, String[] hints, int chosen, final MenuPick pick) {
        if (menuClose != null || root == null) return;
        final FrameLayout scrim = new FrameLayout(this);
        scrim.setBackgroundColor(0x24000000);
        scrim.setAlpha(0f);
        final LinearLayout menu = column();
        // Opaco: las tarjetas de la app son translúcidas (dejan ver el fondo) y aquí se leería el
        // texto de debajo. Se mezcla a mano el color de tarjeta sobre el de fondo, un poco más claro.
        menu.setBackground(round(over(card, over(0x14FFFFFF, bg | 0xFF000000)), px(22)));
        menu.setElevation(px(18));
        menu.setClipToOutline(true);
        menu.setPadding(0, px(6), 0, px(6));
        menu.setClickable(true);                     // tocar su borde no lo cierra (solo fuera)
        final int menuW = Math.min(px(300), root.getWidth() - px(32));
        for (int i = 0; i < names.length; i++) {
            final int index = i;
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(px(18), px(11), px(16), px(11));
            row.setBackground(new RippleDrawable(ColorStateList.valueOf(pressed), null, new ColorDrawable(Color.WHITE)));
            LinearLayout col = column();
            TextView n = text(names[i], 16, i == chosen ? accent : text);
            n.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            col.addView(n);
            TextView h = text(hints[i], 12.5f, text2);
            h.setPadding(0, px(2), 0, 0);
            col.addView(h);
            row.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
            TextView check = text("✓", 17, accent);
            check.setVisibility(i == chosen ? View.VISIBLE : View.INVISIBLE);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-2, -2);
            clp.leftMargin = px(12);
            row.addView(check, clp);
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                    pick.pick(index);
                    if (menuClose != null) menuClose.run();
                }
            });
            menu.addView(row, new LinearLayout.LayoutParams(-1, -2));
        }
        menu.measure(View.MeasureSpec.makeMeasureSpec(menuW, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        final int menuH = menu.getMeasuredHeight();
        // Debajo de la pastilla, alineado a su derecha; si no cabe, encima.
        int[] a = new int[2], o = new int[2];
        anchor.getLocationInWindow(a);
        root.getLocationInWindow(o);
        int ax = a[0] - o[0], ay = a[1] - o[1];
        int x = Math.max(px(16), Math.min(root.getWidth() - menuW - px(16), ax + anchor.getWidth() - menuW));
        int y = ay + anchor.getHeight() + px(8);
        boolean above = y + menuH > root.getHeight() - insetBottom - px(16);
        if (above) y = Math.max(insetTop + px(8), ay - menuH - px(8));
        FrameLayout.LayoutParams mlp = new FrameLayout.LayoutParams(menuW, menuH);
        mlp.leftMargin = x;
        mlp.topMargin = y;
        scrim.addView(menu, mlp);
        root.addView(scrim, new FrameLayout.LayoutParams(-1, -1));
        // Crece desde el punto de la pastilla: su esquina derecha, arriba (o abajo si va encima).
        menu.setPivotX(Math.max(0, Math.min(menuW, ax + anchor.getWidth() / 2 - x)));
        menu.setPivotY(above ? menuH : 0);
        menu.setScaleX(0.4f);
        menu.setScaleY(0.25f);
        menu.setAlpha(0f);
        scrim.animate().alpha(1f).setDuration(200).start();
        menu.animate().alpha(1f).setDuration(140).start();
        final ValueAnimator grow = ValueAnimator.ofFloat(0f, 1f);
        Spring.apply(grow, 0f, 1f, 0f, 0.42f, 0.74f);
        grow.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator va) {
                float f = (Float) va.getAnimatedValue();
                menu.setScaleX(0.4f + 0.6f * f);
                menu.setScaleY(0.25f + 0.75f * f);
            }
        });
        grow.start();
        for (int i = 0; i < menu.getChildCount(); i++) {
            View c = menu.getChildAt(i);
            c.setAlpha(0f);
            c.setTranslationY(above ? px(8) : -px(8));
            c.animate().alpha(1f).translationY(0f).setStartDelay(40 + 35L * (above ? menu.getChildCount() - 1 - i : i))
                    .setDuration(320).setInterpolator(Motion.EXPO_OUT).start();
        }
        menuClose = new Runnable() {
            @Override
            public void run() {
                if (menuClose != this) return;
                menuClose = null;
                syncBack();
                grow.cancel();
                scrim.setEnabled(false);
                scrim.animate().alpha(0f).setDuration(200).start();
                menu.animate().scaleX(0.7f).scaleY(0.6f).alpha(0f).setDuration(180).setInterpolator(Motion.EASE)
                        .withEndAction(new Runnable() {
                            @Override
                            public void run() {
                                root.removeView(scrim);
                            }
                        }).start();
            }
        };
        scrim.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (menuClose != null) menuClose.run();
            }
        });
        syncBack();
    }

    /** Explicación de un ajuste: una línea; al tocar la fila se despliega entera (y se recoge). */
    private TextView desc(Object[] it) {
        String ds = I18n.desc(it);
        if (ds == null || ds.isEmpty()) return null;
        TextView d = text(ds, 12.5f, text2);
        d.setLineSpacing(0, 1.18f);
        d.setPadding(0, px(2), 0, 0);
        // Dos líneas cerradas (con una se cortaba casi todo, sobre todo con la letra grande); al
        // tocar la fila se abre entera.
        d.setMaxLines(DESC_LINES);
        d.setEllipsize(TextUtils.TruncateAt.END);
        return d;
    }

    private static final int DESC_LINES = 2;

    private void expand(final TextView d) {
        final boolean open = d.getMaxLines() == DESC_LINES;
        int w = d.getWidth();
        if (w <= 0) return;
        final int from = d.getHeight();
        d.setMaxLines(open ? Integer.MAX_VALUE : DESC_LINES);
        d.setEllipsize(open ? null : TextUtils.TruncateAt.END);
        d.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int to = d.getMeasuredHeight();
        d.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        if (from == to) return;
        final ViewGroup.LayoutParams lp = d.getLayoutParams();
        ValueAnimator a = ValueAnimator.ofInt(from, to);
        a.setDuration(open ? 300 : 240);
        a.setInterpolator(EASE);
        a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator va) {
                lp.height = (Integer) va.getAnimatedValue();
                d.setLayoutParams(lp);
                bar.invalidate();
            }
        });
        a.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator an) {
                lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                d.setLayoutParams(lp);
            }
        });
        if (open) {
            d.setAlpha(0.6f);
            d.animate().alpha(1f).setDuration(300).start();
        }
        a.start();
    }

    private void resetOnLongPress(View r, final Object[] it, final Runnable refresh) {
        final float def = (Float) it[4];
        final int decimals = (Integer) it[5];
        r.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                write((String) it[0], null);
                refresh.run();
                toast(I18n.t("Por defecto: ", "Default: ") + (decimals < 0
                        ? (def >= 0.5f ? I18n.t("activado", "on") : I18n.t("desactivado", "off")) : format(def, decimals)));
                return true;
            }
        });
    }

    // ---------------------------------------------------------------- piezas
    /**
     * Encabezado de grupo al estilo editorial de las webs: en mayúsculas pequeñas y espaciadas,
     * con una rayita del color de acento delante que se dibuja de izquierda a derecha al aparecer.
     */
    private View label(String s) {
        LinearLayout h = new LinearLayout(this);
        h.setGravity(Gravity.CENTER_VERTICAL);
        h.setPadding(px(8), px(22), px(8), px(8));
        final View line = new View(this);
        line.setBackgroundColor(accent);
        line.setPivotX(0f);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(px(14), Math.max(2, px(2)));
        llp.rightMargin = px(8);
        h.addView(line, llp);
        TextView t = text(s, 11.5f, text2);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setAllCaps(true);
        t.setLetterSpacing(0.14f);
        h.addView(t);
        // La raya se dibuja cuando la cabecera sale en pantalla (con un poco de retraso, detrás
        // de la entrada de la propia cabecera).
        if (ValueAnimator.areAnimatorsEnabled()) {
            line.setScaleX(0f);
            h.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(View v) {
                    v.removeOnAttachStateChangeListener(this);
                    line.animate().scaleX(1f).setStartDelay(320).setDuration(900).setInterpolator(Motion.EXPO_OUT).start();
                }

                @Override
                public void onViewDetachedFromWindow(View v) {
                }
            });
        }
        return h;
    }

    private View space(int dpH) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, px(dpH)));
        return v;
    }

    /** Tarjeta: negra, con un filo de luz finísimo y esquinas más cerradas (más compacta). */
    private LinearLayout card() {
        LinearLayout c = column();
        GradientDrawable g = round(card, px(18));
        g.setStroke(Math.max(1, px(0.8f)), HAIRLINE);
        c.setBackground(g);
        c.setClipToOutline(true);
        c.setPadding(0, px(2), 0, px(2));
        c.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        c.setTag(Motion.CASCADE);                    // al entrar la página, sus filas aparecen una a una
        return c;
    }

    private View notice(String msg, String action, View.OnClickListener l) {
        LinearLayout n = column();
        // Aviso: tarjeta negra con el filo del color de acento (llama la atención sin rellenarse).
        GradientDrawable g = round(warn, px(18));
        g.setStroke(Math.max(1, px(1)), (accent & 0x00FFFFFF) | 0x66000000);
        n.setBackground(g);
        n.setPadding(px(16), px(14), px(16), px(14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = px(14);
        n.setLayoutParams(lp);
        TextView w = text(msg, 13.5f, text);
        w.setLineSpacing(0, 1.2f);
        n.addView(w);
        if (action != null) {
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-2, -2);
            blp.topMargin = px(14);
            n.addView(button(action, true, l), blp);
        }
        return n;
    }

    /** Advertencia en rojo suave (root, dispositivos probados). */
    private View warning(String msg) {
        LinearLayout n = new LinearLayout(this);
        n.setGravity(Gravity.TOP);
        // Sin rojo aparte: la misma tarjeta negra con el escudo y el filo en el color de acento.
        GradientDrawable g = round(card, px(18));
        g.setStroke(Math.max(1, px(0.8f)), (accent & 0x00FFFFFF) | 0x4D000000);
        n.setBackground(g);
        n.setPadding(px(14), px(12), px(16), px(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = px(14);
        n.setLayoutParams(lp);
        ImageView ic = new ImageView(this);
        ic.setImageDrawable(new Icons(Icons.SHIELD, accent));
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(px(20), px(20));
        ilp.rightMargin = px(12);
        ilp.topMargin = px(1);
        n.addView(ic, ilp);
        TextView w = text(msg, 13, text2);
        w.setLineSpacing(0, 1.2f);
        n.addView(w, new LinearLayout.LayoutParams(0, -2, 1f));
        return n;
    }

    private TextView button(String s, boolean primary, View.OnClickListener l) {
        TextView b = text(s, 15, primary ? onAccent : text);
        b.setTypeface(Typeface.create(Typeface.DEFAULT, 600, false));
        b.setGravity(Gravity.CENTER);
        b.setPadding(px(20), px(13), px(20), px(13));
        // Botón en cápsula: el principal, lleno del acento; el otro, negro con filo.
        GradientDrawable shape = round(primary ? accent : card, px(100));
        if (!primary) shape.setStroke(Math.max(1, px(0.8f)), 0x2EFFFFFF);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(primary ? 0x33000000 : pressed), shape, null));
        b.setClickable(true);
        b.setOnClickListener(l);
        sink(b);
        return b;
    }

    // ---------------------------------------------------------------- datos
    /** Pregunta antes de aplicar un perfil: pisa los ajustes de animación que hubiera. */
    /** Una tarjeta con una fila por perfil (perfiles, estilos de cristal o modos) y la marca «En uso». */
    private LinearLayout presetCard(Object[][] presets, int iconKind) {
        LinearLayout pr = card();
        pr.setTag(Motion.CASCADE);
        for (final Object[] preset : presets) {
            LinearLayout row = (LinearLayout) navRow(iconKind, Presets.name(preset),
                    Presets.about(preset), false, new Runnable() {
                @Override
                public void run() {
                    confirmPreset(preset);
                }
            });
            final TextView inUse = text(I18n.t("En uso", "In use"), 12, accent);
            inUse.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            inUse.setPadding(px(9), px(3), px(9), px(3));
            inUse.setBackground(round(accentSoft, px(100)));
            LinearLayout.LayoutParams ul = new LinearLayout.LayoutParams(-2, -2);
            ul.leftMargin = px(10);
            row.addView(inUse, ul);
            // Se vuelve a mirar al aplicar un perfil o importar ajustes (settingsReplaced).
            Runnable mark = new Runnable() {
                @Override
                public void run() {
                    inUse.setVisibility(Presets.active(SettingsActivity.this, preset) ? View.VISIBLE : View.GONE);
                }
            };
            mark.run();
            refreshers.add(mark);
            pr.addView(row);
        }
        return pr;
    }

    /** «¿Cómo va mi móvil?»: mira en el registro cómo han ido las últimas animaciones del genio. */
    private void showHealth() {
        List<String> lines = new ArrayList<>();
        try (android.database.Cursor cu = getContentResolver().query(PrefsProvider.LOG, null, null, null, null)) {
            while (cu != null && cu.moveToNext()) lines.add(cu.getString(0));
        } catch (Throwable ignored) {
        }
        final Health h = Health.analyze(lines);
        String msg;
        if (h.verdict == Health.UNKNOWN) {
            msg = I18n.t("Todavía no hay datos suficientes. Abre y cierra unas cuantas apps desde el escritorio (al menos "
                            + Health.MIN_RUNS + ") con el genio encendido y vuelve a mirar.",
                    "Not enough data yet. Open and close a few apps from the home screen (at least "
                            + Health.MIN_RUNS + ") with the genie on and check again.");
        } else {
            String stats = String.format(java.util.Locale.ROOT, I18n.t(
                            "En las últimas %d animaciones: %.0f fotogramas por segundo de media y un %.0f %% de fotogramas con tirón.",
                            "Over the last %d animations: %.0f frames per second on average and %.0f %% of frames with a hitch."),
                    h.runs, h.fps(), h.jankRatio() * 100f);
            String verdict = h.verdict == Health.SMOOTH
                    ? I18n.t("Va fluido. No hace falta tocar nada.", "It runs smoothly. Nothing to change.")
                    : h.verdict == Health.TIGHT
                    ? I18n.t("Va justo. El perfil Rendimiento (menos resolución, sin contenido en vivo) debería notarse.",
                    "It is a tight fit. The Performance preset (lower resolution, no live content) should help.")
                    : I18n.t("Le cuesta. Te recomiendo el perfil Rendimiento.", "It struggles. I recommend the Performance preset.");
            msg = stats + "\n\n" + verdict;
        }
        android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(this)
                .setTitle(I18n.t("¿Cómo va mi móvil?", "How is my phone doing?"))
                .setMessage(msg)
                .setNegativeButton(I18n.t("Cerrar", "Close"), null);
        if (h.verdict == Health.TIGHT || h.verdict == Health.STRUGGLING) {
            b.setPositiveButton(I18n.t("Aplicar Rendimiento", "Apply Performance"), new android.content.DialogInterface.OnClickListener() {
                @Override
                public void onClick(android.content.DialogInterface d, int which) {
                    Presets.apply(SettingsActivity.this, Presets.PERFORMANCE);
                    settingsReplaced();
                    toast(I18n.t("Perfil aplicado: ", "Preset applied: ") + Presets.name(Presets.PERFORMANCE));
                }
            });
        }
        b.show();
    }

    private void confirmPreset(final Object[] preset) {
        new android.app.AlertDialog.Builder(this)
                .setTitle(I18n.t("Aplicar el perfil «", "Apply the “") + Presets.name(preset) + I18n.t("»", "” preset"))
                .setMessage(Presets.about(preset) + "\n\n" + (Presets.keys(preset) == Presets.GLASS_KEYS
                        ? I18n.t("Cambia el aspecto del cristal (centro de control, carpetas y menú de "
                                + "atajos). No toca las animaciones ni enciende o apaga funciones. ",
                        "It changes the look of the glass (control center, folders and shortcut "
                                + "menu). It leaves the animations alone and doesn't turn features on or off. ")
                        : Presets.keys(preset) == Presets.MODE_KEYS
                        ? I18n.t("Cambia a la vez las animaciones y el aspecto del cristal. No enciende ni apaga funciones. ",
                        "It changes the animations and the look of the glass together. It doesn't turn features on or off. ")
                        : I18n.t("Cambia los ajustes de forma, velocidad y calidad de las animaciones. No enciende ni apaga "
                                + "funciones y no toca el cristal. ",
                        "It changes the shape, speed and quality settings of the animations. It doesn't turn features "
                                + "on or off, and it leaves the glass alone. "))
                        + I18n.t("Podrás deshacerlo justo después.", "You can undo it right after."))
                .setNegativeButton(I18n.t("Cancelar", "Cancel"), null)
                .setPositiveButton(I18n.t("Aplicar", "Apply"), new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int which) {
                        // Foto de lo que había en las claves que toca el perfil, para «Deshacer».
                        final android.content.SharedPreferences prefs = getSharedPreferences(PrefsProvider.PREFS, 0);
                        final String[] keys = Presets.keys(preset);
                        final String[] before = new String[keys.length];
                        for (int i = 0; i < keys.length; i++) {
                            Object v = prefs.getAll().get(keys[i]);
                            before[i] = v == null ? null : String.valueOf(v);
                        }
                        Presets.apply(SettingsActivity.this, preset);
                        settingsReplaced();
                        showPill(I18n.t("«" + Presets.name(preset) + "» aplicado · Deshacer",
                                "“" + Presets.name(preset) + "” applied · Undo"), false, new Runnable() {
                            @Override
                            public void run() {
                                android.content.SharedPreferences.Editor e = prefs.edit();
                                for (int i = 0; i < keys.length; i++) {
                                    if (before[i] == null) e.remove(keys[i]);
                                    else e.putString(keys[i], before[i]);
                                }
                                e.commit();
                                settingsReplaced();
                                toast(I18n.t("Deshecho", "Undone"));
                            }
                        }, 6000);
                    }
                })
                .show();
    }

    /**
     * Pide al sistema que añada el tile al panel (Android 13+ enseña un diálogo con «Añadir»).
     * Si el sistema no lo permite (algunas capas lo bloquean), se explica cómo hacerlo a mano.
     */
    private void addTile() {
        final String manual = I18n.t("Abre el panel de ajustes rápidos, toca editar (el lápiz) y arrastra «HyperSpring» a tus tiles.",
                "Open the quick settings panel, tap edit (the pencil) and drag “HyperSpring” into your tiles.");
        if (Build.VERSION.SDK_INT < 33) {
            toast(manual);
            return;
        }
        try {
            android.app.StatusBarManager sb = getSystemService(android.app.StatusBarManager.class);
            sb.requestAddTileService(new android.content.ComponentName(this, PauseTile.class),
                    "HyperSpring", android.graphics.drawable.Icon.createWithResource(this,
                            // Sin clase R (el build local no la genera): el icono se busca por su nombre.
                            getResources().getIdentifier("ic_tile", "drawable", getPackageName())),
                    getMainExecutor(), new java.util.function.Consumer<Integer>() {
                        @Override
                        public void accept(Integer result) {
                            // 2 = ya estaba; 0 o 1 = el usuario ha respondido; el resto, error.
                            if (result == android.app.StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED
                                    || result == android.app.StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED) {
                                PauseTile.added(SettingsActivity.this, true);
                            }
                            if (result == android.app.StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED) {
                                toast(I18n.t("El tile ya está en tu panel", "The tile is already in your panel"));
                            } else if (result != android.app.StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED
                                    && result != android.app.StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED) {
                                toast(manual);
                            }
                        }
                    });
        } catch (Throwable t) {
            toast(manual);
        }
    }

    /** Tras cambiar muchos ajustes de golpe: avisa a los procesos enganchados y repinta la app. */
    void settingsReplaced() {
        send(Remote.TUNE);
        for (Runnable rf : refreshers) rf.run();
    }

    /** Comparte el código de los ajustes con el menú de compartir del sistema. */
    private void shareSettings() {
        try {
            String msg = I18n.t("Mis ajustes de HyperSpring " + Version.NAME + ". Para usarlos, copia este mensaje "
                            + "y en HyperSpring ve a Acerca de → Cargar ajustes.",
                    "My HyperSpring " + Version.NAME + " settings. To use them, copy this message and in "
                            + "HyperSpring go to About → Load settings.")
                    + "\n\n" + Backup.shareCode(this);
            android.content.Intent i = new android.content.Intent(android.content.Intent.ACTION_SEND);
            i.setType("text/plain");
            i.putExtra(android.content.Intent.EXTRA_TEXT, msg);
            startActivity(android.content.Intent.createChooser(i, I18n.t("Compartir ajustes", "Share settings")));
        } catch (Throwable t) {
            toast(I18n.t("No se pudo: ", "Failed: ") + t.getMessage());
        }
    }

    /** El texto del portapapeles, o null. */
    private String clipText() {
        try {
            android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            android.content.ClipData clip = cm.getPrimaryClip();
            if (clip != null && clip.getItemCount() > 0) {
                CharSequence cs = clip.getItemAt(0).coerceToText(this);
                if (cs != null) return cs.toString();
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /** Lee el portapapeles, busca un código HS1: y, si el usuario confirma, lo aplica. */
    private void pasteSettings() {
        final String json = Backup.fromCode(clipText());
        if (json == null) {
            toast(I18n.t("No hay ningún código de HyperSpring copiado (empieza por HS1:)",
                    "No HyperSpring code copied (it starts with HS1:)"));
            return;
        }
        String from = "";
        try {
            from = new org.json.JSONObject(json).optString("version");
        } catch (Throwable ignored) {
        }
        new android.app.AlertDialog.Builder(this)
                .setTitle(I18n.t("¿Usar estos ajustes?", "Use these settings?"))
                .setMessage(I18n.t("Sustituyen todos tus ajustes" + (from.isEmpty() ? "" : " (son de la " + from + ")")
                                + ". Tu lista de apps sin genio se conserva. Si quieres poder volver atrás, exporta antes "
                                + "los tuyos (Acerca de → Guardar o compartir ajustes).",
                        "They replace all your settings" + (from.isEmpty() ? "" : " (they come from " + from + ")")
                                + ". Your list of apps without genie is kept. To be able to go back, export yours "
                                + "first (About → Save or share settings)."))
                .setNegativeButton(I18n.t("Cancelar", "Cancel"), null)
                .setPositiveButton(I18n.t("Usar", "Use"), new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int which) {
                        int n = Backup.restore(SettingsActivity.this, json, true);
                        if (n < 0) {
                            toast(I18n.t("Ese código no es válido", "That code is not valid"));
                            return;
                        }
                        settingsReplaced();
                        toast(I18n.t("Ajustes aplicados: ", "Settings applied: ") + n + ". "
                                + I18n.t("Reinicia el launcher y la interfaz para las funciones.", "Restart the launcher and System UI for the features."));
                    }
                })
                .show();
    }

    private static final int REQ_EXPORT = 1, REQ_IMPORT = 2;

    /**
     * Abre el selector de archivos del sistema para guardar (exportar) o elegir (importar) el
     * archivo. Así la app no necesita ningún permiso de almacenamiento.
     */
    private void pickFile(boolean export) {
        try {
            android.content.Intent i = new android.content.Intent(export
                    ? android.content.Intent.ACTION_CREATE_DOCUMENT : android.content.Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(android.content.Intent.CATEGORY_OPENABLE);
            // Al importar se acepta cualquier tipo: algunos gestores no reconocen .json.
            i.setType(export ? "application/json" : "*/*");
            if (export) i.putExtra(android.content.Intent.EXTRA_TITLE, Backup.FILE_NAME);
            startActivityForResult(i, export ? REQ_EXPORT : REQ_IMPORT);
        } catch (Throwable t) {
            toast(I18n.t("Este móvil no tiene selector de archivos", "This phone has no file picker"));
        }
    }

    @Override
    protected void onActivityResult(int request, int result, android.content.Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        android.net.Uri uri = data.getData();
        try {
            if (request == REQ_EXPORT) {
                try (java.io.OutputStream out = getContentResolver().openOutputStream(uri, "wt")) {
                    out.write(Backup.export(this).getBytes("UTF-8"));
                }
                toast(I18n.t("Ajustes exportados", "Settings exported"));
            } else if (request == REQ_IMPORT) {
                java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
                try (java.io.InputStream in = getContentResolver().openInputStream(uri)) {
                    byte[] buf = new byte[8192];
                    // Un archivo de ajustes ocupa unos pocos KB: no se lee más de 1 MB.
                    for (int n; bytes.size() < (1 << 20) && (n = in.read(buf)) > 0; ) bytes.write(buf, 0, n);
                }
                // Se convierte a texto de una vez: trozo a trozo, una letra de varios bytes (ñ, é…)
                // partida entre dos trozos salía estropeada.
                int n = Backup.restore(this, bytes.toString("UTF-8"));
                if (n < 0) {
                    toast(I18n.t("Ese archivo no es de ajustes de HyperSpring", "That file is not a HyperSpring settings file"));
                    return;
                }
                settingsReplaced();
                toast(I18n.t("Ajustes importados: ", "Settings imported: ") + n + ". "
                        + I18n.t("Reinicia el launcher y la interfaz para las funciones.", "Restart the launcher and System UI for the features."));
            }
        } catch (Throwable t) {
            toast(I18n.t("No se pudo: ", "Failed: ") + t.getMessage());
        }
    }

    private float stored(Object[] it) {
        float def = (Float) it[4];
        try {
            String v = getSharedPreferences(PrefsProvider.PREFS, 0).getString((String) it[0], null);
            if (v != null) return Math.max((Float) it[2], Math.min((Float) it[3], Float.parseFloat(v.trim())));
        } catch (Throwable ignored) {
        }
        return def;
    }

    /** Guarda en la app (PrefsProvider lo sirve) y avisa al launcher y a la interfaz: sin root. */
    private void write(String key, String value) {
        android.content.SharedPreferences.Editor e = getSharedPreferences(PrefsProvider.PREFS, 0).edit();
        if (value == null) e.remove(key);
        else e.putString(key, value);
        e.commit();
        send(Remote.TUNE);
    }

    private void send(String action) {
        Remote.send(this, action);
    }

    /**
     * Reinicia el launcher o la interfaz. Con root (lo normal con LSPosed) se cierra el proceso
     * desde fuera, que funciona siempre. Sin root, se le pide al proceso que se cierre él mismo; pero
     * HyperOS congela el launcher mientras no se ve, así que la orden puede no llegar hasta volver
     * al escritorio.
     */
    void restart(final boolean systemUi) {
        // Reiniciado (desde la pastilla o desde Inicio): ese proceso ya no espera nada.
        if (systemUi) pendSystemUi = false;
        else pendLauncher = false;
        if (pill != null) hidePill(true);
        final String name = systemUi ? I18n.t("la interfaz del sistema", "System UI") : I18n.t("el launcher", "the launcher");
        toast(I18n.t("Reiniciando ", "Restarting ") + name + "…");
        new Thread(new Runnable() {
            @Override
            public void run() {
                // Se cierra el proceso viejo (por su pid) y el sistema lo vuelve a abrir solo.
                String pkg = systemUi ? "com.android.systemui" : Launchers.current(SettingsActivity.this);
                String cmd = "p=$(pidof " + pkg + "); "
                        + (systemUi ? "" : "am force-stop " + pkg + " >/dev/null 2>&1; sleep 0.3; ")
                        + "[ -n \"$p\" ] && kill -9 $p 2>/dev/null; echo hecho";
                final String[] out = {""};
                boolean ok = false;
                try {
                    Process p = new ProcessBuilder("su", "-c", cmd).redirectErrorStream(true).start();
                    java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    for (String l; (l = r.readLine()) != null; ) sb.append(l).append(' ');
                    out[0] = sb.toString().trim();
                    ok = p.waitFor() == 0 && out[0].contains("hecho");
                } catch (Throwable t) {
                    out[0] = String.valueOf(t.getMessage());
                }
                final boolean rooted = ok;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (rooted) {
                            if (!getSharedPreferences("app", 0).getBoolean("root_ok", false)) {
                                getSharedPreferences("app", 0).edit().putBoolean("root_ok", true).apply();
                            }
                            return;
                        }
                        // Sin root: la interfaz del sistema (que nunca se congela) cierra el
                        // launcher; y el propio proceso se cierra si recibe la orden.
                        send(systemUi ? Remote.RESTART_SYSTEMUI : Remote.RESTART_LAUNCHER);
                        toast(I18n.t("Sin root (", "No root (") + (out[0].isEmpty() ? I18n.t("denegado", "denied") : out[0]) + "): "
                                + I18n.t("se ha pedido a " + name + " que se reinicie.", "asked " + name + " to restart."));
                    }
                });
            }
        }, "reinicio").start();
    }

    // ---------------------------------------------------------------- pastilla flotante
    /** La pastilla que flota sobre la barra de pestañas (deshacer un perfil, reiniciar), o null. */
    private TextView pill;
    /** Funciones cambiadas que esperan a que se reinicie su proceso. */
    private boolean pendLauncher, pendSystemUi;

    /** Tras cambiar una función que necesita reiniciar su proceso: la pastilla se queda hasta reiniciar. */
    private void pendingRestart(boolean systemUi) {
        if (ob != null) return;                 // en la presentación, el reinicio se ofrece al final
        if (systemUi) pendSystemUi = true;
        else pendLauncher = true;
        showRestartPill();
    }

    private void showRestartPill() {
        final boolean l = pendLauncher, s = pendSystemUi;
        if (!l && !s) return;
        String what = l && s ? I18n.t("el launcher y la interfaz", "the launcher and System UI")
                : l ? I18n.t("el launcher", "the launcher") : I18n.t("la interfaz", "System UI");
        // Mantener pulsado, como las filas de reiniciar: un toque sin querer no cierra nada.
        showPill(I18n.t("Mantén pulsado para reiniciar " + what, "Hold to restart " + what), true, new Runnable() {
            @Override
            public void run() {
                if (l) restart(false);
                if (s) restart(true);
            }
        }, 0);
    }

    /**
     * Enseña la pastilla con un mensaje y una acción: al tocarla o, si {@code hold}, al mantenerla
     * pulsada. Con {@code hideMs} > 0 se va sola; después vuelve la de reiniciar si hace falta.
     */
    private void showPill(String msg, final boolean hold, final Runnable action, long hideMs) {
        if (pill != null) root.removeView(pill);
        final TextView p = text(msg, 13.5f, text);
        p.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        p.setGravity(Gravity.CENTER);
        p.setPadding(px(18), px(11), px(18), px(11));
        GradientDrawable g = round(card, px(100));
        g.setStroke(Math.max(1, px(1)), accent);
        p.setBackground(g);
        p.setElevation(px(6));
        if (hold) {
            p.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    toast(I18n.t("Mantenla pulsada", "Hold it down"));
                }
            });
            p.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    hidePill(false);
                    action.run();
                    return true;
                }
            });
        } else {
            p.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    hidePill(true);
                    action.run();
                }
            });
        }
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        lp.bottomMargin = barSpace + px(10);
        lp.leftMargin = px(16);
        lp.rightMargin = px(16);
        root.addView(p, lp);
        pill = p;
        p.setAlpha(0f);
        p.setTranslationY(px(16));
        p.animate().alpha(1f).translationY(0f).setDuration(260).start();
        if (hideMs > 0) p.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (pill == p) hidePill(true);
            }
        }, hideMs);
    }

    /** Quita la pastilla; con {@code thenRestart}, vuelve a avisar del reinicio pendiente. */
    private void hidePill(boolean thenRestart) {
        final TextView p = pill;
        pill = null;
        if (p != null) p.animate().alpha(0f).translationY(px(16)).setDuration(200).withEndAction(new Runnable() {
            @Override
            public void run() {
                root.removeView(p);
            }
        }).start();
        if (thenRestart) showRestartPill();
    }

    /** Lo último que se pulsó: si abre una página, la transición sale de ahí (ver push). */
    private View lastTouch;
    private long lastTouchAt;
    /** Dónde se tocó {@link #lastTouch}, en sus propias coordenadas (las baldosas se llenan desde ahí). */
    private float lastTouchX, lastTouchY;

    /** Hundimiento al pulsar: la vista se encoge un poco y vuelve con un pequeño rebote. */
    void sink(View v) {
        v.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        lastTouch = v;
                        lastTouchAt = SystemClock.uptimeMillis();
                        lastTouchX = e.getX();
                        lastTouchY = e.getY();
                        v.animate().scaleX(0.96f).scaleY(0.96f).setStartDelay(0).setDuration(140).setInterpolator(EASE).start();
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        v.animate().scaleX(1f).scaleY(1f).setStartDelay(0).setDuration(380)
                                .setInterpolator(new android.view.animation.OvershootInterpolator(2.2f)).start();
                        break;
                    default:
                        break;
                }
                return false;                                   // el clic y la onda siguen igual
            }
        });
    }

    /** Lo que publican el launcher y la interfaz del sistema (ver {@link PrefsProvider#STATE}). */
    String state(String key) {
        try {
            return getSharedPreferences("state", 0).getString(key, null);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * ¿Hay que avisar de que falta la app de capturas en el ámbito? Con la captura encendida, si
     * HyperSpring nunca se ha cargado ahí, o si desde el reinicio hubo una captura (SystemUI lo
     * apunta en shot_missing) y la app de capturas no se cargó. Así no salta tras reiniciar
     * a quien sí lo tiene marcado.
     */
    boolean shotMissing() {
        if (stored(item(com.icehome.up.telon.Tune.ITEMS, "telon_squeeze_on")) < 0.5f) return false;
        if (shotLoaded()) return false;
        if (state("shot_seen") == null) return true;
        try {
            String m = state("shot_missing");
            return m != null && Long.parseLong(m) >= bootTime();
        } catch (Throwable t) {
            return false;
        }
    }

    /** ¿Se ha cargado HyperSpring en la app de capturas desde el último reinicio? */
    private boolean shotLoaded() {
        String st = state("shot_seen");
        if (st == null) return false;
        try {
            return Long.parseLong(st.split("\\|")[0]) >= bootTime();
        } catch (Throwable t) {
            return false;
        }
    }

    static long bootTime() {
        return System.currentTimeMillis() - SystemClock.elapsedRealtime();
    }

    /** Partes que cargaron una versión anterior de HyperSpring desde el último reinicio, o null. */
    private String staleParts() {
        String launcher = staleOne("launcher_seen") ? I18n.t("el launcher", "the launcher") : null;
        String ui = staleOne("systemui_seen") ? I18n.t("la interfaz del sistema", "System UI") : null;
        if (launcher != null && ui != null) return I18n.t("el launcher y la interfaz del sistema", "the launcher and System UI");
        return launcher != null ? launcher : ui;
    }

    private boolean staleOne(String key) {
        String st = state(key);
        if (st == null) return false;
        String[] p = st.split("\\|");
        try {
            return Long.parseLong(p[0]) >= bootTime() && p.length > 1 && !p[1].equals(Version.NAME);
        } catch (Throwable t) {
            return false;
        }
    }

    private String appVersion(String pkg) {
        try {
            return getPackageManager().getPackageInfo(pkg, 0).versionName;
        } catch (Throwable t) {
            return null;
        }
    }

    /** «Android 15 · OS3.0.4.0» (HyperOS) o «MIUI 14» si es MIUI. */
    private static String systemVersion() {
        String os = prop("ro.mi.os.version.incremental");
        if (os == null) os = prop("ro.mi.os.version.name");
        String out = "Android " + Build.VERSION.RELEASE;
        if (os != null) return out + " · HyperOS " + (os.startsWith("OS") ? os.substring(2) : os);
        String miui = prop("ro.miui.ui.version.name");
        return miui != null ? out + " · MIUI " + miui.replace("V", "") : out;
    }

    /** ¿HyperOS o MIUI? Sin ellos no hay launcher ni interfaz de Xiaomi que enganchar. */
    static boolean xiaomiOs() {
        return prop("ro.mi.os.version.name") != null || prop("ro.miui.ui.version.name") != null;
    }

    private static String prop(String key) {
        try {
            Object v = Class.forName("android.os.SystemProperties").getMethod("get", String.class).invoke(null, key);
            return v instanceof String && !((String) v).isEmpty() ? (String) v : null;
        } catch (Throwable t) {
            return null;
        }
    }

    boolean installed(String pkg) {
        try {
            getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    // ---------------------------------------------------------------- utilidades
    private static String format(float v, int decimals) {
        if (decimals <= 0) return String.valueOf(Math.round(v));
        return String.format(java.util.Locale.ROOT, "%." + decimals + "f", v);
    }

    private String version() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Throwable t) {
            return "";
        }
    }

    private TextView text(String s, float sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        return t;
    }

    private static GradientDrawable round(int color, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        return g;
    }

    private LinearLayout.LayoutParams weight(int rightMargin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
        lp.rightMargin = rightMargin;
        return lp;
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private int px(float v) {
        return Math.round(v * dp);
    }
}
