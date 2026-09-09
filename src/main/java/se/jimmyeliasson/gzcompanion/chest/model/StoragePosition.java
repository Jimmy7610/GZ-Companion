package se.jimmyeliasson.gzcompanion.chest.model;

/**
 * Immutable block-coordinate position. Contains no Minecraft API types.
 */
public record StoragePosition(int x, int y, int z) {

    public String toDisplayString() {
        return "X " + x + "  Y " + y + "  Z " + z;
    }

    public String toCoordinateText() {
        return x + " " + y + " " + z;
    }

    /**
     * Stable deterministic ordering (x, then y, then z) used to canonicalize double-chest anchors
     * so the same physical storage resolves to the same identity regardless of which half was clicked.
     */
    public int compareOrder(StoragePosition other) {
        if (other == null) return -1;
        if (this.x != other.x) return Integer.compare(this.x, other.x);
        if (this.y != other.y) return Integer.compare(this.y, other.y);
        return Integer.compare(this.z, other.z);
    }
}
