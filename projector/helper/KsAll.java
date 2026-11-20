import java.lang.reflect.Method;

/**
 * Runs on the projector (as the shell user) through app_process.
 *   KsAll get                      -> prints "X1=115 Y1=31 ..." (current corner offsets, pixels inward)
 *   KsAll delta X1=5 Y3=-10 ...    -> adds the given steps (setXnKeystoneOffset/setYnKeystoneOffset are RELATIVE),
 *                                     then prints the new values
 *
 * Every reply ends with AUTO=<0|1> (read only: this helper never changes the firmware's Auto keystone setting).
 * While the firmware's own Auto keystone is on, the four-corner setters are refused and the getters read 0.
 */
public class KsAll {
    static final String[] KEYS = {"X1", "Y1", "X2", "Y2", "X3", "Y3", "X4", "Y4"};

    public static void main(String[] a) throws Exception {
        Class<?> c = Class.forName("com.hisilicon.android.tvapi.util.AdvancedPictureSetting");
        Object o = c.getDeclaredConstructor().newInstance();
        boolean auto = ((Integer) c.getMethod("getAutoKeystone").invoke(o)) != 0;
        if (a.length > 0 && a[0].equals("delta")) {
            for (int i = 1; i < a.length; i++) {
                String[] kv = a[i].split("=");
                int d = Integer.parseInt(kv[1]);
                if (d == 0) continue;
                Method m = c.getMethod("set" + kv[0] + "KeystoneOffset", int.class);
                m.invoke(o, d);
            }
            Thread.sleep(400);
        }
        StringBuilder sb = new StringBuilder();
        for (String k : KEYS) {
            Object v = c.getMethod("get" + k + "KeystonePosition").invoke(o);
            sb.append(k).append('=').append(v).append(' ');
        }
        sb.append("AUTO=").append(auto ? 1 : 0);
        System.out.println(sb.toString().trim());
    }
}
