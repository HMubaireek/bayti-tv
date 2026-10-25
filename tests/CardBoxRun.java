package sa.bayti.home;

/** Prints CardBox's answers for the cases tests/card_box_test.py reads: one line "name w h" in pixels each. */
public class CardBoxRun {
    public static void main(String[] a) {
        // the same 1920 x 1080 pixels as a 960 dp screen at density 2, a 1280 dp one at 1.5 and a 1920 dp one at 1
        float[] density = {2f, 1.5f, 1f};
        for (float d : density) {
            int w = 1920, h = 1080;
            // the model of the home screen: the clock block 300 dp wide at the right margin; the dock's tile is a fifth of the
            // screen wide plus 12%, 16:9, in a band with 22 dp padding; the title 62 dp above it; 30 dp under the dock
            int clockLeft = Math.round(w - 48 * d - 300 * d);
            int tile = Math.round(w / 5f * 1.12f), band = Math.round(tile * 9 / 16f + 44 * d);
            int block = Math.round(62 * d) + band, titleTop = h - Math.round(30 * d) - block, dockTop = titleTop + Math.round(62 * d);
            for (int n = 1; n <= 3; n++) {
                int[] early = CardBox.box(n, w, h, d, 0, 0, 0, 0);
                int[] short_ = CardBox.box(n, w, h, d, clockLeft, titleTop, dockTop, Math.round(200 * d));
                int[] long_ = CardBox.box(n, w, h, d, clockLeft, titleTop, dockTop, Math.round(420 * d));
                System.out.println("early " + d + " " + n + " " + early[0] + " " + early[1]);
                System.out.println("short " + d + " " + n + " " + short_[0] + " " + short_[1] + " " + clockLeft + " " + titleTop + " " + dockTop);
                System.out.println("long " + d + " " + n + " " + long_[0] + " " + long_[1] + " " + clockLeft + " " + titleTop + " " + dockTop);
            }
        }
    }
}
