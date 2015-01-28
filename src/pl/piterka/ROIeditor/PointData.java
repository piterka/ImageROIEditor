package pl.piterka.ROIeditor;

//Container for point data
public class PointData {

    private final int x;
    private final int y;

    public PointData(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public PointData(double x, double y) {
        this.x = (int) Math.round(x);
        this.y = (int) Math.round(y);
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }
}
