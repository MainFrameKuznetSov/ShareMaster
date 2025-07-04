package p2p.utility;
import java.util.Random;

public class UploadUtil
{
    public static int generatePort()
    {
        final int START_PORT=49152;
        final int END_PORT=65535;
        Random rand=new Random();
        return rand.nextInt((END_PORT-START_PORT)+START_PORT);
    }
}
