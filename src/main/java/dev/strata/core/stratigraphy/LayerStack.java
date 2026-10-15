package dev.strata.core.stratigraphy;

/**
 * One column's layer cake: up to {@link #MAX_LAYERS} entries ordered from the
 * surface down. Entry {@code i} spans {@code (topY[i+1], topY[i]]}; the last
 * entry extends to the bottom of the world.
 */
public final class LayerStack {

    public static final int MAX_LAYERS = 12;

    public final GeoMaterial[] rock = new GeoMaterial[MAX_LAYERS];
    public final int[] topY = new int[MAX_LAYERS];
    public int count;

    public LayerStack() {
        for (int i = 0; i < MAX_LAYERS; i++) {
            rock[i] = GeoMaterial.STONE;
            topY[i] = 0;
        }
        count = 0;
    }

    /** Material at absolute height {@code y}. Never null. */
    public GeoMaterial rockAt(int y) {
        for (int i = 0; i + 1 < count; i++) {
            if (y <= topY[i] && y > topY[i + 1]) {
                return rock[i];
            }
        }
        return count > 0 ? rock[count - 1] : GeoMaterial.STONE;
    }

    public void clear() {
        count = 0;
    }

    /** Pushes a layer; callers must push from the surface downward. */
    public void push(GeoMaterial material, int top) {
        if (count >= MAX_LAYERS || material == null) {
            return;
        }
        rock[count] = material;
        topY[count] = top;
        count++;
    }
}
